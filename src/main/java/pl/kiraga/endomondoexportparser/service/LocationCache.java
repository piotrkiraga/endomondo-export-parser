package pl.kiraga.endomondoexportparser.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Disk-backed cache of resolved places, keyed by coordinate rounded to 3 decimal
 * places (~110m). Most of the archive's workouts start from a handful of real-world
 * places (home, work, a few regular routes), so this collapses what would be one
 * Nominatim + Overpass lookup per workout down to a handful of real network calls —
 * on this run, and to zero on every rerun after.
 */
@Service
public class LocationCache {

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, PlaceDescription> entries;

    public LocationCache() {
        this(Path.of("data", "generated", "location-cache.json"));
    }

    LocationCache(Path file) {
        this.file = file;
    }

    public Optional<PlaceDescription> get(double latitude, double longitude) {
        lock.lock();
        try {
            return Optional.ofNullable(load().get(key(latitude, longitude)));
        } finally {
            lock.unlock();
        }
    }

    public void put(double latitude, double longitude, PlaceDescription place) {
        lock.lock();
        try {
            Map<String, PlaceDescription> current = load();
            current.put(key(latitude, longitude), place);
            save(current);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return load().size();
        } finally {
            lock.unlock();
        }
    }

    public Map<String, PlaceDescription> entries() {
        lock.lock();
        try {
            return Map.copyOf(load());
        } finally {
            lock.unlock();
        }
    }

    private Map<String, PlaceDescription> load() {

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
                    entries.put(property.getKey(), mapper.treeToValue(property.getValue(), PlaceDescription.class));
                }
            }
        } catch (IOException | RuntimeException e) {
            entries = new HashMap<>();
        }

        return entries;

    }

    private void save(Map<String, PlaceDescription> current) {

        ObjectNode root = mapper.createObjectNode();
        for (Map.Entry<String, PlaceDescription> entry : current.entrySet()) {
            root.set(entry.getKey(), mapper.valueToTree(entry.getValue()));
        }

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write location cache " + file, e);
        }

    }

    private String key(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude);
    }

}
