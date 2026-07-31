package pl.kiraga.endomondoexportparser.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.dto.strava.StravaDictionarySnapshotDto;
import pl.kiraga.endomondoexportparser.model.AppStatus;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.service.MigrationTestSupport;

/**
 * Uses {@link MigrationTestSupport}'s mocked-network rig, the same safety bar
 * {@link MigrationReviewControllerTest} relies on — no {@code @SpringBootTest} here,
 * since that would resolve the real {@code data/generated/} files, and a real,
 * non-expired token saved there (as happens during normal manual use of this app) would
 * let {@code refresh()} reach the real Strava network during a test run.
 */
public class HomeControllerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);

    private HomeController controllerFor(MigrationTestSupport.Rig rig, Path dir) {
        HomeController controller = new HomeController(rig.stravaDictionary(), rig.tokenStore(), rig.appStatusResolver());
        controller.setArchiveRootProperty(dir.resolve("archive").toString());
        return controller;
    }

    private void archiveWith(Path dir, String... basenames) throws Exception {
        Path workouts = Files.createDirectories(dir.resolve("archive").resolve("Workouts"));
        byte[] content = getClass().getResourceAsStream("/fixtures/workout-tracked.json").readAllBytes();
        for (String basename : basenames) {
            Files.write(workouts.resolve(basename + ".json"), content);
        }
    }

    /**
     * {@code BaseController.message(...)} needs a real {@code MessageSource}; only the
     * refresh tests hit that path. Also pins the locale {@code message(...)} reads via
     * {@code LocaleContextHolder} — deterministic regardless of the JVM's default locale.
     */
    private static void giveItAMessageSource(HomeController controller) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages", "version");
        controller.setMessageSource(messageSource);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void viewShowsNoSnapshotBeforeAnyRefresh(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        HomeController controller = controllerFor(rig, dir);

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("home", mav.getViewName());
        assertNull(mav.getModel().get("snapshot"));
        assertEquals(Boolean.TRUE, mav.getModel().get("stravaConnected"));
    }

    @Test
    void refreshPopulatesTheSnapshotFromOneAthleteCall(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        HomeController controller = controllerFor(rig, dir);
        giveItAMessageSource(controller);
        MockRestServiceServer server = rig.server();
        server.expect(requestTo("https://www.strava.com/api/v3/athlete"))
                .andRespond(withSuccess("""
                        {"id": 555, "firstname": "Piotr", "lastname": "Kiraga",
                         "bikes": [{"id": "b18387038", "name": "Decathlon Riverside 5 Man"}], "shoes": []}
                        """, APPLICATION_JSON));

        ModelAndView mav = controller.refresh(new ModelAndView());

        assertEquals("home", mav.getViewName());
        StravaDictionarySnapshotDto snapshot = (StravaDictionarySnapshotDto) mav.getModel().get("snapshot");
        assertEquals("Piotr", snapshot.firstname());
        assertEquals("Decathlon Riverside 5 Man", snapshot.gearNames().get("b18387038"));
        server.verify();
    }

    @Test
    void viewCarriesTheAppStatusSnapshot(@TempDir Path dir) throws Exception {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        archiveWith(dir, "2011-09-10 12_58_59.0", "2014-09-16 09_05_21.0");
        rig.ledger().markPending("2011-09-10 12_58_59.0", PlannedAction.UPLOAD_TCX);
        rig.ledger().markDone("2011-09-10 12_58_59.0", 42L);
        HomeController controller = controllerFor(rig, dir);

        AppStatus status = (AppStatus) controller.view(new ModelAndView()).getModel().get("appStatus");

        assertEquals(2, status.archiveWorkoutCount());
        assertEquals(1, status.done());
        assertEquals(0, status.locationCacheSize());
    }

    @Test
    void viewStillRendersWithoutAnArchive(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        HomeController controller = controllerFor(rig, dir);

        AppStatus status = (AppStatus) controller.view(new ModelAndView()).getModel().get("appStatus");

        assertNull(status.archiveWorkoutCount());
    }

    @Test
    void refreshWithoutBeingConnectedReportsAnErrorRatherThanThrowing(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED, false);
        HomeController controller = controllerFor(rig, dir);
        giveItAMessageSource(controller);
        // No server.expect(...) at all: no stored token means no HTTP call is even attempted.

        ModelAndView mav = controller.refresh(new ModelAndView());

        assertEquals(1, ((List<?>) mav.getModel().get("errorMessages")).size());
        rig.server().verify();
    }

}
