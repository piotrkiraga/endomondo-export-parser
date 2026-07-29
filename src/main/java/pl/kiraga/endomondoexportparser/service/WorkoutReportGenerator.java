package pl.kiraga.endomondoexportparser.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.model.MigrationPlan;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.model.ResolvedWorkout;
import pl.kiraga.endomondoexportparser.model.WorkoutReport;
import pl.kiraga.endomondoexportparser.model.WorkoutReportEntry;
import pl.kiraga.endomondoexportparser.util.ReportStylesUtil;
import pl.kiraga.endomondoexportparser.util.RequestThrottleUtil;
import pl.kiraga.endomondoexportparser.util.WorkoutDescriptionUtil;

/**
 * Builds the workout migration preview: for every workout in the archive, exactly what
 * {@link MigrationPlanner} decided to do with it and — for anything not skipped — the
 * real name/sport/description a migration would send to Strava, resolved by
 * {@link WorkoutResolver} (the same resolution {@link MigrationExecutor} uses for the
 * real thing, so what this report shows and what actually gets sent can never diverge).
 * Planning itself never reaches the network — but for a workout already migrated and
 * while connected to Strava, {@link #build(Path, Map)} does read its confirmed gear back
 * (see {@link ConfirmedGearResolver}), throttled like a real migration run, since the
 * write-side gear correction is known unreliable and a bare "planned" id would otherwise
 * be presented as fact for workouts where the real answer is one Strava call away.
 */
@Service
public class WorkoutReportGenerator {

    private static final String WORKOUTS_DIR = "Workouts";

    /**
     * Runs before {@code <body>} so the right mode is set before first paint (a later
     * script would flash the wrong colors first). Reads the app's own explicit choice —
     * best-effort: {@code localStorage} can throw under {@code file://} in some browsers,
     * hence the {@code try/catch}. Duplicated in {@link PhotoReportGenerator}, not
     * shared — see reports-follow-explicit-theme's design.md.
     */
    private static final String THEME_SCRIPT = "<script>try{var t=localStorage.getItem('theme');"
            + "if(t==='light'||t==='dark'){document.documentElement.setAttribute('data-theme',t);}"
            + "}catch(e){}</script>";

    private final MigrationPlanner planner;
    private final WorkoutResolver resolver;
    private final OldBikeGearResolver oldBikeGearResolver;
    private final ConfirmedGearResolver confirmedGearResolver;
    private final StravaTokenStore stravaTokenStore;
    private final RequestThrottleUtil throttle;

    @Autowired
    public WorkoutReportGenerator(MigrationPlanner planner, WorkoutResolver resolver,
                                   OldBikeGearResolver oldBikeGearResolver, ConfirmedGearResolver confirmedGearResolver,
                                   StravaTokenStore stravaTokenStore) {
        this(planner, resolver, oldBikeGearResolver, confirmedGearResolver, stravaTokenStore, new RequestThrottleUtil(1000));
    }

    WorkoutReportGenerator(MigrationPlanner planner, WorkoutResolver resolver, OldBikeGearResolver oldBikeGearResolver,
                            ConfirmedGearResolver confirmedGearResolver, StravaTokenStore stravaTokenStore,
                            RequestThrottleUtil throttle) {
        this.planner = planner;
        this.resolver = resolver;
        this.oldBikeGearResolver = oldBikeGearResolver;
        this.confirmedGearResolver = confirmedGearResolver;
        this.stravaTokenStore = stravaTokenStore;
        this.throttle = throttle;
    }

    /** Dry-run: no workout has a Strava activity id yet. */
    public WorkoutReport build(Path archiveRoot) {
        return build(archiveRoot, Map.of());
    }

