package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;

public class LocationCacheTest {

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50));

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
    void survivesAFreshInstanceReadingTheSameFile(@TempDir Path dir) {
        Path file = dir.resolve("cache.json");
        new LocationCache(file).put(50.06143, 19.93658, VISTULA_IN_KRAKOW);

        LocationCache reopened = new LocationCache(file);

        assertEquals(VISTULA_IN_KRAKOW, reopened.get(50.06143, 19.93658).orElseThrow());
        assertTrue(Files.isRegularFile(file));
    }

}
