package pl.kiraga.endomondoexportparser.migration;

import java.nio.file.Path;

/**
 * A workout with its Strava name/description resolved by {@link WorkoutResolver} — the
 * single shared basis for both {@link WorkoutReportGenerator} (a dry-run preview) and
 * {@link MigrationExecutor} (the real migration), so what the report shows and what
 * actually gets sent to Strava can never diverge.
 */
public record ResolvedWorkout(
        String basename,
        PlannedAction action,
        String reason,
        String name,
        String stravaSportType,
        String description,
        Path tcxFile,
        String startTime,
        Double distanceKm,
        Integer durationS,
        int pictureCount) {

    /** For a skipped workout, or one whose JSON could not be re-parsed on this pass. */
    static ResolvedWorkout unresolved(WorkoutPlan planned) {
        return new ResolvedWorkout(
                planned.basename(), planned.action(), planned.reason(),
                null, planned.stravaSportType(), null, null,
                planned.startTime(), planned.distanceKm(), planned.durationS(), planned.pictureCount());
    }

    static ResolvedWorkout resolved(WorkoutPlan planned, String name, String description, Path tcxFile) {
        return new ResolvedWorkout(
                planned.basename(), planned.action(), planned.reason(),
                name, planned.stravaSportType(), description, tcxFile,
                planned.startTime(), planned.distanceKm(), planned.durationS(), planned.pictureCount());
    }

}
