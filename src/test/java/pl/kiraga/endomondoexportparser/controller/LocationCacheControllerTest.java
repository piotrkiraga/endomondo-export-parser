package pl.kiraga.endomondoexportparser.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.model.CachedCoordinate;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.Locality;
import pl.kiraga.endomondoexportparser.model.LocationGroup;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.service.LocationCacheCountryBackfillService;
import pl.kiraga.endomondoexportparser.service.MigrationTestSupport;
import pl.kiraga.endomondoexportparser.service.NominatimClient;

/**
 * Same mocked-network rig as {@link HomeControllerTest}, for the same reason: no
 * {@code @SpringBootTest} here, so the real {@code data/} directory is never read.
 */
public class LocationCacheControllerTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", "Dębniki", new NearbyFeature("Vistula", FeatureKind.WATER, 50), "PL");

    private static final PlaceDescription DEBNIKI = new PlaceDescription("Kraków", "Dębniki", null, "PL");

    private static final PlaceDescription WARSAW = new PlaceDescription("Warszawa", "Śródmieście", null, "PL");

    private static final PlaceDescription BEFORE_COUNTRY_TRACKING =
            new PlaceDescription("Tervuren", "Tervuren", null, null);

    /** Answers every re-lookup with the same country, so the backfill test is about the handler, not the lookup. */
    private static final class BelgianNominatimClient extends NominatimClient {

        private BelgianNominatimClient() {
            super(RestClient.builder());
        }

        @Override
        public Optional<Locality> reverseGeocode(double latitude, double longitude) {
            return Optional.of(new Locality("Tervuren", null, "BE"));
        }

    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private LocationCacheController controllerFor(MigrationTestSupport.Rig rig) {
        LocationCacheController controller = new LocationCacheController(rig.locationCache(),
                new LocationCacheCountryBackfillService(rig.locationCache(), new BelgianNominatimClient()),
                rig.tokenStore());
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages", "version");
        controller.setMessageSource(messageSource);
        return controller;
    }

    @SuppressWarnings("unchecked")
    private List<LocationGroup> groupsFrom(ModelAndView modelAndView) {
        return (List<LocationGroup>) modelAndView.getModel().get("groups");
    }

    @Test
    void viewListsEachDistinctPlaceWithItsCoordinates(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("location-cache", mav.getViewName());
        assertEquals(List.of(new LocationGroup("Dębniki, Kraków, Poland", List.of(new CachedCoordinate(50.061, 19.937))),
                        new LocationGroup("Śródmieście, Warszawa, Poland", List.of(new CachedCoordinate(52.232, 21.006)))),
                groupsFrom(mav));
    }

    @Test
    void coordinatesSharingASummaryCollapseIntoOneGroup(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        rig.locationCache().put(50.05812, 19.93104, DEBNIKI);
        rig.locationCache().put(50.05812, 19.94420, DEBNIKI);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.view(new ModelAndView());

        List<LocationGroup> groups = groupsFrom(mav);
        assertEquals(2, groups.size());
        LocationGroup debniki = groups.get(0);
        assertEquals("Dębniki, Kraków, Poland", debniki.place());
        assertEquals(3, debniki.count());
        assertEquals(List.of(new CachedCoordinate(50.058, 19.931), new CachedCoordinate(50.058, 19.944),
                new CachedCoordinate(50.061, 19.937)), debniki.coordinates());
        assertEquals(1, groups.get(1).count());
    }

    @Test
    void groupsAreOrderedAlphabeticallyByPlace(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        rig.locationCache().put(50.82370, 4.51940, BEFORE_COUNTRY_TRACKING);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals(List.of("Dębniki, Kraków, Poland", "Tervuren", "Śródmieście, Warszawa, Poland"),
                groupsFrom(mav).stream().map(LocationGroup::place).toList());
    }

    @Test
    void placesFollowTheActiveUiLanguage(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        LocationCacheController controller = controllerFor(rig);

        LocaleContextHolder.setLocale(Locale.forLanguageTag("pl"));
        ModelAndView polish = controller.view(new ModelAndView());

        assertEquals("Dębniki, Kraków, Polska", groupsFrom(polish).get(0).place());
    }

    @Test
    void anEntryWithoutACountryCodeIsSummarisedWithoutOne(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(50.82370, 4.51940, BEFORE_COUNTRY_TRACKING);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals(new LocationGroup("Tervuren", List.of(new CachedCoordinate(50.824, 4.519))),
                groupsFrom(mav).get(0));
    }

    @Test
    void backfillFillsInMissingCountriesAndReportsHowManyOnAFreshlyBuiltPage(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(50.82370, 4.51940, BEFORE_COUNTRY_TRACKING);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.backfillCountries(new ModelAndView());

        assertEquals("location-cache", mav.getViewName());
        assertEquals(List.of("Country filled in for 1 cached location(s)"), mav.getModel().get("infoMessages"));
        assertEquals(List.of("Tervuren, Belgium", "Śródmieście, Warszawa, Poland"),
                groupsFrom(mav).stream().map(LocationGroup::place).toList());
    }

    @Test
    void backfillWithEveryCountryAlreadyKnownReportsThatThereWasNothingToDo(@TempDir Path dir) {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        rig.locationCache().put(52.23172, 21.00600, WARSAW);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.backfillCountries(new ModelAndView());

        assertEquals(List.of("Every cached location already has a country; nothing to fill in"),
                mav.getModel().get("infoMessages"));
        assertEquals(List.of("Śródmieście, Warszawa, Poland"),
                groupsFrom(mav).stream().map(LocationGroup::place).toList());
    }

    @Test
    void viewRendersAnEmptyListWhenNothingIsCached(@TempDir Path dir) {
        MigrationTestSupport.Rig rig = MigrationTestSupport.build(dir, FIXED);
        LocationCacheController controller = controllerFor(rig);

        ModelAndView mav = controller.view(new ModelAndView());

        assertEquals("location-cache", mav.getViewName());
        assertTrue(groupsFrom(mav).isEmpty());
    }

}
