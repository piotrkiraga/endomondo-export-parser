package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the workout migration preview: for every workout in the archive, exactly what
 * {@link MigrationPlanner} decided to do with it and — for anything not skipped — the
 * real name/sport/description a migration would send to Strava, resolved by
 * {@link WorkoutResolver} (the same resolution {@link MigrationExecutor} uses for the
 * real thing, so what this report shows and what actually gets sent can never diverge).
 * No Strava collaborator here: like the planner, producing this report cannot reach
 * the network.
 */
@Service
public class WorkoutReportGenerator {

    private static final String WORKOUTS_DIR = "Workouts";

    private final MigrationPlanner planner;
    private final WorkoutResolver resolver;

    public WorkoutReportGenerator(MigrationPlanner planner, WorkoutResolver resolver) {
        this.planner = planner;
        this.resolver = resolver;
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

        List<WorkoutReportEntry> entries = new ArrayList<>();
        for (ResolvedWorkout workout : resolved) {
            entries.add(WorkoutReportEntry.from(workout, activityIdsByBasename.get(workout.basename())));
        }

        return new WorkoutReport(List.copyOf(entries), plan.tracksWithoutMetadata());

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
                .append("<style>").append(ReportStyles.CSS).append("</style></head><body>\n")
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
                .append("<div class=\"meta\">")
                .append(escape(entry.startTime() == null ? "" : entry.startTime()))
                .append(" &mdash; ").append(escape(entry.stravaSportType() == null ? "" : entry.stravaSportType()));
        if (entry.distanceKm() != null) {
            section.append(" &mdash; ").append(formatNumber(entry.distanceKm())).append(" km");
        }
        if (entry.durationS() != null) {
            section.append(" &mdash; ").append(formatDuration(entry.durationS()));
        }
        if (entry.pictureCount() > 0) {
            section.append(" &mdash; ").append(entry.pictureCount()).append(" photo(s)");
        }
        section.append(" &mdash; ").append(activityLink(entry.activityId()))
                .append(" &mdash; <span class=\"basename\">archive: ").append(escape(entry.basename())).append("</span>")
                .append("</div>\n")
                .append("<div class=\"description\">").append(escape(entry.stravaDescription())).append("</div>\n")
                .append("</section>\n");

        return section.toString();

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

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
