package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConfirmedGearCacheTest {

    @Test
    void missIsEmpty(@TempDir Path dir) {
        ConfirmedGearCache cache = new ConfirmedGearCache(dir.resolve("cache.json"));
        assertTrue(cache.get(1234567890L).isEmpty());
    }

    @Test
    void putThenGetRoundTrips(@TempDir Path dir) {
        ConfirmedGearCache cache = new ConfirmedGearCache(dir.resolve("cache.json"));
        cache.put(1234567890L, "b9876543");

        assertEquals("b9876543", cache.get(1234567890L).orElseThrow());
    }

    @Test
    void confirmedNoGearRoundTripsAsAnEmptyString(@TempDir Path dir) {
        ConfirmedGearCache cache = new ConfirmedGearCache(dir.resolve("cache.json"));
        cache.put(1234567890L, "");

        assertEquals("", cache.get(1234567890L).orElseThrow(), "empty string is a real answer, not a miss");
    }

    @Test
    void survivesAFreshInstanceReadingTheSameFile(@TempDir Path dir) {
        Path file = dir.resolve("cache.json");
        new ConfirmedGearCache(file).put(1234567890L, "b9876543");

        ConfirmedGearCache reopened = new ConfirmedGearCache(file);

        assertEquals("b9876543", reopened.get(1234567890L).orElseThrow());
        assertTrue(Files.isRegularFile(file));
    }

}
