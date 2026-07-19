package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.service.InvalidWorkoutJsonException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns an archive directory into a decision per workout. This is the dry run: it has
 * no Strava collaborator, so producing a plan cannot reach the network — the property
 * the dry-run requirement rests on is structural, not a flag that could be forgotten.
 */
@Service
public class MigrationPlanner {

    private static final String MANUAL_SOURCE = "INPUT_MANUAL";

    private final ArchiveScanner scanner;
    private final EndomondoJsonParser parser;

    public MigrationPlanner(ArchiveScanner scanner, EndomondoJsonParser parser) {
        this.scanner = scanner;
        this.parser = parser;
    }

    public MigrationPlan plan(Path workoutsDirectory) {

        ArchiveScan scan = scanner.scan(workoutsDirectory);

        List<WorkoutPlan> plans = new ArrayList<>();
        for (WorkoutPair workout : scan.workouts()) {
            plans.add(planOne(workout));
        }

        return new MigrationPlan(List.copyOf(plans), scan.tracksWithoutMetadata());

    }

    private WorkoutPlan planOne(WorkoutPair workout) {

        EndomondoJson parsed;
        try {
            parsed = parser.parse(Files.readAllBytes(workout.json()));
        } catch (InvalidWorkoutJsonException e) {
            return WorkoutPlan.skip(workout.basename(), "workout JSON could not be parsed: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + workout.json(), e);
        }

        if (parsed.getSport() == null) {
            return WorkoutPlan.skip(workout.basename(), "workout records no sport");
        }

        Optional<String> stravaSportType = SportMapping.stravaSportType(parsed.getSport());
        if (stravaSportType.isEmpty()) {
            return WorkoutPlan.skip(workout.basename(), "unmapped Endomondo sport: " + parsed.getSport());
        }

        boolean manual = MANUAL_SOURCE.equals(parsed.getSource());
        if (!manual && !workout.hasTrack()) {
            return WorkoutPlan.skip(workout.basename(), "tracked workout has no paired TCX file");
        }

        return new WorkoutPlan(
                workout.basename(),
                manual ? PlannedAction.CREATE_MANUAL : PlannedAction.UPLOAD_TCX,
                parsed.getName(),
                parsed.getSport(),
                stravaSportType.get(),
                parsed.getSource(),
                parsed.getStart_time(),
                parsed.getDistance_km(),
                parsed.getDuration_s(),
                parsed.getPictures().size(),
                null);

    }

}
