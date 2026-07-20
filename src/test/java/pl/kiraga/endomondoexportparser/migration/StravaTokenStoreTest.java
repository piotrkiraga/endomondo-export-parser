package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StravaTokenStoreTest {

    private static final StravaTokens TOKENS = new StravaTokens("access-1", "refresh-1", 1_800_000_000L);

    @Test
    void missingFileYieldsEmpty(@TempDir Path dir) {
        StravaTokenStore store = new StravaTokenStore(dir.resolve("tokens.json"));
        assertTrue(store.load().isEmpty());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) {
        StravaTokenStore store = new StravaTokenStore(dir.resolve("tokens.json"));
        store.save(TOKENS);

        assertEquals(TOKENS, store.load().orElseThrow());
    }

    @Test
    void survivesAFreshInstanceReadingTheSameFile(@TempDir Path dir) {
        Path file = dir.resolve("tokens.json");
        new StravaTokenStore(file).save(TOKENS);

        assertEquals(TOKENS, new StravaTokenStore(file).load().orElseThrow());
    }

    @Test
    void laterSaveOverwritesTheEarlierOne(@TempDir Path dir) {
        StravaTokenStore store = new StravaTokenStore(dir.resolve("tokens.json"));
        store.save(TOKENS);
        StravaTokens replacement = new StravaTokens("access-2", "refresh-2", 1_900_000_000L);
        store.save(replacement);

        assertEquals(replacement, store.load().orElseThrow());
    }

}
