package pl.kiraga.endomondoexportparser.migration;

import java.nio.file.Path;
import java.util.List;

/**
 * The complete workout report: one entry per workout in the archive. Like
 * {@link MigrationPlan}, holding only decisions and previews makes it safe to produce
 * and display without credentials.
 */
public record WorkoutReport(List<WorkoutReportEntry> entries, List<Path> tracksWithoutMetadata) {

    public long count(PlannedAction action) {
        return entries.stream().filter(entry -> entry.action() == action).count();
    }

}
