package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.Location;
import pl.kiraga.endomondoexportparser.format.json.Picture;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.service.InvalidWorkoutJsonException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves and geotags exactly one workout's photos, without scanning the rest of the
 * archive — {@link PhotoReportGenerator} does the equivalent for every workout at once
 * to build the handout report; this is the same logic, scoped to a single workout, for
 * the interactive migration review page (task 6.1), which only ever needs one workout's
 * photos per screen. Reuses {@link PhotoGeotagger}, so a photo already stamped by a
 * report generation (or an earlier review visit) is read back, not re-derived.
 */
@Service
public class WorkoutPhotoResolver {

    private final EndomondoJsonParser parser;
    private final PhotoGeotagger geotagger;

    public WorkoutPhotoResolver(EndomondoJsonParser parser, PhotoGeotagger geotagger) {
        this.parser = parser;
        this.geotagger = geotagger;
    }

    /**
     * {@code archiveRoot} is the archive root (containing "Workouts" and
     * "resources/gfx"); {@code jsonFile} is the workout's own JSON (e.g.
     * {@link ResolvedWorkout#jsonFile()}); {@code photoCopiesDirectory} is where geotagged
     * copies are written, matching {@link PhotoReportGenerator#generate}'s convention.
     */
    public List<Path> resolve(Path archiveRoot, Path jsonFile, Path photoCopiesDirectory) {

        EndomondoJson parsed = parseQuietly(jsonFile);
        if (parsed == null || parsed.getPictures().isEmpty()) {
            return List.of();
        }

        Location workoutFirstPoint = parsed.getPoints().isEmpty() ? null : parsed.getPoints().get(0).getLocation();

        List<Path> copies = new ArrayList<>();
        for (Picture picture : parsed.getPictures()) {
            if (picture.getUrl() == null) {
                continue;
            }
            Path relative = Path.of(picture.getUrl()).normalize();
            Path source = archiveRoot.resolve(relative);
            Path copy = photoCopiesDirectory.resolve(relative);
            GeotaggedPhoto geotagged = geotagger.handOut(source, copy, picture.getPoint(), workoutFirstPoint,
                    picture.getCreated_date());
            copies.add(geotagged.copy());
        }

        return List.copyOf(copies);

    }

    private EndomondoJson parseQuietly(Path json) {
        try {
            return parser.parse(Files.readAllBytes(json));
        } catch (InvalidWorkoutJsonException | IOException e) {
            return null;
        }
    }

}
