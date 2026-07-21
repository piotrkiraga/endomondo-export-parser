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
        int pictureCount) {

    static WorkoutReportEntry from(WorkoutPlan planned, String stravaName, String stravaDescription) {
        return new WorkoutReportEntry(
                planned.basename(),
                planned.action(),
                planned.reason(),
                stravaName,
                planned.stravaSportType(),
                stravaDescription,
                planned.startTime(),
                planned.distanceKm(),
                planned.durationS(),
                planned.pictureCount());
    }

}
