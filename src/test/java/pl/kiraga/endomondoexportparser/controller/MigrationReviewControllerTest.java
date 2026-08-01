package pl.kiraga.endomondoexportparser.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.service.MigrationTestSupport;
import pl.kiraga.endomondoexportparser.model.LedgerEntry;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.model.ResolvedWorkout;
import pl.kiraga.endomondoexportparser.service.MigrationExecutor;
import pl.kiraga.endomondoexportparser.service.MigrationLedger;
import pl.kiraga.endomondoexportparser.service.ResolvedPlanCache;
import pl.kiraga.endomondoexportparser.service.StravaClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;


/**
 * Calls controller methods directly rather than through {@code MockMvc}/Thymeleaf, so
 * this never needs a Spring context — no risk of a stray request reaching the real
 * {@code StravaClient}. {@link StravaClient} here is wired to a {@link MockRestServiceServer}
 * via {@link MigrationTestSupport}, the same safety bar
 * {@link pl.kiraga.endomondoexportparser.service.MigrationExecutorTest} uses, so even
 * a future test accidentally exercising {@code migrate} cannot reach the real network.
 * Actual Thymeleaf template rendering is verified separately, live.
 */
public class MigrationReviewControllerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);
    private static final String TRACKED_BASENAME = "2011-09-10 12_58_59.0";
    private static final String MANUAL_BASENAME = "2014-09-16 09_05_21.0";

    private MockRestServiceServer server;
    private MigrationTestSupport.Rig rig;

    private MigrationReviewController controllerFor(Path dir) {
        rig = MigrationTestSupport.build(dir, FIXED);
        server = rig.server();
        return controllerWith(dir, rig.executor());
    }

    private MigrationReviewController controllerWith(Path dir, MigrationExecutor executor) {
        MigrationReviewController controller = new MigrationReviewController(
                executor, new ResolvedPlanCache(executor), rig.ledger(), rig.photoResolver(), rig.tokenStore(),
                rig.confirmedGearResolver(), rig.oldBikeGearResolver());
        controller.setArchiveRootProperty(dir.resolve("archive").toString());
        controller.setPhotoReportOutputDirectory(dir.resolve("photo-report").toString());
        return controller;
    }

    private MigrationLedger ledgerFor(Path dir) {
        return MigrationTestSupport.build(dir, FIXED).ledger();
    }

    private Path archiveWithTwoActionableWorkouts(Path dir) throws Exception {
        Path archiveRoot = dir.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", TRACKED_BASENAME);
        writeTrack(workouts, TRACKED_BASENAME);
        copyFixture(workouts, "workout-manual.json", MANUAL_BASENAME);
        return archiveRoot;
    }

    private void copyFixture(Path workoutsDirectory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(workoutsDirectory.resolve(basename + ".json"), content);
    }

    private void writeTrack(Path workoutsDirectory, String basename) throws Exception {
        Files.write(workoutsDirectory.resolve(basename + ".tcx"), "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
    }

    // --- Starting position ---

    @Test
    void startJumpsToTheFirstWorkoutWhenNothingIsDecidedYet(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.start(new ModelAndView());

        assertEquals("redirect:/migration/review/0", mav.getViewName());
    }

    @Test
    void startSkipsPastWorkoutsAlreadyDoneOrSkipped(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 42L);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.start(new ModelAndView());

        assertEquals("redirect:/migration/review/1", mav.getViewName());
    }

    @Test
    void startSkipsPastAFailedWorkoutTooRatherThanBouncingBackToItForever(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markFailed(TRACKED_BASENAME, "duplicate");
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.start(new ModelAndView());

        assertEquals("redirect:/migration/review/1", mav.getViewName());
    }

    @Test
    void startGoesToSummaryWhenEverythingIsAlreadyDecided(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 42L);
        ledger.markSkipped(MANUAL_BASENAME, PlannedAction.CREATE_MANUAL);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.start(new ModelAndView());

        assertEquals("redirect:/migration/review/summary", mav.getViewName());
    }

    // --- Viewing a single workout ---

    @Test
    void viewRendersTheWorkoutAtTheGivenIndexWithNoDecisionYet(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.view(new ModelAndView(), 0);

        assertEquals("migration/review", mav.getViewName());
        assertEquals(TRACKED_BASENAME, ((ResolvedWorkout) mav.getModel().get("workout")).basename());
        assertEquals(1, mav.getModel().get("position"));
        assertEquals(2, mav.getModel().get("total"));
        assertEquals(Boolean.FALSE, mav.getModel().get("hasDecision"));
        assertEquals(Boolean.FALSE, mav.getModel().get("isDone"));
    }

    @Test
    void viewOfAnOutOfRangeIndexRedirectsToSummary(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.view(new ModelAndView(), 99);

        assertEquals("redirect:/migration/review/summary", mav.getViewName());
    }

    @Test
    void viewShowsTheMigratedFlagAndAllowsAMigrateButtonToRemigrate(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 777L);
        MigrationReviewController controller = controllerFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777, \"gear_id\": \"b18387038\"}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

        ModelAndView mav = controller.view(new ModelAndView(), 0);

        assertEquals(Boolean.TRUE, mav.getModel().get("isDone"));
        assertEquals(Boolean.TRUE, mav.getModel().get("hasDecision"));
        LedgerEntry entry = (LedgerEntry) mav.getModel().get("ledgerEntry");
        assertEquals(777L, entry.activityId());
        assertEquals("Trek Checkpoint (b18387038)", mav.getModel().get("currentGearDisplay"));
        assertEquals("2026-07-21 14:00:00", mav.getModel().get("formattedMigratedOn"), "displayed in Europe/Warsaw, not raw UTC");
    }

    @Test
    void viewShowsPlannedGearForANotYetMigratedRide(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);
        MigrationTestSupport.configureOldBike(rig.oldBikeGearResolver(), "b18387038", "2021-01-31");

        // No /activities call: TRACKED_BASENAME isn't migrated, so there's nothing to
        // confirm — only the planned gear's own name is looked up.
        server.expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

        ModelAndView mav = controller.view(new ModelAndView(), 0);

        assertEquals(Boolean.FALSE, mav.getModel().get("isDone"));
        assertEquals("b18387038", mav.getModel().get("plannedGearId"));
        assertEquals("Trek Checkpoint (b18387038)", mav.getModel().get("plannedGearDisplay"));
        server.verify();
    }

    // --- Migrate: always allowed, even when already decided ---

    @Test
    void migrateCallsTheExecutorAndRedirectsBackToTheSameIndex(@TempDir Path dir) throws Exception {
        Path archiveRoot = archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        ModelAndView mav = controller.migrate(new ModelAndView(), 0);

        server.verify();
        assertEquals("redirect:/migration/review/0", mav.getViewName());
        assertTrue(ledgerFor(dir).isDone(TRACKED_BASENAME));
    }

    @Test
    void migrateOnAnAlreadyDoneWorkoutOnlyRefreshesMetadata(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 111L);
        MigrationReviewController controller = controllerFor(dir);

        // No /uploads expectation: re-clicking Migrate on a DONE workout must not
        // re-send the TCX (Strava would reject it as a duplicate anyway).
        server.expect(requestTo("https://www.strava.com/api/v3/activities/111"))
                .andRespond(withSuccess("{\"id\": 111}", APPLICATION_JSON));

        controller.migrate(new ModelAndView(), 0);

        server.verify();
        assertEquals(111L, ledgerFor(dir).find(TRACKED_BASENAME).orElseThrow().activityId());
    }

    // --- Skip: no Strava call, advances immediately, persists ---

    @Test
    void skipMarksTheLedgerAndAdvancesWithoutAnyHttpCall(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);
        // No server.expect(...) at all: any HTTP call would fail this test.

        ModelAndView mav = controller.skip(new ModelAndView(), 0);

        server.verify();
        assertEquals("redirect:/migration/review/1", mav.getViewName());
        assertTrue(ledgerFor(dir).isSkipped(TRACKED_BASENAME));
    }

    // --- Previous/Next: plain template links, not controller endpoints; the model flags
    // that gate their visibility are what's worth testing here. ---

    @Test
    void hasPreviousIsFalseOnTheFirstWorkoutAndTrueAfterIt(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView first = controller.view(new ModelAndView(), 0);
        ModelAndView second = controller.view(new ModelAndView(), 1);

        assertEquals(Boolean.FALSE, first.getModel().get("hasPrevious"));
        assertEquals(Boolean.TRUE, second.getModel().get("hasPrevious"));
    }

    @Test
    void hasNextIsFalseOnTheLastWorkout(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView last = controller.view(new ModelAndView(), 1);

        assertEquals(Boolean.FALSE, last.getModel().get("hasNext"));
    }

    // --- Stop: always goes to the summary ---

    @Test
    void stopGoesToTheSummaryPage(@TempDir Path dir) {
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.stop(new ModelAndView());

        assertEquals("redirect:/migration/review/summary", mav.getViewName());
    }

    // --- Summary counts ---

    @Test
    void summaryCountsEveryOutcomeAcrossTheWholePlan(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationLedger ledger = ledgerFor(dir);
        ledger.markPending(TRACKED_BASENAME, PlannedAction.UPLOAD_TCX);
        ledger.markDone(TRACKED_BASENAME, 42L);
        ledger.markSkipped(MANUAL_BASENAME, PlannedAction.CREATE_MANUAL);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.summary(new ModelAndView());

        assertEquals("migration/review-summary", mav.getViewName());
        assertEquals(1, mav.getModel().get("uploaded"));
        assertEquals(0, mav.getModel().get("manualCreated"));
        assertEquals(1, mav.getModel().get("skippedByUser"));
        assertEquals(0, mav.getModel().get("failed"));
        assertEquals(0, mav.getModel().get("remaining"));
        assertEquals(2, mav.getModel().get("total"));
    }

    @Test
    void summaryCountsUndecidedWorkoutsAsRemaining(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView mav = controller.summary(new ModelAndView());

        assertEquals(2, mav.getModel().get("remaining"));
    }

    // --- Missing archive ---

    @Test
    void viewShowsArchiveMissingRatherThanThrowing(@TempDir Path dir) {
        MigrationReviewController controller = controllerFor(dir);
        // dir/archive was never created.

        ModelAndView mav = controller.view(new ModelAndView(), 0);

        assertEquals("migration/review", mav.getViewName());
        assertEquals(Boolean.FALSE, mav.getModel().get("archivePresent"));
        assertFalse(mav.getModel().containsKey("workout"));
    }

    @Test
    void eachIndexResolvesToItsOwnWorkoutInPlanOrder(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);

        ModelAndView first = controller.view(new ModelAndView(), 0);
        ModelAndView second = controller.view(new ModelAndView(), 1);

        assertEquals(TRACKED_BASENAME, ((ResolvedWorkout) first.getModel().get("workout")).basename());
        assertEquals(MANUAL_BASENAME, ((ResolvedWorkout) second.getModel().get("workout")).basename());
    }

    // --- Plan caching: resolved once per archive, ledger status still read fresh ---

    /** Counts {@code resolveAll} calls while leaving every other executor behaviour to the real one. */
    private static final class CountingExecutor extends MigrationExecutor {

        private final MigrationExecutor delegate;
        private int resolveAllCalls;

        private CountingExecutor(MigrationExecutor delegate) {
            super(null, null, null, null, null, null);
            this.delegate = delegate;
        }

        @Override
        public List<ResolvedWorkout> resolveAll(Path archiveRoot) {
            resolveAllCalls++;
            return delegate.resolveAll(archiveRoot);
        }

    }

    @Test
    void viewingTheSameArchiveTwiceResolvesThePlanOnlyOnce(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        rig = MigrationTestSupport.build(dir, FIXED);
        server = rig.server();
        CountingExecutor executor = new CountingExecutor(rig.executor());
        MigrationReviewController controller = controllerWith(dir, executor);

        controller.view(new ModelAndView(), 0);
        controller.view(new ModelAndView(), 1);
        controller.start(new ModelAndView());

        assertEquals(1, executor.resolveAllCalls);
    }

    @Test
    void aSkipMadeThroughThePageShowsOnTheNextViewOfThatWorkout(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);
        controller.view(new ModelAndView(), 0);

        controller.skip(new ModelAndView(), 0);
        ModelAndView mav = controller.view(new ModelAndView(), 0);

        server.verify();
        assertEquals(Boolean.TRUE, mav.getModel().get("isSkipped"));
        assertEquals(Boolean.TRUE, mav.getModel().get("hasDecision"));
    }

    @Test
    void aMigrateMadeThroughThePageMovesTheNextStartRedirectOn(@TempDir Path dir) throws Exception {
        archiveWithTwoActionableWorkouts(dir);
        MigrationReviewController controller = controllerFor(dir);
        assertEquals("redirect:/migration/review/0", controller.start(new ModelAndView()).getViewName());

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andRespond(withSuccess("{\"id\": 555, \"activity_id\": 777}", APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));
        controller.migrate(new ModelAndView(), 0);

        server.verify();
        assertEquals("redirect:/migration/review/1", controller.start(new ModelAndView()).getViewName());
    }

}
