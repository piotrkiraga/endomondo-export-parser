package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Pairs the archive's Workouts directory into workouts by basename: each
 * "YYYY-MM-DD HH_mm_ss.0" contributes a .json and usually a .tcx.
 */
@Service
public class ArchiveScanner {

    private static final String JSON = ".json";
    private static final String TCX = ".tcx";

    public ArchiveScan scan(Path workoutsDirectory) {

        if (!Files.isDirectory(workoutsDirectory)) {
            throw new IllegalArgumentException("Not a directory: " + workoutsDirectory);
        }

        Map<String, Path> jsonByBasename = new TreeMap<>();
        Map<String, Path> tcxByBasename = new TreeMap<>();

        try (Stream<Path> entries = Files.list(workoutsDirectory)) {
            entries.filter(Files::isRegularFile).forEach(file -> {
                String filename = file.getFileName().toString();
                String lowercase = filename.toLowerCase(Locale.ROOT);
                if (lowercase.endsWith(JSON)) {
                    jsonByBasename.put(withoutExtension(filename, JSON), file);
                } else if (lowercase.endsWith(TCX)) {
                    tcxByBasename.put(withoutExtension(filename, TCX), file);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read archive directory " + workoutsDirectory, e);
        }

        List<WorkoutPair> workouts = new ArrayList<>();
        jsonByBasename.forEach((basename, json) ->
                workouts.add(new WorkoutPair(basename, json, tcxByBasename.get(basename))));

        List<Path> tracksWithoutMetadata = tcxByBasename.entrySet().stream()
                .filter(entry -> !jsonByBasename.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        return new ArchiveScan(List.copyOf(workouts), tracksWithoutMetadata);

    }

    private String withoutExtension(String filename, String extension) {
        return filename.substring(0, filename.length() - extension.length());
    }

}
