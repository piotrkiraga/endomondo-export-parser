package pl.kiraga.endomondoexportparser.migration;

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
        Long activityId) {

    /** {@code activityId} is null for a workout not yet migrated (or the planner skipped it). */
    static WorkoutReportEntry from(ResolvedWorkout workout, Long activityId) {
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
                activityId);
    }

}
