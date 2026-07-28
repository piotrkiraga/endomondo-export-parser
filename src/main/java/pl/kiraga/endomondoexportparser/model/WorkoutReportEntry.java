package pl.kiraga.endomondoexportparser.model;

import pl.kiraga.endomondoexportparser.service.MigrationPlanner;
import pl.kiraga.endomondoexportparser.service.OldBikeGearResolver;
import pl.kiraga.endomondoexportparser.service.WorkoutReportGenerator;

/**
 * One workout's preview row: the planned action from {@link MigrationPlanner}, plus —
 * for anything not skipped — the exact name/sport/description a real migration would
 * send to Strava.
 */
public record WorkoutReportEntry(
        String basename,
        PlannedAction action,
        String reason,
        String stravaName,
        String stravaSportType,
        String stravaDescription,
        String startTime,
        Double distanceKm,
        Integer durationS,
        int pictureCount,
        Long activityId,
        String plannedGearId,
        String plannedGearDisplay,
        String confirmedGearDisplay,
        String sourceFiles) {

    /**
     * {@code activityId} is null for a workout not yet migrated (or the planner skipped
     * it). {@code confirmedGearDisplay} is a tri-state (see {@link WorkoutReportGenerator}):
     * null means not migrated, not connected, or the live Strava read failed — fall back
     * to {@code plannedGearDisplay}/{@code plannedGearId}; an empty string means the read
     * succeeded and Strava confirmed no gear at all — real information, not a fallback
     * case; any other value is "{name} ({id})". {@code plannedGearDisplay} is the same
     * "{name} ({id})" formatting for the offline-computed {@code plannedGearId}, resolved
     * once per report build (not per workout, since it's always the same configured old
     * bike) when connected — null when not connected or the one-time name lookup fails,
     * in which case {@code plannedGearId}'s bare id is all there is.
     */
    public static WorkoutReportEntry from(ResolvedWorkout workout, Long activityId, OldBikeGearResolver oldBikeGearResolver,
                                    String plannedGearDisplay, String confirmedGearDisplay) {
        return new WorkoutReportEntry(
                workout.basename(),
                workout.action(),
                workout.reason(),
                workout.name(),
                workout.stravaSportType(),
                workout.description(),
                workout.startTime(),
                workout.distanceKm(),
                workout.durationS(),
                workout.pictureCount(),
                activityId,
                oldBikeGearResolver.gearIdFor(workout.stravaSportType(), workout.startTime()),
                plannedGearDisplay,
                confirmedGearDisplay,
                workout.sourceFiles());
    }

}
