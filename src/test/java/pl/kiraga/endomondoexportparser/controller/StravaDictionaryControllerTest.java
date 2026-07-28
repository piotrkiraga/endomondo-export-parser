package pl.kiraga.endomondoexportparser.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import pl.kiraga.endomondoexportparser.service.MigrationTestSupport;

/**
 * Uses {@link MigrationTestSupport}'s mocked-network rig, the same safety bar
 * {@link MigrationReviewControllerTest} relies on — no {@code @SpringBootTest} here,
 * since that would resolve the real {@code data/generated/} files, and a real,
 * non-expired token saved there (as happens during normal manual use of this app) would
 * let {@code refresh()} reach the real Strava network during a test run.
 */
public class StravaDictionaryControllerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);

    /**
     * {@code BaseController.message(...)} needs a real {@code MessageSource}; only the
     * refresh tests hit that path. Also pins the locale {@code message(...)} reads via
     * {@code LocaleContextHolder} — deterministic regardless of the JVM's default locale.
     */
    private static void giveItAMessageSource(StravaDictionaryController controller) {
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
        StravaDictionaryController controller = new StravaDictionaryController(rig.stravaDictionary(), rig.tokenStore());

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("migration/strava-dictionary", mav.getViewName());
        assertNull(mav.getModel().get("snapshot"));
        assertEquals(Boolean.TRUE, mav.getModel().get("stravaConnected"));
    }

    @Test
    void refreshPopulatesTheSnapshotFromOneAthleteCall(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        StravaDictionaryController controller = new StravaDictionaryController(rig.stravaDictionary(), rig.tokenStore());
        giveItAMessageSource(controller);
        MockRestServiceServer server = rig.server();
        server.expect(requestTo("https://www.strava.com/api/v3/athlete"))
                .andRespond(withSuccess("""
                        {"id": 555, "firstname": "Piotr", "lastname": "Kiraga",
                         "bikes": [{"id": "b18387038", "name": "Decathlon Riverside 5 Man"}], "shoes": []}
                        """, APPLICATION_JSON));

        ModelAndView mav = controller.refresh(new ModelAndView());

        StravaDictionarySnapshotDto snapshot = (StravaDictionarySnapshotDto) mav.getModel().get("snapshot");
        assertEquals("Piotr", snapshot.firstname());
        assertEquals("Decathlon Riverside 5 Man", snapshot.gearNames().get("b18387038"));
        server.verify();
    }

    @Test
    void refreshWithoutBeingConnectedReportsAnErrorRatherThanThrowing(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED, false);
        StravaDictionaryController controller = new StravaDictionaryController(rig.stravaDictionary(), rig.tokenStore());
        giveItAMessageSource(controller);
        // No server.expect(...) at all: no stored token means no HTTP call is even attempted.

        ModelAndView mav = controller.refresh(new ModelAndView());

        assertEquals(1, ((List<?>) mav.getModel().get("errorMessages")).size());
        rig.server().verify();
    }

}
