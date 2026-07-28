package pl.kiraga.endomondoexportparser.model;

/**
 * What the migration intends to do with one workout.
 */
public enum PlannedAction {

    /** Upload the paired TCX; Strava derives the route and its own statistics. */
    UPLOAD_TCX,

    /**
     * Create a manual activity from JSON metadata only. Used for INPUT_MANUAL workouts,
     * whose exported TCX trackpoints are synthetic and would draw invented routes.
     */
    CREATE_MANUAL,

    /** Do nothing, for a stated reason. */
    SKIP

}
