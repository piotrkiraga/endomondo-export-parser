package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persists the last {@link StravaDictionarySnapshot} to disk (git-ignored with the rest
 * of personal data under {@code data/}), mirroring {@link StravaTokenStore}'s pattern —
 * so the dictionary survives an app restart without needing a fresh refresh.
 */
@Service
public class StravaDictionaryCache {

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReentrantLock lock = new ReentrantLock();

    public StravaDictionaryCache() {
        this(Path.of("data", "generated", "strava-dictionary.json"));
    }

    StravaDictionaryCache(Path file) {
        this.file = file;
    }

    public Optional<StravaDictionarySnapshot> load() {
        lock.lock();
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(Files.readAllBytes(file), StravaDictionarySnapshot.class));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    public void save(StravaDictionarySnapshot snapshot) {
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(snapshot), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write Strava dictionary cache to " + file, e);
        } finally {
            lock.unlock();
        }
    }

}
