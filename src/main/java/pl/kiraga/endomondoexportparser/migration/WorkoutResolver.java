package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.Location;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.service.InvalidWorkoutJsonException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves each workout in a {@link MigrationPlan} to the exact name/description a real
 * migration would send to Strava, via {@link WorkoutNaming}/{@link WorkoutDescription}
 * and {@link PlaceLookup} — the one place that resolution logic lives, shared by
 * {@link WorkoutReportGenerator} (a preview) and {@link MigrationExecutor} (the real
 * thing) so the two can never show and do different things. Extracted from
 * {@code WorkoutReportGenerator} when {@link MigrationExecutor} needed the identical
 * logic (2026-07-21) rather than duplicating it.
 */
@Service
public class WorkoutResolver {

    private final ArchiveScanner scanner;
    private final EndomondoJsonParser parser;
    private final PlaceLookup placeLookup;

    public WorkoutResolver(ArchiveScanner scanner, EndomondoJsonParser parser, PlaceLookup placeLookup) {
        this.scanner = scanner;
        this.parser = parser;
        this.placeLookup = placeLookup;
    }

    public List<ResolvedWorkout> resolve(Path workoutsDirectory, MigrationPlan plan) {

        ArchiveScan scan = scanner.scan(workoutsDirectory);
        Map<String, WorkoutPair> byBasename = new HashMap<>();
        for (WorkoutPair workout : scan.workouts()) {
            byBasename.put(workout.basename(), workout);
        }

        List<ResolvedWorkout> resolved = new ArrayList<>();
        for (WorkoutPlan planned : plan.workouts()) {
            resolved.add(resolveOne(planned, byBasename.get(planned.basename())));
        }

        return List.copyOf(resolved);

    }

    private ResolvedWorkout resolveOne(WorkoutPlan planned, WorkoutPair pair) {

        if (planned.action() == PlannedAction.SKIP || pair == null) {
            return ResolvedWorkout.unresolved(planned);
        }

        EndomondoJson parsed = parseQuietly(pair.json());
        if (parsed == null) {
            return ResolvedWorkout.unresolved(planned);
        }

        PlaceDescription place = placeFor(parsed);
        String name = WorkoutNaming.resolve(planned.name(), planned.startTime(), planned.stravaSportType(), place);
        String description = WorkoutDescription.build(dateOnly(planned.startTime()), place);

        return ResolvedWorkout.resolved(planned, name, description, pair);

    }

    private PlaceDescription placeFor(EndomondoJson parsed) {
        if (parsed.getPoints().isEmpty()) {
            return null;
        }
        Location firstPoint = parsed.getPoints().get(0).getLocation();
        if (firstPoint == null || firstPoint.getLatitude() == null || firstPoint.getLongitude() == null) {
            return null;
        }
        return placeLookup.lookup(firstPoint.getLatitude(), firstPoint.getLongitude()).orElse(null);
    }

    /** Endomondo's start_time looks like "2015-04-11 11:37:00.0"; the date is a fixed prefix. */
    private static String dateOnly(String startTime) {
        return (startTime == null || startTime.length() < 10) ? startTime : startTime.substring(0, 10);
    }

    private EndomondoJson parseQuietly(Path json) {
        try {
            return parser.parse(Files.readAllBytes(json));
        } catch (InvalidWorkoutJsonException | IOException e) {
            // MigrationPlanner already reports unparseable workouts as skipped; this
            // second pass simply contributes no name/description for one.
            return null;
        }
    }

}
