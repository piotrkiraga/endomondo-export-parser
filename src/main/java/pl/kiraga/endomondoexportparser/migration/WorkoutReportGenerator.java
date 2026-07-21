package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.Location;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.service.InvalidWorkoutJsonException;

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

/**
 * Builds the workout migration preview: for every workout in the archive, exactly what
 * {@link MigrationPlanner} decided to do with it and — for anything not skipped — the
 * real name/sport/description a migration would send to Strava. Reuses the planner for
 * the action decision (the only place those rules live) and does its own scan+parse
 * pass, the same way {@link PhotoReportGenerator} already does independently of the
 * planner, purely to resolve the display-layer name/description via {@link PlaceLookup}.
 * No Strava collaborator here either: like the planner, producing this report cannot
 * reach the network.
 */
@Service
public class WorkoutReportGenerator {

    private static final String WORKOUTS_DIR = "Workouts";

    private final MigrationPlanner planner;
    private final ArchiveScanner scanner;
    private final EndomondoJsonParser parser;
    private final PlaceLookup placeLookup;

    public WorkoutReportGenerator(MigrationPlanner planner, ArchiveScanner scanner, EndomondoJsonParser parser,
                                   PlaceLookup placeLookup) {
        this.planner = planner;
        this.scanner = scanner;
        this.parser = parser;
        this.placeLookup = placeLookup;
    }

    /** {@code archiveRoot} is the archive root (containing "Workouts"), matching {@link PhotoReportGenerator}. */
    public WorkoutReport build(Path archiveRoot) {

        Path workoutsDirectory = archiveRoot.resolve(WORKOUTS_DIR);
        MigrationPlan plan = planner.plan(workoutsDirectory);
        ArchiveScan scan = scanner.scan(workoutsDirectory);

        Map<String, Path> jsonByBasename = new HashMap<>();
        for (WorkoutPair workout : scan.workouts()) {
            jsonByBasename.put(workout.basename(), workout.json());
        }

        List<WorkoutReportEntry> entries = new ArrayList<>();
        for (WorkoutPlan planned : plan.workouts()) {
            entries.add(entryFor(planned, jsonByBasename.get(planned.basename())));
        }

        return new WorkoutReport(List.copyOf(entries), plan.tracksWithoutMetadata());

    }

    /** Builds the report and writes it as a single self-contained HTML file. */
    public WorkoutReport generate(Path archiveRoot, Path outputHtmlFile) {
        WorkoutReport report = build(archiveRoot);
        render(report, outputHtmlFile);
        return report;
    }

    private WorkoutReportEntry entryFor(WorkoutPlan planned, Path json) {

        if (planned.action() == PlannedAction.SKIP || json == null) {
            return WorkoutReportEntry.from(planned, null, null);
        }

        EndomondoJson parsed = parseQuietly(json);
        if (parsed == null) {
            return WorkoutReportEntry.from(planned, null, null);
        }

        PlaceDescription place = placeFor(parsed);
        String name = WorkoutNaming.resolve(planned.name(), planned.startTime(), planned.stravaSportType(), place);
        String description = WorkoutDescription.build(dateOnly(planned.startTime()), place);

        return WorkoutReportEntry.from(planned, name, description);

    }

    private PlaceDescription placeFor(EndomondoJson parsed) {
        if (parsed.getPoints().isEmpty()) {
            return null;
        }
        Location firstPoint = parsed.getPoints().get(0).getLocation();
        if (firstPoint == null || firstPoint.getLatitude() == null || firstPoint.getLongitude() == null) {
            return null;
        }
        return placeLookup.lookup(firstPoint.getLatitude(), firstPoint.getLongitude()).orElse(null);
    }

    /** Endomondo's start_time looks like "2015-04-11 11:37:00.0"; the date is a fixed prefix. */
    private static String dateOnly(String startTime) {
        return (startTime == null || startTime.length() < 10) ? startTime : startTime.substring(0, 10);
    }

    private EndomondoJson parseQuietly(Path json) {
        try {
            return parser.parse(Files.readAllBytes(json));
        } catch (InvalidWorkoutJsonException | IOException e) {
            // MigrationPlanner already reports unparseable workouts as skipped; this
            // second pass simply contributes no name/description for one.
            return null;
        }
    }

    private void render(WorkoutReport report, Path outputHtmlFile) {

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>Endomondo workout report</title>")
                .append("<style>")
                .append("body{font-family:sans-serif;margin:2em;background:#fafafa}")
                .append("h1{font-size:1.3em}")
                .append(".summary{color:#444;margin-bottom:1.5em}")
                .append(".workout{margin-bottom:1em;padding:1em;background:#fff;border:1px solid #ddd}")
                .append(".workout.skip{background:#fff8f0;border-color:#e8d5b5}")
                .append(".workout h2{font-size:1em;margin:0 0 .3em}")
                .append(".meta{color:#666;font-size:.85em;margin-bottom:.4em}")
                .append(".description{font-size:.9em;color:#333;margin:.4em 0;white-space:pre-wrap}")
                .append(".action{display:inline-block;font-size:.75em;padding:.1em .5em;border-radius:.3em;color:#fff}")
                .append(".action.upload{background:#2a7}")
                .append(".action.manual{background:#38a}")
                .append(".action.skip{background:#b76}")
                .append(".basename{color:#999}")
                .append("</style></head><body>\n")
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
        section.append(" &mdash; <span class=\"basename\">archive: ").append(escape(entry.basename())).append("</span>")
                .append("</div>\n")
                .append("<div class=\"description\">").append(escape(entry.stravaDescription())).append("</div>\n")
                .append("</section>\n");

        return section.toString();

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
