package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StravaDictionaryCacheTest {

    private static final StravaDictionarySnapshot SNAPSHOT = new StravaDictionarySnapshot(
            555L, "Piotr", "Kiraga", "https://example.com/p.jpg", "Kraków", null, "Poland",
            Map.of("b18387038", "Decathlon Riverside 5 Man"), "2026-07-27T12:00:00Z");

    @Test
    void missingFileYieldsEmpty(@TempDir Path dir) {
        StravaDictionaryCache cache = new StravaDictionaryCache(dir.resolve("dictionary.json"));
        assertTrue(cache.load().isEmpty());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) {
        StravaDictionaryCache cache = new StravaDictionaryCache(dir.resolve("dictionary.json"));
        cache.save(SNAPSHOT);

        assertEquals(SNAPSHOT, cache.load().orElseThrow());
    }

    @Test
    void laterSaveReplacesTheEarlierSnapshotWhole(@TempDir Path dir) {
        StravaDictionaryCache cache = new StravaDictionaryCache(dir.resolve("dictionary.json"));
        cache.save(SNAPSHOT);
        StravaDictionarySnapshot replacement = new StravaDictionarySnapshot(
                555L, "Piotr", "Kiraga", "https://example.com/p.jpg", "Kraków", null, "Poland",
                Map.of("b18387038", "Decathlon Riverside 5 Man", "b18305600", "Trek Cross Dual Sport 3"),
                "2026-07-27T13:00:00Z");
        cache.save(replacement);

        assertEquals(replacement, cache.load().orElseThrow());
    }

}