    /**
     * {@code archiveRoot} is the archive root (containing "Workouts"), matching
     * {@link PhotoReportGenerator}. {@code activityIdsByBasename} supplies Strava
     * activity ids for workouts already migrated (e.g. {@link MigrationLedger#activityIdsByBasename()}),
     * same convention as {@link PhotoReportGenerator#build}; a workout absent from it
     * renders as "pending migration".
     */
    public WorkoutReport build(Path archiveRoot, Map<String, Long> activityIdsByBasename) {

        Path workoutsDirectory = archiveRoot.resolve(WORKOUTS_DIR);
        MigrationPlan plan = planner.plan(workoutsDirectory);
        List<ResolvedWorkout> resolved = resolver.resolve(workoutsDirectory, plan);

        boolean connected = stravaTokenStore.load().isPresent();
        Map<String, String> gearNameCache = new HashMap<>();

        String configuredGearId = oldBikeGearResolver.configuredGearId();
        String plannedGearDisplay = connected && configuredGearId != null
                ? gearNameFor(configuredGearId, gearNameCache) : null;

        List<WorkoutReportEntry> entries = new ArrayList<>();
        for (ResolvedWorkout workout : resolved) {
            Long activityId = activityIdsByBasename.get(workout.basename());
            String confirmedGearDisplay = connected && activityId != null
                    ? confirmedGearDisplayFor(activityId, gearNameCache) : null;
            entries.add(WorkoutReportEntry.from(workout, activityId, oldBikeGearResolver, plannedGearDisplay,
                    confirmedGearDisplay));
        }

        return new WorkoutReport(List.copyOf(entries), plan.tracksWithoutMetadata());

    }

    /**
     * Null only on an actual lookup failure (falls back to the planned id instead) — an
     * empty string means Strava was successfully read and confirmed no gear at all,
     * which is real information, not a reason to fall back. {@code gearNameCache} avoids
     * repeating the name lookup for the same gear id across many entries in one build.
     */
    private String confirmedGearDisplayFor(long activityId, Map<String, String> gearNameCache) {
        throttle.await();
        Optional<String> confirmedGearId = confirmedGearResolver.gearIdFor(activityId);
        if (confirmedGearId.isEmpty()) {
            return null;
        }
        String gearId = confirmedGearId.get();
        return gearId.isBlank() ? "" : gearNameFor(gearId, gearNameCache);
    }

    /** Cached per gear id within one build, since the configured old bike is reused across many entries. */
    private String gearNameFor(String gearId, Map<String, String> gearNameCache) {
        return gearNameCache.computeIfAbsent(gearId, id -> {
            throttle.await();
            return confirmedGearResolver.display(id);
        });
    }

    /** Builds the report and writes it as a single self-contained HTML file; dry-run, no activity ids. */
    public WorkoutReport generate(Path archiveRoot, Path outputHtmlFile) {
        return generate(archiveRoot, outputHtmlFile, Map.of());
    }

    /** As {@link #generate(Path, Path)}, with Strava activity ids/links for already-migrated workouts. */
    public WorkoutReport generate(Path archiveRoot, Path outputHtmlFile, Map<String, Long> activityIdsByBasename) {
        WorkoutReport report = build(archiveRoot, activityIdsByBasename);
        render(report, outputHtmlFile);
        return report;
    }

