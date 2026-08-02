package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.dto.strava.StravaDictionarySnapshotDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaTokensDto;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.model.WorkoutReport;
import pl.kiraga.endomondoexportparser.model.WorkoutReportEntry;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.util.RequestThrottleUtil;

/**
 * Builds archives from the fixtures used elsewhere in the migration package, the same
 * way {@link MigrationPlannerTest} does. {@link PlaceLookup} is stubbed throughout.
 * {@code generator} and {@code generatorWithOldBikeGear} are never connected to Strava
 * (an always-empty token store), so every test using them stays fully offline, matching
 * the report's planned-gear-only path; the dedicated "confirmed gear" tests below wire a
 * real, connected {@link StravaClient} against a {@link MockRestServiceServer} instead.
 */
public class WorkoutReportGeneratorTest {

    private static final Clock FIXED_NOON = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);
    private static final PlaceLookup NO_PLACES = (lat, lon) -> Optional.empty();
    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50), null);
    private static final PlaceLookup ALWAYS_VISTULA = (lat, lon) -> Optional.of(VISTULA_IN_KRAKOW);

    @TempDir
    static Path sharedTemp;

    private final WorkoutReportGenerator generator = generatorWith(NO_PLACES);

    private StravaTokenStore notConnectedTokenStore() {
        return new StravaTokenStore(sharedTemp.resolve("never-written-tokens.json"));
    }

    /** Empty and never written to: every {@code gearNameFor} lookup is a cache miss. */
    private StravaDictionaryService emptyDictionary(StravaClient stravaClient) {
        return new StravaDictionaryService(stravaClient, new StravaDictionaryCache(sharedTemp.resolve("never-written-dictionary.json")),
                FIXED_NOON);
    }

    private WorkoutReportGenerator generatorWith(PlaceLookup placeLookup) {
        StravaTokenStore tokenStore = notConnectedTokenStore();
        StravaClient stravaClient = new StravaClient(RestClient.builder(), tokenStore, "client-id", "client-secret",
                FIXED_NOON, millis -> { });
        return new WorkoutReportGenerator(
                new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser()),
                new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), placeLookup),
                new OldBikeGearResolver(), confirmedGearResolver(stravaClient, emptyDictionary(stravaClient),
                        sharedTemp.resolve("never-read-gear-cache.json")),
                tokenStore, new RequestThrottleUtil(0));
    }

    private ConfirmedGearResolver confirmedGearResolver(StravaClient stravaClient, StravaDictionaryService dictionary,
                                                         Path gearCacheFile) {
        return new ConfirmedGearResolver(stravaClient, dictionary, new ConfirmedGearCache(gearCacheFile));
    }

    private WorkoutReportGenerator generatorWithOldBikeGear(String gearId, String cutoffDate) {
        OldBikeGearResolver oldBikeGearResolver = new OldBikeGearResolver();
        oldBikeGearResolver.setOldBikeGearId(gearId);
        oldBikeGearResolver.setOldBikeCutoffDate(cutoffDate);
        StravaTokenStore tokenStore = notConnectedTokenStore();
        StravaClient stravaClient = new StravaClient(RestClient.builder(), tokenStore, "client-id", "client-secret",
                FIXED_NOON, millis -> { });
        return new WorkoutReportGenerator(
                new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser()),
                new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), NO_PLACES),
                oldBikeGearResolver, confirmedGearResolver(stravaClient, emptyDictionary(stravaClient),
                        sharedTemp.resolve("never-read-gear-cache.json")),
                tokenStore, new RequestThrottleUtil(0));
    }

    /** A generator connected to Strava via a mocked network, for the confirmed-gear tests. */
    private record ConnectedRig(WorkoutReportGenerator generator, MockRestServiceServer server,
                                 ConfirmedGearCache gearCache) {
    }

    private ConnectedRig connectedGenerator(Path tempDir) {
        return connectedGenerator(tempDir, new OldBikeGearResolver());
    }

    private ConnectedRig connectedGenerator(Path tempDir, OldBikeGearResolver oldBikeGearResolver) {
        return connectedGenerator(tempDir, oldBikeGearResolver, 0);
    }

    private ConnectedRig connectedGenerator(Path tempDir, OldBikeGearResolver oldBikeGearResolver,
                                             long throttleIntervalMillis) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StravaTokenStore tokenStore = new StravaTokenStore(tempDir.resolve("tokens.json"));
        tokenStore.save(new StravaTokensDto("t", "refresh-1", FIXED_NOON.instant().plusSeconds(3600).getEpochSecond()));
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", FIXED_NOON,
                millis -> { });
        StravaDictionaryService stravaDictionary = new StravaDictionaryService(stravaClient,
                new StravaDictionaryCache(tempDir.resolve("dictionary.json")), FIXED_NOON);
        ConfirmedGearCache gearCache = new ConfirmedGearCache(tempDir.resolve("confirmed-gear-cache.json"));
        WorkoutReportGenerator generator = new WorkoutReportGenerator(
                new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser()),
                new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), NO_PLACES),
                oldBikeGearResolver, new ConfirmedGearResolver(stravaClient, stravaDictionary, gearCache), tokenStore,
                new RequestThrottleUtil(throttleIntervalMillis));
        return new ConnectedRig(generator, server, gearCache);
    }

    private void copyFixture(Path workoutsDirectory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(workoutsDirectory.resolve(basename + ".json"), content);
    }

    private void writeTrack(Path workoutsDirectory, String basename) throws Exception {
        Files.write(workoutsDirectory.resolve(basename + ".tcx"), "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
    }

    private static String stamp(String date) {
        return "This workout (recorded on " + date + ") is migrated from Endomondo export data by "
                + "endomondo-export-parser: https://github.com/piotrkiraga/endomondo-export-parser.";
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
        assertEquals(stamp("2011-09-10 12:58:00"), entry.stravaDescription());
        assertNull(entry.reason());
        assertEquals("Workouts/2011-09-10 12_58_59.0.json, Workouts/2011-09-10 12_58_59.0.tcx", entry.sourceFiles());
    }

    @Test
    void trackedWorkoutShowsThePlannedOldBikeGearWhenOnOrBeforeTheCutoff(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        WorkoutReportEntry entry = generatorWithOldBikeGear("b18387038", "2021-01-31")
                .build(archiveRoot).entries().get(0);

        assertEquals("b18387038", entry.plannedGearId());
    }

    @Test
    void trackedWorkoutHasNoGearWhenTheOldBikeCorrectionIsUnconfigured(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        WorkoutReportEntry entry = generator.build(archiveRoot).entries().get(0);

        assertNull(entry.plannedGearId());
    }

    @Test
    void manualWorkoutGetsAManualCreateEntry(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-manual.json", "2014-09-16 09_05_21.0");

        WorkoutReportEntry entry = generator.build(archiveRoot).entries().get(0);

        assertEquals(PlannedAction.CREATE_MANUAL, entry.action());
        assertEquals("Sample manual walk", entry.stravaName());
        assertEquals("Walk", entry.stravaSportType());
        assertEquals("Workouts/2014-09-16 09_05_21.0.json", entry.sourceFiles(),
                "no paired TCX for a manual entry, so only the JSON is listed");
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
        assertEquals(stamp("2016-02-02 07:15:00"), entry.stravaDescription());
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
        assertEquals("Recorded along Vistula in Kraków.\n\n" + stamp("2016-02-02 07:15:00"),
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
        assertTrue(html.contains("is migrated from Endomondo export data"));
        assertTrue(html.contains("Source: Workouts/2011-09-10 12_58_59.0.json, Workouts/2011-09-10 12_58_59.0.tcx"),
                "the source file(s) on disk stay visible for cross-reference");
        assertTrue(html.contains("<div class=\"detail-list\">"),
                "sport/start time/distance/duration render as a detail-list, matching the in-app pages");
        assertTrue(html.contains("<span class=\"detail-label\">Sport</span>"));
    }

    @Test
    void renderedDescriptionIsTighterThanTheRealOneSentToStrava(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        WorkoutReportEntry entry = generatorWith(ALWAYS_VISTULA).build(archiveRoot).entries().get(0);
        assertTrue(entry.stravaDescription().contains("\n\n"),
                "the value a real migration would send keeps its paragraph break");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generatorWith(ALWAYS_VISTULA).generate(archiveRoot, outputHtmlFile);
        String html = Files.readString(outputHtmlFile);

        int start = html.indexOf("<div class=\"description\">") + "<div class=\"description\">".length();
        String renderedDescription = html.substring(start, html.indexOf("</div>", start));
        assertFalse(renderedDescription.contains("\n\n"), "the report's own rendering is tightened to a single line break");
        assertTrue(renderedDescription.contains("\n"), "still two sentences, just not blank-line separated");
    }

    @Test
    void alreadyMigratedWorkoutGetsAClickableStravaLinkRatherThanPendingMigration(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile, Map.of("2011-09-10 12_58_59.0", 777L));

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("https://www.strava.com/activities/777"));
        assertFalse(html.contains("pending migration"));
    }

    @Test
    void notYetMigratedWorkoutShowsPendingMigrationRatherThanALink(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile);

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("pending migration"));
    }

    @Test
    void migratedWorkoutCardCarriesTheMigratedClassAndCheckmark(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile, Map.of("2011-09-10 12_58_59.0", 777L));

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("<section class=\"workout migrated\">"), "the card must carry the migrated accent class");
        assertTrue(html.contains("<span class=\"status-icon\">✓</span> <a href=\"https://www.strava.com/activities/777\">view on Strava</a>"),
                "the checkmark must precede the existing Strava link");
        assertTrue(html.contains(".workout.migrated{"), "the accent must be styled inline, so the file still works offline");
    }

    @Test
    void notYetMigratedWorkoutCardKeepsTheDefaultLookAndGetsTheOpenCircle(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile);

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("<section class=\"workout\">"));
        assertFalse(html.contains("class=\"workout migrated\""), "no activity id means no migrated accent");
        assertTrue(html.contains("<span class=\"status-icon\">○</span> pending migration"));
    }

    @Test
    void themeReadScriptRunsInHeadBeforeBodyForNoFlashOfWrongMode(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        Path outputHtmlFile = root.resolve("data").resolve("workout-report.html");
        generator.generate(archiveRoot, outputHtmlFile);

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("localStorage.getItem('theme')"), "must read the app's explicit theme choice");
        assertTrue(html.contains("setAttribute('data-theme',t)"), "must apply the choice via data-theme");
        int scriptIndex = html.indexOf("localStorage.getItem('theme')");
        int headEnd = html.indexOf("</head>");
        int bodyStart = html.indexOf("<body>");
        assertTrue(scriptIndex > 0 && scriptIndex < headEnd, "the theme script must run inside <head>, before it closes");
        assertTrue(headEnd < bodyStart, "sanity check: head must close before body opens");
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

    // --- Confirmed gear (already-migrated workouts, connected to Strava) ---

    @Test
    void migratedWorkoutShowsConfirmedGearRatherThanPlannedWhenConnected(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777, \"gear_id\": \"b18387038\"}", APPLICATION_JSON));
        rig.server().expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

        WorkoutReportEntry entry = rig.generator()
                .build(archiveRoot, Map.of("2011-09-10 12_58_59.0", 777L)).entries().get(0);

        assertEquals("Trek Checkpoint (b18387038)", entry.confirmedGearDisplay());
        rig.server().verify();
    }

    @Test
    void migratedWorkoutConfirmsNoGearRatherThanFallingBackToPlanned(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777}", APPLICATION_JSON));

        WorkoutReportEntry entry = rig.generator()
                .build(archiveRoot, Map.of("2011-09-10 12_58_59.0", 777L)).entries().get(0);

        assertEquals("", entry.confirmedGearDisplay(), "confirmed empty, not null — a real answer, not a fallback case");
        rig.server().verify();
    }

    @Test
    void migratedWorkoutFallsBackToPlannedGearWhenTheLiveReadFails(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withUnauthorizedRequest());

        WorkoutReportEntry entry = rig.generator()
                .build(archiveRoot, Map.of("2011-09-10 12_58_59.0", 777L)).entries().get(0);

        assertNull(entry.confirmedGearDisplay());
        rig.server().verify();
    }

    @Test
    void notMigratedWorkoutNeverAttemptsALiveLookupEvenWhenConnected(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root);
        // No server.expect(...) at all: no activityId means nothing to look up.

        WorkoutReportEntry entry = rig.generator().build(archiveRoot).entries().get(0);

        assertNull(entry.confirmedGearDisplay());
        rig.server().verify();
    }

    @Test
    void plannedGearShowsItsNameTooWhenConnectedEvenThoughNotYetMigrated(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        OldBikeGearResolver oldBikeGearResolver = new OldBikeGearResolver();
        oldBikeGearResolver.setOldBikeGearId("b18387038");
        oldBikeGearResolver.setOldBikeCutoffDate("2021-01-31");
        ConnectedRig rig = connectedGenerator(root, oldBikeGearResolver);
        // No /activities expectation: this workout isn't migrated, so there is nothing to
        // confirm — only the planned gear's own name is looked up, once.
        rig.server().expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

        WorkoutReportEntry entry = rig.generator().build(archiveRoot).entries().get(0);

        assertEquals("b18387038", entry.plannedGearId());
        assertEquals("Trek Checkpoint (b18387038)", entry.plannedGearDisplay());
        assertNull(entry.confirmedGearDisplay());
        rig.server().verify();
    }

    @Test
    void plannedGearNameIsResolvedOnceAndReusedAcrossEntries(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");
        copyFixture(workouts, "workout-tracked.json", "2011-09-11 12_58_59.0");
        writeTrack(workouts, "2011-09-11 12_58_59.0");

        OldBikeGearResolver oldBikeGearResolver = new OldBikeGearResolver();
        oldBikeGearResolver.setOldBikeGearId("b18387038");
        oldBikeGearResolver.setOldBikeCutoffDate("2021-01-31");
        ConnectedRig rig = connectedGenerator(root, oldBikeGearResolver);
        // Exactly one /gear expectation: a second call would fail server.verify() below.
        rig.server().expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

        WorkoutReport report = rig.generator().build(archiveRoot);

        assertEquals(2, report.entries().size());
        report.entries().forEach(entry -> assertEquals("Trek Checkpoint (b18387038)", entry.plannedGearDisplay()));
        rig.server().verify();
    }

    // --- Confirmed gear served from ConfirmedGearCache ---

    /** The dictionary supplies the gear's name, so a cache hit needs no network call at all. */
    private void saveGearName(Path tempDir, String gearId, String name) {
        new StravaDictionaryCache(tempDir.resolve("dictionary.json")).save(new StravaDictionarySnapshotDto(
                555L, "Piotr", "Kiraga", null, null, null, null, Map.of(gearId, name), "2026-07-27T12:00:00Z"));
    }

    @Test
    void alreadyConfirmedGearIsShownWithoutAnyStravaCall(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root);
        rig.gearCache().put(777L, "b18387038");
        saveGearName(root, "b18387038", "Trek Checkpoint");
        // No server.expect(...) at all: gear confirmed on an earlier run must not be re-read.

        WorkoutReportEntry entry = rig.generator()
                .build(archiveRoot, Map.of("2011-09-10 12_58_59.0", 777L)).entries().get(0);

        assertEquals("Trek Checkpoint (b18387038)", entry.confirmedGearDisplay());
        rig.server().verify();
    }

    @Test
    void regeneratingTheReportReusesTheGearConfirmedByTheFirstGeneration(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root);
        saveGearName(root, "b18387038", "Trek Checkpoint");
        // Exactly one /activities expectation: the second generation reading it again would fail verify().
        rig.server().expect(requestTo("https://www.strava.com/api/v3/activities/777"))
                .andRespond(withSuccess("{\"id\": 777, \"gear_id\": \"b18387038\"}", APPLICATION_JSON));
        Map<String, Long> activityIds = Map.of("2011-09-10 12_58_59.0", 777L);
        rig.generator().build(archiveRoot, activityIds);

        WorkoutReportEntry entry = rig.generator().build(archiveRoot, activityIds).entries().get(0);

        assertEquals("Trek Checkpoint (b18387038)", entry.confirmedGearDisplay());
        rig.server().verify();
    }

    @Test
    void aCachedConfirmedGearSkipsTheThrottleWaitToo(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");
        copyFixture(workouts, "workout-tracked.json", "2011-09-11 12_58_59.0");
        writeTrack(workouts, "2011-09-11 12_58_59.0");

        ConnectedRig rig = connectedGenerator(root, new OldBikeGearResolver(), 5000);
        rig.gearCache().put(777L, "");
        rig.gearCache().put(778L, "");

        long startedAt = System.currentTimeMillis();
        WorkoutReport report = rig.generator().build(archiveRoot,
                Map.of("2011-09-10 12_58_59.0", 777L, "2011-09-11 12_58_59.0", 778L));
        long elapsedMillis = System.currentTimeMillis() - startedAt;

        report.entries().forEach(entry -> assertEquals("", entry.confirmedGearDisplay()));
        assertTrue(elapsedMillis < 2000,
                "a cache hit makes no call, so it must not pay the throttle's pacing wait; took " + elapsedMillis + "ms");
        rig.server().verify();
    }

}
