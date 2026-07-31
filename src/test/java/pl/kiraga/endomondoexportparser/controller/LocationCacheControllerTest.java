package pl.kiraga.endomondoexportparser.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.model.CachedCoordinate;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.service.MigrationTestSupport;

/**
 * Same mocked-network rig as {@link HomeControllerTest}, for the same reason: no
 * {@code @SpringBootTest} here, so the real {@code data/} directory is never read.
 */
public class LocationCacheControllerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", "Dębniki", new NearbyFeature("Vistula", FeatureKind.WATER, 50), "PL");

    private static final PlaceDescription WARSAW = new PlaceDescription("Warszawa", "Śródmieście", null, "PL");

    private static final PlaceDescription BEFORE_COUNTRY_TRACKING =
            new PlaceDescription("Tervuren", "Tervuren", null, null);

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @SuppressWarnings("unchecked")
    private Map<CachedCoordinate, String> entriesFrom(ModelAndView modelAndView) {
        return (Map<CachedCoordinate, String>) modelAndView.getModel().get("entries");
    }

    @Test
    void viewListsEveryCachedCoordinateWithItsSummary(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        LocationCacheController controller = new LocationCacheController(rig.locationCache(), rig.tokenStore());

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("location-cache", mav.getViewName());
        Map<CachedCoordinate, String> entries = entriesFrom(mav);
        assertEquals(2, entries.size());
        assertEquals("Dębniki, Kraków, Poland", entries.get(new CachedCoordinate(50.061, 19.937)));
        assertEquals("Śródmieście, Warszawa, Poland", entries.get(new CachedCoordinate(52.232, 21.006)));
    }

    @Test
    void summariesFollowTheActiveUiLanguage(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        LocationCacheController controller = new LocationCacheController(rig.locationCache(), rig.tokenStore());

        LocaleContextHolder.setLocale(Locale.forLanguageTag("pl"));
        ModelAndView polish = controller.view(new ModelAndView());

        assertEquals("Dębniki, Kraków, Polska", entriesFrom(polish).get(new CachedCoordinate(50.061, 19.937)));
    }

    @Test
    void anEntryWithoutACountryCodeIsSummarisedWithoutOne(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(50.82370, 4.51940, BEFORE_COUNTRY_TRACKING);
        LocationCacheController controller = new LocationCacheController(rig.locationCache(), rig.tokenStore());

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("Tervuren", entriesFrom(mav).get(new CachedCoordinate(50.824, 4.519)));
    }

    @Test
    void viewListsEntriesSortedByCoordinate(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        LocationCacheController controller = new LocationCacheController(rig.locationCache(), rig.tokenStore());

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals(List.of(new CachedCoordinate(50.061, 19.937), new CachedCoordinate(52.232, 21.006)),
                new ArrayList<>(entriesFrom(mav).keySet()));
    }

    @Test
    void viewRendersAnEmptyListWhenNothingIsCached(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        LocationCacheController controller = new LocationCacheController(rig.locationCache(), rig.tokenStore());

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("location-cache", mav.getViewName());
        assertTrue(entriesFrom(mav).isEmpty());
    }

}
