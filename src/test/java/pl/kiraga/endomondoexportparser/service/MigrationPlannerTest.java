package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.model.ArchiveScan;
import pl.kiraga.endomondoexportparser.model.MigrationPlan;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.model.WorkoutPair;
import pl.kiraga.endomondoexportparser.model.WorkoutPlan;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

/**
 * Exercises the scanner and planner over archives assembled from the scrubbed fixtures.
 * The planner is constructed with no Strava collaborator, which is what makes the
 * dry run incapable of reaching the network.
 */
public class MigrationPlannerTest {

    private final MigrationPlanner planner = new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser());
    private final ArchiveScanner scanner = new ArchiveScanner();

    private void copyFixture(Path directory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(directory.resolve(basename + ".json"), content);
    }

    private void writeTrack(Path directory, String basename) throws Exception {
        Files.write(directory.resolve(basename + ".tcx"), "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
    }

    // --- Scanner ---

    @Test
    void scannerPairsFilesByBasename(@TempDir Path archive) throws Exception {
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(archive, "2011-09-10 12_58_59.0");

        ArchiveScan scan = scanner.scan(archive);

        assertEquals(1, scan.workouts().size());
        WorkoutPair pair = scan.workouts().get(0);
        assertEquals("2011-09-10 12_58_59.0", pair.basename());
        assertTrue(pair.hasTrack());
        assertTrue(scan.tracksWithoutMetadata().isEmpty());
    }

    @Test
    void scannerReportsTracksThatHaveNoMetadata(@TempDir Path archive) throws Exception {
        writeTrack(archive, "2011-09-10 12_58_59.0");

        ArchiveScan scan = scanner.scan(archive);

        assertTrue(scan.workouts().isEmpty());
        assertEquals(1, scan.tracksWithoutMetadata().size());
    }

    // --- Planner decisions ---

    @Test
    void trackedWorkoutWithTrackPlansAnUpload(@TempDir Path archive) throws Exception {
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(archive, "2011-09-10 12_58_59.0");

        WorkoutPlan plan = planner.plan(archive).workouts().get(0);

        assertEquals(PlannedAction.UPLOAD_TCX, plan.action());
        assertEquals("Sample tracked ride", plan.name());
        assertEquals("CYCLING_SPORT", plan.endomondoSport());
        assertEquals("Ride", plan.stravaSportType());
        assertEquals(34.04, plan.distanceKm());
        assertNull(plan.reason());
    }

    @Test
    void manualWorkoutPlansAManualCreateAndIgnoresItsTrack(@TempDir Path archive) throws Exception {
        copyFixture(archive, "workout-manual.json", "2014-09-16 09_05_21.0");
        writeTrack(archive, "2014-09-16 09_05_21.0");

        WorkoutPlan plan = planner.plan(archive).workouts().get(0);

        assertEquals(PlannedAction.CREATE_MANUAL, plan.action());
        assertEquals("INPUT_MANUAL", plan.source());
        assertEquals("Walk", plan.stravaSportType());
    }

    @Test
    void trackedWorkoutWithoutTrackIsSkippedWithReason(@TempDir Path archive) throws Exception {
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");

        WorkoutPlan plan = planner.plan(archive).workouts().get(0);

        assertEquals(PlannedAction.SKIP, plan.action());
        assertTrue(plan.reason().contains("no paired TCX"));
    }

    @Test
    void unmappedSportSkipsOnlyThatWorkout(@TempDir Path archive) throws Exception {
        Files.write(archive.resolve("2016-01-01 10_00_00.0.json"),
                "[{\"name\": \"Kite session\"}, {\"sport\": \"KITESURFING\"}, {\"source\": \"TRACK_MOBILE\"}]"
                        .getBytes(StandardCharsets.UTF_8));
        writeTrack(archive, "2016-01-01 10_00_00.0");
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(archive, "2011-09-10 12_58_59.0");

        MigrationPlan plan = planner.plan(archive);

        assertEquals(1, plan.count(PlannedAction.UPLOAD_TCX));
        assertEquals(1, plan.count(PlannedAction.SKIP));
        assertTrue(plan.skipped().get(0).reason().contains("KITESURFING"));
    }

    @Test
    void unparseableJsonSkipsWithoutFailingTheRun(@TempDir Path archive) throws Exception {
        Files.write(archive.resolve("broken.json"), "not json at all".getBytes(StandardCharsets.UTF_8));
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(archive, "2011-09-10 12_58_59.0");

        MigrationPlan plan = planner.plan(archive);

        assertEquals(2, plan.workouts().size());
        assertEquals(1, plan.count(PlannedAction.UPLOAD_TCX));
        assertEquals(1, plan.count(PlannedAction.SKIP));
    }

    // --- Plan totals ---

    @Test
    void planSummarisesActionsPicturesAndSports(@TempDir Path archive) throws Exception {
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(archive, "2011-09-10 12_58_59.0");
        copyFixture(archive, "workout-manual.json", "2014-09-16 09_05_21.0");
        copyFixture(archive, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(archive, "2015-04-11 11_38_17.0");

        MigrationPlan plan = planner.plan(archive);

        assertEquals(3, plan.workouts().size());
        assertEquals(2, plan.count(PlannedAction.UPLOAD_TCX));
        assertEquals(1, plan.count(PlannedAction.CREATE_MANUAL));
        assertEquals(0, plan.count(PlannedAction.SKIP));

        assertEquals(2, plan.totalPictures());
        assertEquals(1, plan.workoutsWithPictures());

        assertEquals(List.of("CYCLING_SPORT", "WALKING"), List.copyOf(plan.sportCounts().keySet()));
        assertEquals(2, plan.sportCounts().get("CYCLING_SPORT"));
    }

    @Test
    void workoutsAreOrderedByBasenameSoPlansReadChronologically(@TempDir Path archive) throws Exception {
        copyFixture(archive, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(archive, "2015-04-11 11_38_17.0");
        copyFixture(archive, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(archive, "2011-09-10 12_58_59.0");

        MigrationPlan plan = planner.plan(archive);

        assertEquals("2011-09-10 12_58_59.0", plan.workouts().get(0).basename());
        assertEquals("2015-04-11 11_38_17.0", plan.workouts().get(1).basename());
    }

}
