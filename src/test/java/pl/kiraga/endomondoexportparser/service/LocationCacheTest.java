package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;

public class LocationCacheTest {

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50), null);

    private static final PlaceDescription WARSAW = new PlaceDescription("Warszawa", "Śródmieście", null, null);

    @Test
    void missIsEmpty(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("cache.json"));
        assertTrue(cache.get(50.06143, 19.93658).isEmpty());
    }

    @Test
    void putThenGetRoundTrips(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("cache.json"));
        cache.put(50.06143, 19.93658, VISTULA_IN_KRAKOW);

        Optional<PlaceDescription> found = cache.get(50.06143, 19.93658);

        assertEquals(VISTULA_IN_KRAKOW, found.orElseThrow());
    }

    @Test
    void coordinatesRoundedToTheSameKeyShareAnEntry(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("cache.json"));
        cache.put(50.06143, 19.93658, VISTULA_IN_KRAKOW);

        assertEquals(VISTULA_IN_KRAKOW, cache.get(50.06149, 19.93655).orElseThrow());
    }

    @Test
    void sizeIsZeroWithoutACacheFile(@TempDir Path dir) {
        assertEquals(0, new LocationCache(dir.resolve("cache.json")).size());
    }

    @Test
    void sizeCountsDistinctCachedCoordinates(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("cache.json"));
        cache.put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        cache.put(52.23172, 21.00600, VISTULA_IN_KRAKOW);
        cache.put(50.06149, 19.93655, VISTULA_IN_KRAKOW);

        assertEquals(2, cache.size(), "coordinates rounding to the same key share one entry");
    }

    @Test
    void entriesAreEmptyWithoutACacheFile(@TempDir Path dir) {
        assertTrue(new LocationCache(dir.resolve("cache.json")).entries().isEmpty());
    }

    @Test
    void entriesExposeEveryStoredCoordinate(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("cache.json"));
        cache.put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        cache.put(52.23172, 21.00600, WARSAW);

        Map<String, PlaceDescription> entries = cache.entries();

        assertEquals(2, entries.size());
        assertEquals(VISTULA_IN_KRAKOW, entries.get("50.061,19.937"));
        assertEquals(WARSAW, entries.get("52.232,21.006"));
    }

    @Test
    void entriesCannotBeModified(@TempDir Path dir) {
        LocationCache cache = new LocationCache(dir.resolve("cache.json"));
        cache.put(50.06143, 19.93658, VISTULA_IN_KRAKOW);

        Map<String, PlaceDescription> entries = cache.entries();

        assertThrows(UnsupportedOperationException.class, () -> entries.put("0.000,0.000", WARSAW));
    }

    @Test
    void aCountryCodeSurvivesTheJsonRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("cache.json");
        PlaceDescription krakow = new PlaceDescription("Kraków", "Dębniki", null, "PL");
        new LocationCache(file).put(50.06143, 19.93658, krakow);

        PlaceDescription found = new LocationCache(file).get(50.06143, 19.93658).orElseThrow();

        assertEquals(krakow, found);
        assertEquals("PL", found.countryCode());
    }

    @Test
    void anEntryStoredWithoutACountryCodeFieldLoadsWithANullCountryCode(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cache.json");
        Files.writeString(file, """
                {"50.061,19.937": {"locality": "Kraków", "suburb": "Dębniki", "nearbyFeature": null}}
                """, StandardCharsets.UTF_8);

        PlaceDescription found = new LocationCache(file).get(50.06143, 19.93658).orElseThrow();

        assertEquals("Kraków", found.locality());
        assertNull(found.countryCode());
    }

    @Test
    void survivesAFreshInstanceReadingTheSameFile(@TempDir Path dir) {
        Path file = dir.resolve("cache.json");
        new LocationCache(file).put(50.06143, 19.93658, VISTULA_IN_KRAKOW);

        LocationCache reopened = new LocationCache(file);

        assertEquals(VISTULA_IN_KRAKOW, reopened.get(50.06143, 19.93658).orElseThrow());
        assertTrue(Files.isRegularFile(file));
    }

}
