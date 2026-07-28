package pl.kiraga.endomondoexportparser.model;

import pl.kiraga.endomondoexportparser.service.MigrationPlanner;

/**
 * The intended treatment of a single workout, as decided by {@link MigrationPlanner}.
 * A plan is a decision only — producing one performs no Strava call.
 */
public record WorkoutPlan(
        String basename,
        PlannedAction action,
        String name,
        String endomondoSport,
        String stravaSportType,
        String source,
        String startTime,
        Double distanceKm,
        Integer durationS,
        int pictureCount,
        String reason) {

    public static WorkoutPlan skip(String basename, String reason) {
        return new WorkoutPlan(basename, PlannedAction.SKIP, null, null, null, null, null, null, null, 0, reason);
    }

}
