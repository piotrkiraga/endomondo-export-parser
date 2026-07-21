package pl.kiraga.endomondoexportparser.migration;

/** A workout's migration state within {@link MigrationLedger}. */
public enum LedgerStatus {

    /** Written before the Strava call that would create/upload this workout. */
    PENDING,

    /** The Strava call succeeded; {@link LedgerEntry#activityId()} is set. */
    DONE,

    /** The Strava call failed; {@link LedgerEntry#reason()} explains why. */
    FAILED

}
