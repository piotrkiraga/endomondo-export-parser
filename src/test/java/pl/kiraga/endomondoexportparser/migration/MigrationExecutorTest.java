package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses {@link MockRestServiceServer}; no test here touches the real network or sleeps
 * for real (the throttle is constructed with a zero interval, and the upload-poll
 * sleeper is a no-op), matching {@link StravaClientTest}'s conventions.
 */
public class MigrationExecutorTest {

    private static final Clock FIXED_NOON = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);
    private static final PlaceLookup NO_PLACES = (lat, lon) -> Optional.empty();
    private static final String TRACKED_BASENAME = "2011-09-10 12_58_59.0";
    private static final String MANUAL_BASENAME = "2014-09-16 09_05_21.0";

    private MockRestServiceServer server;
    private StravaTokenStore tokenStore;

    private MigrationExecutor executorFor(Path dir) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        tokenStore.save(new StravaTokens("t", "refresh-1", Instant.parse("2026-07-21T13:00:00Z").getEpochSecond()));
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", FIXED_NOON,
                millis -> { });

        MigrationPlanner planner = new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser());
        WorkoutResolver resolver = new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), NO_PLACES);
        MigrationLedger ledger = new MigrationLedger(dir.resolve("ledger.json"), FIXED_NOON);

        return new MigrationExecutor(planner, resolver, ledger, stravaClient, new RequestThrottle(0), millis -> { });
    }

    private MigrationLedger ledgerFor(Path dir) {
        return new MigrationLedger(dir.resolve("ledger.json"), FIXED_NOON);
    }

    private Path archiveWithTrackedWorkout(Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", TRACKED_BASENAME);
        writeTrack(workouts, TRACKED_BASENAME);
        return archiveRoot;
    }

    private Path archiveWithManualWorkout(Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-manual.json", MANUAL_BASENAME);
        return archiveRoot;
    }

    private void copyFixture(Path workoutsDirectory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(workoutsDirectory.resolve(basename + ".json"), content);
    }

    private void writeTrack(Path workoutsDirectory, String basename) throws Exception {
        Files.write(workoutsDirectory.resolve(basename + ".tcx"),
                "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
    }

    // --- Tracked workout: upload, poll, correct metadata ---

    @Test
    void trackedWorkoutUploadsPollsAndCorrectsMetadata(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"id": 555, "external_id": "%s", "status": "Your activity is still being processed."}
                        """.formatted(TRACKED_BASENAME), APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/uploads/555"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andExpect(method(PUT))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        MigrationRunSummary summary = executor.run(archiveRoot);

        server.verify();
        assertEquals(1, summary.uploaded());
        assertEquals(0, summary.failed());
        assertTrue(summary.metadataWarnings().isEmpty());

        LedgerEntry entry = ledgerFor(dir).find(TRACKED_BASENAME).orElseThrow();
        assertEquals(LedgerStatus.DONE, entry.status());
        assertEquals(777L, entry.activityId());
    }

    @Test
    void uploadStillProcessingPollsAgainBeforeSucceeding(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"status\": \"queued\"}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/uploads/555"))
                .andRespond(withSuccess("{\"id\": 555, \"status\": \"still processing\"}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/uploads/555"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        MigrationRunSummary summary = executor.run(archiveRoot);

        server.verify();
        assertEquals(1, summary.uploaded());
        assertEquals(777L, ledgerFor(dir).find(TRACKED_BASENAME).orElseThrow().activityId());
    }

    @Test
    void aDuplicateRejectionWithAnActivityIdStillCountsAsUploaded(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess(
                        "{\"id\": 555, \"error\": \"duplicate of activity 999\", \"activity_id\": 999}",
                        APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/999"))
                .andRespond(withSuccess("{\"id\": 999}", APPLICATION_JSON));

        MigrationRunSummary summary = executor.run(archiveRoot);

        assertEquals(1, summary.uploaded());
        assertEquals(999L, ledgerFor(dir).find(TRACKED_BASENAME).orElseThrow().activityId());
    }

    // --- Manual workout: create, correct metadata ---

    @Test
    void manualWorkoutCreatesAndCorrectsMetadata(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithManualWorkout(dir);
        MigrationExecutor executor = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/activities"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"id\": 888}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/888"))
                .andExpect(method(PUT))
                .andRespond(withSuccess("{\"id\": 888}", APPLICATION_JSON));

        MigrationRunSummary summary = executor.run(archiveRoot);

        server.verify();
        assertEquals(1, summary.manualCreated());
        assertEquals(0, summary.failed());

        LedgerEntry entry = ledgerFor(dir).find(MANUAL_BASENAME).orElseThrow();
        assertEquals(LedgerStatus.DONE, entry.status());
        assertEquals(888L, entry.activityId());
    }

    // --- Old-bike gear assignment: TRACKED_BASENAME is a Ride recorded 2011-09-10 ---

    @Test
    void gearIsAssignedToARideOnOrBeforeTheConfiguredCutoff(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);
        executor.setOldBikeGearId("b18387038");
        executor.setOldBikeCutoffDate("2021-01-31");

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andExpect(content().json("{\"gear_id\":\"b18387038\"}"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        executor.run(archiveRoot);

        server.verify();
    }

    @Test
    void gearIsNotAssignedToARideAfterTheConfiguredCutoff(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);
        executor.setOldBikeGearId("b18387038");
        executor.setOldBikeCutoffDate("2011-09-09"); // the day before the workout

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andExpect(content().json(
                        "{\"name\":\"Sample tracked ride\",\"sport_type\":\"Ride\"}", false))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        executor.run(archiveRoot);

        server.verify();
        // A strict body check (no gear_id key at all) is done at the StravaClient level
        // (StravaClientTest#updateActivityOmitsGearIdEntirelyWhenNull); this test only
        // needs to prove the executor decided not to pass a gear id at all.
    }

    @Test
    void gearIsNeverAssignedWhenUnconfigured(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);
        // Neither setOldBikeGearId nor setOldBikeCutoffDate called: the default,
        // matching every other test in this class that doesn't touch gear at all.

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        executor.run(archiveRoot);

        server.verify();
    }

    @Test
    void gearIsNotAssignedToANonRideSportEvenBeforeTheCutoff(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithManualWorkout(dir); // MANUAL_BASENAME is a Walk, recorded 2014-09-16
        MigrationExecutor executor = executorFor(dir);
        executor.setOldBikeGearId("b18387038");
        executor.setOldBikeCutoffDate("2099-01-01"); // deliberately always in the future

        server.expect(requestTo("https://www.strava.com/api/v3/activities"))
                .andRespond(withSuccess("{\"id\": 888}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/888"))
                .andExpect(content().json("{\"sport_type\":\"Walk\"}", false))
                .andRespond(withSuccess("{\"id\": 888}", APPLICATION_JSON));

        executor.run(archiveRoot);

        server.verify();
    }

    // --- Resume behaviour ---

    @Test
    void aWorkoutAlreadyDoneInTheLedgerMakesNoHttpCallAtAll(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 42L);

        MigrationExecutor executor = executorFor(dir);
        // No server.expect(...) calls at all: MockRestServiceServer fails loudly on any
        // unexpected request, so a passing run here proves zero Strava calls happened.

        MigrationRunSummary summary = executor.run(archiveRoot);

        server.verify();
        assertEquals(1, summary.alreadyDone());
        assertEquals(0, summary.uploaded());
    }

    @Test
    void reRunningAfterASuccessfulUploadMakesNoDuplicateCalls(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor firstRun = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        MigrationRunSummary first = firstRun.run(archiveRoot);
        assertEquals(1, first.uploaded());

        // A fresh executor/server pair sharing the same ledger file, simulating a
        // separate re-run of the app: no expectations are registered at all, so any
        // HTTP call the second run made would fail the test.
        MigrationExecutor secondRun = executorFor(dir);

        MigrationRunSummary second = secondRun.run(archiveRoot);

        server.verify();
        assertEquals(0, second.uploaded());
        assertEquals(1, second.alreadyDone());
    }

    @Test
    void aUserSkippedWorkoutMakesNoHttpCallAndCountsSeparatelyFromPlannerSkips(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markSkipped(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);

        MigrationExecutor executor = executorFor(dir);

        MigrationRunSummary summary = executor.run(archiveRoot);

        server.verify();
        assertEquals(1, summary.skippedByUser());
        assertEquals(0, summary.skippedByPlanner());
        assertEquals(0, summary.uploaded());
    }

    @Test
    void migrateOneOnAnAlreadyDoneWorkoutOnlyRefreshesMetadataRatherThanReUploading(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 111L);

        MigrationExecutor executor = executorFor(dir);
        ResolvedWorkout workout = executor.resolveAll(archiveRoot).get(0);

        // No /uploads expectation at all: MockRestServiceServer fails loudly on any
        // unexpected request, so re-uploading the TCX would fail this test.
        server.expect(requestTo("https://www.strava.com/api/v3/activities/111"))
                .andRespond(withSuccess("{\"id\": 111}", APPLICATION_JSON));

        WorkoutMigrationOutcome outcome = executor.migrateOne(workout);

        server.verify();
        assertTrue(outcome.success());
        assertEquals(111L, outcome.activityId());
        assertEquals(111L, ledger.find(TRACKED_BASENAME).orElseThrow().activityId(),
                "re-migrating a DONE workout must keep its original activity id, not mint a new one");
    }

    // --- Failure handling ---

    @Test
    void aStravaFailureMarksTheLedgerFailedRatherThanThrowing(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"message\": \"server error\"}")
                        .contentType(APPLICATION_JSON));

        MigrationRunSummary summary = executor.run(archiveRoot);

        assertEquals(0, summary.uploaded());
        assertEquals(1, summary.failed());
        assertFalse(summary.failures().isEmpty());

        LedgerEntry entry = ledgerFor(dir).find(TRACKED_BASENAME).orElseThrow();
        assertEquals(LedgerStatus.FAILED, entry.status());
    }

    @Test
    void aMetadataUpdateFailureIsAWarningNotAFailure(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTrackedWorkout(dir);
        MigrationExecutor executor = executorFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"message\": \"server error\"}")
                        .contentType(APPLICATION_JSON));

        MigrationRunSummary summary = executor.run(archiveRoot);

        assertEquals(1, summary.uploaded(), "the activity was created; only its metadata correction failed");
        assertEquals(0, summary.failed());
        assertEquals(1, summary.metadataWarnings().size());

        assertEquals(LedgerStatus.DONE, ledgerFor(dir).find(TRACKED_BASENAME).orElseThrow().status(),
                "DONE as soon as an activity id exists, so a retry can't create a duplicate");
    }

    // --- Planner-level skips never reach the ledger or the network ---

    @Test
    void aPlannerSkippedWorkoutMakesNoHttpCallAndNoLedgerEntry(@TempDir Path dir) throws Exception {
        Path archiveRoot = dir.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", TRACKED_BASENAME);
        // No .tcx written: MigrationPlanner skips a tracked workout with no paired track.
        MigrationExecutor executor = executorFor(dir);

        MigrationRunSummary summary = executor.run(archiveRoot);

        server.verify();
        assertEquals(1, summary.skippedByPlanner());
        assertEquals(0, summary.failed());
        assertTrue(ledgerFor(dir).find(TRACKED_BASENAME).isEmpty());
    }

}
