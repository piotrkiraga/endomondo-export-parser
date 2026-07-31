package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.Locality;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;

public class LocationCacheCountryBackfillServiceTest {

    private static final NearbyFeature VISTULA = new NearbyFeature("Vistula", FeatureKind.WATER, 50);

    /** Answers from a canned per-coordinate script and records what was asked, without any network. */
    private static final class ScriptedNominatimClient extends NominatimClient {

        private final Map<String, Optional<Locality>> answers;
        private final List<String> lookedUp = new ArrayList<>();

        private ScriptedNominatimClient(Map<String, Optional<Locality>> answers) {
            super(RestClient.builder());
            this.answers = answers;
        }

        @Override
        public Optional<Locality> reverseGeocode(double latitude, double longitude) {
            String key = String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude);
            lookedUp.add(key);
            return answers.getOrDefault(key, Optional.empty());
        }

    }

    @Test
    void fillsInTheCountryAndLeavesEveryOtherFieldAsCached(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("location-cache.json"));
        cache.put(50.06143, 19.93658, new PlaceDescription("Kraków", "Dębniki", VISTULA, null));
        ScriptedNominatimClient nominatim = new ScriptedNominatimClient(
                Map.of("50.061,19.937", Optional.of(new Locality("Krakau", null, "PL"))));

        int updated = new LocationCacheCountryBackfillService(cache, nominatim).backfillMissingCountries();

        assertEquals(1, updated);
        assertEquals(new PlaceDescription("Kraków", "Dębniki", VISTULA, "PL"),
                cache.get(50.06143, 19.93658).orElseThrow());
    }

    @Test
    void entriesThatAlreadyHaveACountryAreNeverLookedUp(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("location-cache.json"));
        cache.put(50.06143, 19.93658, new PlaceDescription("Kraków", "Dębniki", null, "PL"));
        cache.put(50.82370, 4.51940, new PlaceDescription("Tervuren", null, null, null));
        ScriptedNominatimClient nominatim = new ScriptedNominatimClient(
                Map.of("50.824,4.519", Optional.of(new Locality("Tervuren", null, "BE"))));

        int updated = new LocationCacheCountryBackfillService(cache, nominatim).backfillMissingCountries();

        assertEquals(1, updated);
        assertEquals(List.of("50.824,4.519"), nominatim.lookedUp);
        assertEquals("BE", cache.get(50.82370, 4.51940).orElseThrow().countryCode());
    }

    @Test
    void aFailedOrCountrylessRelookupLeavesThatEntryAloneAndTheBatchContinues(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("location-cache.json"));
        cache.put(0.0, 0.0, new PlaceDescription("Nowhere", null, null, null));
        cache.put(52.23172, 21.00600, new PlaceDescription("Warszawa", "Śródmieście", null, null));
        cache.put(50.82370, 4.51940, new PlaceDescription("Tervuren", null, null, null));
        ScriptedNominatimClient nominatim = new ScriptedNominatimClient(Map.of(
                "52.232,21.006", Optional.of(new Locality("Warszawa", "Śródmieście", null)),
                "50.824,4.519", Optional.of(new Locality("Tervuren", null, "BE"))));

        int updated = new LocationCacheCountryBackfillService(cache, nominatim).backfillMissingCountries();

        assertEquals(1, updated);
        assertEquals(3, nominatim.lookedUp.size());
        assertNull(cache.get(0.0, 0.0).orElseThrow().countryCode());
        assertNull(cache.get(52.23172, 21.00600).orElseThrow().countryCode());
        assertEquals("BE", cache.get(50.82370, 4.51940).orElseThrow().countryCode());
    }

    @Test
    void anEmptyCacheUpdatesNothingAndLooksNothingUp(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("location-cache.json"));
        ScriptedNominatimClient nominatim = new ScriptedNominatimClient(Map.of());

        assertEquals(0, new LocationCacheCountryBackfillService(cache, nominatim).backfillMissingCountries());
        assertTrue(nominatim.lookedUp.isEmpty());
    }

    @Test
    void aCacheWhereEveryEntryHasACountryUpdatesNothingAndLooksNothingUp(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("location-cache.json"));
        cache.put(50.06143, 19.93658, new PlaceDescription("Kraków", "Dębniki", null, "PL"));
        cache.put(50.82370, 4.51940, new PlaceDescription("Tervuren", null, null, "BE"));
        ScriptedNominatimClient nominatim = new ScriptedNominatimClient(Map.of());

        assertEquals(0, new LocationCacheCountryBackfillService(cache, nominatim).backfillMissingCountries());
        assertTrue(nominatim.lookedUp.isEmpty());
    }

}
