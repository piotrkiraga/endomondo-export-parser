package pl.kiraga.endomondoexportparser.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Disk-backed cache of gear ids already read back from Strava, keyed by activity id, with
 * the same lock-and-reload-per-call pattern as {@link LocationCache}. The workout report
 * reads one confirmed gear id per already-migrated workout, which over a real archive is
 * far more calls than Strava's rate limit allows in one window — cached, a regeneration
 * makes none of them. An empty-string value is a real answer ("Strava confirmed no gear"),
 * matching {@link ConfirmedGearResolver#gearIdFor}'s own semantics.
 */
@Service
public class ConfirmedGearCache {

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, String> entries;

    public ConfirmedGearCache() {
        this(Path.of("data", "generated", "confirmed-gear-cache.json"));
    }

    ConfirmedGearCache(Path file) {
        this.file = file;
    }

    public Optional<String> get(long activityId) {
        lock.lock();
        try {
            return Optional.ofNullable(load().get(key(activityId)));
        } finally {
            lock.unlock();
        }
    }

    public void put(long activityId, String gearId) {
        lock.lock();
        try {
            Map<String, String> current = load();
            current.put(key(activityId), gearId);
            save(current);
        } finally {
            lock.unlock();
        }
    }

    private Map<String, String> load() {

        if (entries != null) {
            return entries;
        }

        entries = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return entries;
        }

        try {
            JsonNode root = mapper.readTree(Files.readAllBytes(file));
            if (root.isObject()) {
                for (Map.Entry<String, JsonNode> property : root.properties()) {
                    entries.put(property.getKey(), property.getValue().asString());
                }
            }
        } catch (IOException | RuntimeException e) {
            entries = new HashMap<>();
        }

        return entries;

    }

    private void save(Map<String, String> current) {

        ObjectNode root = mapper.createObjectNode();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            root.put(entry.getKey(), entry.getValue());
        }

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write confirmed gear cache " + file, e);
        }

    }

    private String key(long activityId) {
        return String.valueOf(activityId);
    }

}
