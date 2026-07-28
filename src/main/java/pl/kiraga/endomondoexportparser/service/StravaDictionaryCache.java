package pl.kiraga.endomondoexportparser.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.dto.strava.StravaDictionarySnapshotDto;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists the last {@link StravaDictionarySnapshotDto} to disk (git-ignored with the rest
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

    public Optional<StravaDictionarySnapshotDto> load() {
        lock.lock();
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(Files.readAllBytes(file), StravaDictionarySnapshotDto.class));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    public void save(StravaDictionarySnapshotDto snapshot) {
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
