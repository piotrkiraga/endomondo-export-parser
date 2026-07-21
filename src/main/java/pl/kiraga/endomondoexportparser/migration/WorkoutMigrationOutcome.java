package pl.kiraga.endomondoexportparser.migration;

/**
 * The result of one {@link MigrationExecutor#migrateOne} call — success carries the
 * activity id and an optional metadata-correction warning (see design.md: the ledger is
 * already {@code DONE} either way); failure carries the reason nothing was created.
 */
public record WorkoutMigrationOutcome(boolean success, Long activityId, String failureReason, String metadataWarning) {

    static WorkoutMigrationOutcome success(long activityId, String metadataWarning) {
        return new WorkoutMigrationOutcome(true, activityId, null, metadataWarning);
    }

    static WorkoutMigrationOutcome failure(String reason) {
        return new WorkoutMigrationOutcome(false, null, reason, null);
    }

}
