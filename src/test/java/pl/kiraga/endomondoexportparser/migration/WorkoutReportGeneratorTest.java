package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds archives from the fixtures used elsewhere in the migration package, the same
 * way {@link MigrationPlannerTest} does. {@link PlaceLookup} is stubbed throughout: no
 * test in this class touches the network, matching the report's offline guarantee.
 */
public class WorkoutReportGeneratorTest {

    private static final String CREDIT_LINE =
            "Migrated by Piotr Kiraga using endomondo-export-parser: "
                    + "https://github.com/piotrkiraga/endomondo-export-parser";
    private static final PlaceLookup NO_PLACES = (lat, lon) -> Optional.empty();
    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50));
    private static final PlaceLookup ALWAYS_VISTULA = (lat, lon) -> Optional.of(VISTULA_IN_KRAKOW);

    private final WorkoutReportGenerator generator = generatorWith(NO_PLACES);

    private WorkoutReportGenerator generatorWith(PlaceLookup placeLookup) {
        return new WorkoutReportGenerator(
                new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser()),
                new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), placeLookup));
    }

    private void copyFixture(Path workoutsDirectory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(workoutsDirectory.resolve(basename + ".json"), content);
    }

    private void writeTrack(Path workoutsDirectory, String basename) throws Exception {
        Files.write(workoutsDirectory.resolve(basename + ".tcx"), "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
    }

    // --- Action and resolved fields mirror the planner and Strava-naming conventions ---

    @Test
    void trackedWorkoutGetsAnUploadEntryWithTheJsonName(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        WorkoutReportEntry entry = generator.build(archiveRoot).entries().get(0);

        assertEquals(PlannedAction.UPLOAD_TCX, entry.action());
        assertEquals("Sample tracked ride", entry.stravaName());
        assertEquals("Ride", entry.stravaSportType());
        assertEquals(34.04, entry.distanceKm());
        assertEquals("Migrated from Endomondo (recorded 2011-09-10).\n\n" + CREDIT_LINE, entry.stravaDescription());
        assertNull(entry.reason());
    }

    @Test
    void manualWorkoutGetsAManualCreateEntry(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-manual.json", "2014-09-16 09_05_21.0");

        WorkoutReportEntry entry = generator.build(archiveRoot).entries().get(0);

        assertEquals(PlannedAction.CREATE_MANUAL, entry.action());
        assertEquals("Sample manual walk", entry.stravaName());
        assertEquals("Walk", entry.stravaSportType());
    }

    @Test
    void trackedWorkoutWithoutTrackIsSkippedWithReasonAndNoResolvedFields(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");

        WorkoutReportEntry entry = generator.build(archiveRoot).entries().get(0);

        assertEquals(PlannedAction.SKIP, entry.action());
        assertTrue(entry.reason().contains("no paired TCX"));
        assertNull(entry.stravaName());
        assertNull(entry.stravaDescription());
    }

    // --- Naming/description resolution matches the photo report's conventions ---

    @Test
    void unnamedWorkoutFallsBackToTimeOfDayAndSport(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        Files.write(workouts.resolve("2016-02-02 07_15_00.0.json"), ("["
                + "{\"sport\": \"RUNNING\"},"
                + "{\"source\": \"TRACK_MOBILE\"},"
                + "{\"start_time\": \"2016-02-02 07:15:00.0\"}"
                + "]").getBytes(StandardCharsets.UTF_8));
        writeTrack(workouts, "2016-02-02 07_15_00.0");

        WorkoutReportEntry entry = generator.build(archiveRoot).entries().get(0);

        assertEquals("Morning Run", entry.stravaName());
        assertEquals("Migrated from Endomondo (recorded 2016-02-02).\n\n" + CREDIT_LINE, entry.stravaDescription());
    }

    @Test
    void unnamedWorkoutNameAndDescriptionGainThePlaceWhenOneResolves(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        Files.write(workouts.resolve("2016-02-02 07_15_00.0.json"), ("["
                + "{\"sport\": \"RUNNING\"},"
                + "{\"source\": \"TRACK_MOBILE\"},"
                + "{\"start_time\": \"2016-02-02 07:15:00.0\"},"
                + "{\"points\": [[{\"location\": [[{\"latitude\": 50.0614}, {\"longitude\": 19.9366}]]}]]}"
                + "]").getBytes(StandardCharsets.UTF_8));
        writeTrack(workouts, "2016-02-02 07_15_00.0");

        WorkoutReportEntry entry = generatorWith(ALWAYS_VISTULA).build(archiveRoot).entries().get(0);

        assertEquals("Morning Run along Vistula in Kraków", entry.stravaName());
        assertEquals("Migrated from Endomondo (recorded 2016-02-02). Recorded along Vistula in Kraków.\n\n" + CREDIT_LINE,
                entry.stravaDescription());
    }

    @Test
    void jsonSuppliedNameIsNeverEnrichedWithAPlace(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-manual.json", "2014-09-16 09_05_21.0");

        WorkoutReportEntry entry = generatorWith(ALWAYS_VISTULA).build(archiveRoot).entries().get(0);

        assertEquals("Sample manual walk", entry.stravaName(),
                "a human already titled this; a place must not be appended");
    }

    // --- Rendered file ---

    @Test
    void generatedFileShowsActionsNamesAndDescriptions(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");
        copyFixture(workouts, "workout-manual.json", "2014-09-16 09_05_21.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile);

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("Sample tracked ride"));
        assertTrue(html.contains("Sample manual walk"));
        assertTrue(html.contains("Upload TCX"));
        assertTrue(html.contains("Create manual"));
        assertTrue(html.contains("Migrated from Endomondo"));
        assertTrue(html.contains("archive: 2011-09-10 12_58_59.0"), "the basename stays visible for cross-reference");
    }

    @Test
    void generatedFileShowsSkippedWorkoutsWithTheirReason(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile);

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("Skip"));
        assertTrue(html.contains("no paired TCX"));
    }

}
