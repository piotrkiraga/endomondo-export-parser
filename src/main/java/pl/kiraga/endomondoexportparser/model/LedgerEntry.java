package pl.kiraga.endomondoexportparser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pl.kiraga.endomondoexportparser.service.MigrationLedger;

/**
 * One workout's row in {@link MigrationLedger}: written {@link LedgerStatus#PENDING}
 * before any Strava call, then finalized {@link LedgerStatus#DONE} (with the resulting
 * activity id, whether from a fresh upload or Strava's own duplicate-upload rejection —
 * the ledger records whatever id it's given) or {@link LedgerStatus#FAILED} (with a
 * reason). A PENDING entry surviving to the next run is the "crash leaves a visible
 * reconciliation point" the ledger exists for (design.md) — not a bug, but the point of
 * persisting before the call rather than after.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerEntry(
        String basename,
        PlannedAction action,
        LedgerStatus status,
        Long activityId,
        String reason,
        String updatedAt) {

    public static LedgerEntry pending(String basename, PlannedAction action, String updatedAt) {
        return new LedgerEntry(basename, action, LedgerStatus.PENDING, null, null, updatedAt);
    }

    public static LedgerEntry skipped(String basename, PlannedAction action, String updatedAt) {
        return new LedgerEntry(basename, action, LedgerStatus.SKIPPED, null, null, updatedAt);
    }

    public LedgerEntry asDone(long activityId, String updatedAt) {
        return new LedgerEntry(basename, action, LedgerStatus.DONE, activityId, null, updatedAt);
    }

    public LedgerEntry asFailed(String reason, String updatedAt) {
        return new LedgerEntry(basename, action, LedgerStatus.FAILED, null, reason, updatedAt);
    }

}
