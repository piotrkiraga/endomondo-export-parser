package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MigrationLedgerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC);

    private MigrationLedger ledgerAt(Path dir) {
        return new MigrationLedger(dir.resolve("ledger.json"), FIXED);
    }

    @Test
    void missingFileHasNoEntries(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);

        assertTrue(ledger.entries().isEmpty());
        assertFalse(ledger.isDone("2011-09-10 12_58_59.0"));
    }

    @Test
    void markPendingIsVisibleBeforeAnyResolution(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);

        ledger.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        LedgerEntry entry = ledger.find("2011-09-10 12_58_59.0").orElseThrow();
        assertEquals(LedgerStatus.PENDING, entry.status());
        assertEquals(PlannedAction.UPLOAD_TCX, entry.action());
        assertFalse(ledger.isDone("2011-09-10 12_58_59.0"));
    }

    @Test
    void markDoneRecordsTheActivityId(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);
        ledger.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        ledger.markDone("2011-09-10 12_58_59.0", 987654321L);

        LedgerEntry entry = ledger.find("2011-09-10 12_58_59.0").orElseThrow();
        assertEquals(LedgerStatus.DONE, entry.status());
        assertEquals(987654321L, entry.activityId());
        assertTrue(ledger.isDone("2011-09-10 12_58_59.0"));
    }

    @Test
    void markDoneRecordsWhicheverActivityIdItsGiven_evenFromADuplicateRejection(@TempDir Path dir) {
        // Strava's own duplicate-upload rejection still returns an existing activity_id
        // (design.md); the ledger has no separate "was this a duplicate" concept — it
        // just records whatever id the caller resolved, fresh upload or not.
        MigrationLedger ledger = ledgerAt(dir);
        ledger.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        ledger.markDone("2011-09-10 12_58_59.0", 111L);

        assertEquals(111L, ledger.find("2011-09-10 12_58_59.0").orElseThrow().activityId());
    }

    @Test
    void markFailedRecordsTheReasonAndLeavesItRetryable(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);
        ledger.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        ledger.markFailed("2011-09-10 12_58_59.0", "Strava API call failed: 500 server error");

        LedgerEntry entry = ledger.find("2011-09-10 12_58_59.0").orElseThrow();
        assertEquals(LedgerStatus.FAILED, entry.status());
        assertEquals("Strava API call failed: 500 server error", entry.reason());
        assertFalse(ledger.isDone("2011-09-10 12_58_59.0"), "a failed workout is eligible for retry, not permanently skipped");
    }

    @Test
    void markingResolutionWithoutAPriorPendingEntryThrows(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);

        assertThrows(IllegalStateException.class, () -> ledger.markDone("never-pending", 1L));
    }

    @Test
    void aCrashBetweenPendingAndDoneLeavesThePendingEntryVisibleToAFreshInstance(@TempDir Path dir) {
        Path file = dir.resolve("ledger.json");
        new MigrationLedger(file, FIXED).markPending("2014-09-16 09_05_21.0", PlannedAction.CREATE_MANUAL);
        // Simulates a process restart after a crash: nothing ever called markDone/markFailed.

        MigrationLedger resumed = new MigrationLedger(file, FIXED);

        LedgerEntry entry = resumed.find("2014-09-16 09_05_21.0").orElseThrow();
        assertEquals(LedgerStatus.PENDING, entry.status());
        assertFalse(resumed.isDone("2014-09-16 09_05_21.0"), "a crash-interrupted entry is retried, not treated as done");
    }

    @Test
    void aFreshInstanceSeesResolutionsWrittenByAnEarlierOne(@TempDir Path dir) {
        Path file = dir.resolve("ledger.json");
        MigrationLedger first = new MigrationLedger(file, FIXED);
        first.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);
        first.markDone("2011-09-10 12_58_59.0", 42L);

        MigrationLedger second = new MigrationLedger(file, FIXED);

        assertTrue(second.isDone("2011-09-10 12_58_59.0"));
    }

    @Test
    void activityIdsByBasenameOnlyIncludesDoneEntries(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);
        ledger.markPending("done-one", PlannedAction.UPLOAD_TCX);
        ledger.markDone("done-one", 1L);
        ledger.markPending("still-pending", PlannedAction.UPLOAD_TCX);
        ledger.markPending("failed-one", PlannedAction.CREATE_MANUAL);
        ledger.markFailed("failed-one", "boom");

        Map<String, Long> ids = ledger.activityIdsByBasename();

        assertEquals(Map.of("done-one", 1L), ids);
    }

    @Test
    void entriesAreSortedByBasenameForAHumanSkimmingTheFile(@TempDir Path dir) {
        MigrationLedger ledger = ledgerAt(dir);
        ledger.markPending("2014-09-16 09_05_21.0", PlannedAction.CREATE_MANUAL);
        ledger.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        List<LedgerEntry> entries = ledger.entries();

        assertEquals(List.of("2011-09-10 12_58_59.0", "2014-09-16 09_05_21.0"),
                entries.stream().map(LedgerEntry::basename).toList());
    }

    @Test
    void retryingAPendingEntryOverwritesItsTimestamp(@TempDir Path dir) {
        Path file = dir.resolve("ledger.json");
        Clock first = Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC);
        Clock later = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);
        new MigrationLedger(file, first).markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        MigrationLedger retry = new MigrationLedger(file, later);
        retry.markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);

        assertEquals("2026-07-21T10:00:00Z", retry.find("2011-09-10 12_58_59.0").orElseThrow().updatedAt());
    }

}