    private void render(WorkoutReport report, Path outputHtmlFile) {

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>Endomondo workout report</title>")
                .append("<style>").append(ReportStylesUtil.CSS).append("</style>")
                .append(THEME_SCRIPT)
                .append("</head><body>\n")
                .append("<h1>Endomondo workout report</h1>\n")
                .append("<p class=\"summary\">")
                .append(report.count(PlannedAction.UPLOAD_TCX)).append(" track upload(s), ")
                .append(report.count(PlannedAction.CREATE_MANUAL)).append(" manual activit(y/ies), ")
                .append(report.count(PlannedAction.SKIP)).append(" skipped")
                .append(report.tracksWithoutMetadata().isEmpty() ? "" :
                        ", " + report.tracksWithoutMetadata().size() + " track(s) with no metadata")
                .append(".</p>\n");

        for (WorkoutReportEntry entry : report.entries()) {
            html.append(entry.action() == PlannedAction.SKIP ? renderSkip(entry) : renderPlanned(entry));
        }

        html.append("</body></html>\n");

        try {
            Files.createDirectories(outputHtmlFile.toAbsolutePath().normalize().getParent());
            Files.writeString(outputHtmlFile, html.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write report " + outputHtmlFile, e);
        }

    }

    private String renderSkip(WorkoutReportEntry entry) {
        return "<section class=\"workout skip\">\n"
                + "<span class=\"action skip\">Skip</span> "
                + "<span class=\"basename\">" + escape(entry.basename()) + "</span>\n"
                + "<div class=\"description\">" + escape(entry.reason()) + "</div>\n"
                + "</section>\n";
    }

    private String renderPlanned(WorkoutReportEntry entry) {

        String actionLabel = entry.action() == PlannedAction.UPLOAD_TCX ? "Upload TCX" : "Create manual";
        String actionClass = entry.action() == PlannedAction.UPLOAD_TCX ? "upload" : "manual";

        StringBuilder section = new StringBuilder("<section class=\"workout\">\n")
                .append("<h2><span class=\"action ").append(actionClass).append("\">").append(actionLabel)
                .append("</span> ").append(escape(entry.stravaName())).append("</h2>\n")
                .append("<div class=\"detail-list\">\n")
                .append(detailRow("Start time", entry.startTime() == null ? "" : escape(entry.startTime())))
                .append(detailRow("Sport", entry.stravaSportType() == null ? "" : escape(entry.stravaSportType())));
        if (entry.distanceKm() != null) {
            section.append(detailRow("Distance", formatNumber(entry.distanceKm()) + " km"));
        }
        if (entry.durationS() != null) {
            section.append(detailRow("Duration", formatDuration(entry.durationS())));
        }
        section.append("</div>\n")
                .append("<div class=\"meta\">").append(activityLink(entry.activityId()))
                .append(" &mdash; <span class=\"basename\">Source: ").append(escape(entry.sourceFiles())).append("</span>");
        if (entry.pictureCount() > 0) {
            section.append(" &mdash; ").append(entry.pictureCount()).append(" photo(s)");
        }
        section.append(gearMeta(entry))
                .append("</div>\n")
                .append("<div class=\"description\">").append(escape(tighten(entry.stravaDescription()))).append("</div>\n")
                .append("</section>\n");

        return section.toString();

    }

    private static String detailRow(String label, String value) {
        return "<div class=\"detail-row\"><span class=\"detail-label\">" + label
                + "</span><span class=\"detail-value\">" + value + "</span></div>\n";
    }

    /**
     * Prefers the confirmed value (a real Strava read) over the merely planned one,
     * since we know the write-side correction doesn't reliably land — see
     * {@link WorkoutReportEntry#confirmedGearDisplay}'s tri-state.
     */
    private String gearMeta(WorkoutReportEntry entry) {
        if (entry.confirmedGearDisplay() != null) {
            return entry.confirmedGearDisplay().isEmpty()
                    ? " &mdash; gear: none (Strava's own default)"
                    : " &mdash; gear: " + escape(entry.confirmedGearDisplay());
        }
        if (entry.plannedGearId() != null) {
            String display = entry.plannedGearDisplay() != null ? entry.plannedGearDisplay() : entry.plannedGearId();
            return " &mdash; planned gear: " + escape(display);
        }
        return "";
    }

    /** Mirrors {@link PhotoReportGenerator}'s own activity link text/format exactly. */
    private String activityLink(Long activityId) {
        return activityId == null ? "pending migration"
                : "<a href=\"https://www.strava.com/activities/" + activityId + "\">view on Strava</a>";
    }

    private static String formatDuration(Integer seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String formatNumber(Double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Display-only: collapses the blank-line paragraph break {@link WorkoutDescriptionUtil}
     * puts between the place sentence and the credit stamp into a single line break, for
     * a tighter look in this report's compact per-workout cards. The value actually sent
     * to Strava (and shown on the migration review page) is untouched.
     */
    private String tighten(String description) {
        return description == null ? null : description.replace("\n\n", "\n");
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
