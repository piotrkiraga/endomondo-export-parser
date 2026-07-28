package pl.kiraga.endomondoexportparser.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.dto.strava.StravaTokensDto;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists the Strava access/refresh token pair to disk (git-ignored with the rest of
 * personal data under {@code data/}), so the app doesn't need to re-run the OAuth
 * connect flow (task 3.2) on every restart.
 */
@Service
public class StravaTokenStore {

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReentrantLock lock = new ReentrantLock();

    public StravaTokenStore() {
        this(Path.of("data", "generated", "strava-tokens.json"));
    }

    StravaTokenStore(Path file) {
        this.file = file;
    }

    public Optional<StravaTokensDto> load() {
        lock.lock();
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(Files.readAllBytes(file), StravaTokensDto.class));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    public void save(StravaTokensDto tokens) {
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(tokens), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write Strava tokens to " + file, e);
        } finally {
            lock.unlock();
        }
    }

}
