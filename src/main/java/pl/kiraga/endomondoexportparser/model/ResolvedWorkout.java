package pl.kiraga.endomondoexportparser.model;

import java.nio.file.Path;
import pl.kiraga.endomondoexportparser.service.MigrationExecutor;
import pl.kiraga.endomondoexportparser.service.WorkoutReportGenerator;
import pl.kiraga.endomondoexportparser.service.WorkoutResolver;

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
        Path jsonFile,
        String startTime,
        Double distanceKm,
        Integer durationS,
        int pictureCount) {

    /** The archive file(s) this entry maps to on disk, e.g. for cross-referencing outside the app. */
    public String sourceFiles() {
        String json = "Workouts/" + basename + ".json";
        return tcxFile == null ? json : json + ", Workouts/" + basename + ".tcx";
    }

    /** For a skipped workout, or one whose JSON could not be re-parsed on this pass. */
    public static ResolvedWorkout unresolved(WorkoutPlan planned) {
        return new ResolvedWorkout(
                planned.basename(), planned.action(), planned.reason(),
                null, planned.stravaSportType(), null, null, null,
                planned.startTime(), planned.distanceKm(), planned.durationS(), planned.pictureCount());
    }

    public static ResolvedWorkout resolved(WorkoutPlan planned, String name, String description, WorkoutPair pair) {
        return new ResolvedWorkout(
                planned.basename(), planned.action(), planned.reason(),
                name, planned.stravaSportType(), description, pair.tcx(), pair.json(),
                planned.startTime(), planned.distanceKm(), planned.durationS(), planned.pictureCount());
    }

}
