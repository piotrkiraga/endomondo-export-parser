package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds archives from the fixtures used elsewhere in the migration package, so the
 * grouping/uniqueness logic is exercised the same way {@link MigrationPlannerTest} does.
 * {@link PlaceLookup} is stubbed throughout: no test in this class touches the network.
 */
public class PhotoReportGeneratorTest {

    private static final String PHOTO_1 = "resources/gfx/image/10000001/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/big.jpg";
    private static final String PHOTO_2 = "resources/gfx/image/10000002/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/big.jpg";
    private static final String PHOTO_UNMATCHED = "resources/gfx/image/99999999/cccccccccccccccccccccccccccccccc/big.jpg";

    private static final PlaceLookup NO_PLACES = (lat, lon) -> Optional.empty();
    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50));
    private static final PlaceLookup ALWAYS_VISTULA = (lat, lon) -> Optional.of(VISTULA_IN_KRAKOW);

    private final PhotoReportGenerator generator = generatorWith(NO_PLACES);

    private PhotoReportGenerator generatorWith(PlaceLookup placeLookup) {
        return new PhotoReportGenerator(new ArchiveScanner(), new EndomondoJsonParser(), new PhotoGeotagger(), placeLookup);
    }

    private void copyFixture(Path workoutsDirectory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(workoutsDirectory.resolve(basename + ".json"), content);
    }

    private void writeTrack(Path workoutsDirectory, String basename) throws Exception {
        Files.write(workoutsDirectory.resolve(basename + ".tcx"), "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
    }

    private void photoFile(Path archiveRoot, String relativeUrl) throws Exception {
        Path file = archiveRoot.resolve(relativeUrl);
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 8, 8);
        graphics.dispose();
        ImageIO.write(image, "jpg", file.toFile());
    }

    // --- Grouping and uniqueness ---

    @Test
    void referencedPhotosAreGroupedUnderTheirWorkout(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");
        writeTrack(workouts, "2011-09-10 12_58_59.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals(1, report.groups().size(), "only the picture-bearing workout gets a group");
        PhotoGroup group = report.groups().get(0);
        assertEquals("2015-04-11 11_38_17.0", group.basename());
        assertEquals("Sample ride with photos", group.name());
        assertEquals(2, group.photos().size());
        assertEquals(2, report.photoCount());
    }

    @Test
    void unmatchedPhotosAreListedSeparatelyAndNotDuplicated(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);
        photoFile(archiveRoot, PHOTO_UNMATCHED);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals(2, report.photoCount(), "the unmatched file must not appear in any group");
        assertEquals(1, report.unmatchedPhotos().size());
        assertEquals(PHOTO_UNMATCHED, report.unmatchedPhotos().get(0).toString().replace('\\', '/'));
    }

    // --- Activity links ---

    @Test
    void activityLinkIsAbsentBeforeMigration(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertTrue(report.groups().get(0).activityId().isEmpty());
    }

    @Test
    void activityLinkIsPresentAfterMigration(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"),
                Map.of("2015-04-11 11_38_17.0", "998877"));

        assertEquals("998877", report.groups().get(0).activityId().orElseThrow());
    }

    // --- Location resolution surfaced in the report ---

    @Test
    void photoWithNoLocationAnywhereIsMarkedNotGuessed(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        Files.write(workouts.resolve("2016-02-02 08_00_00.0.json"), ("["
                + "{\"name\": \"No-location workout\"},"
                + "{\"sport\": \"WALKING\"},"
                + "{\"source\": \"TRACK_MOBILE\"},"
                + "{\"pictures\": [[{\"created_date\": \"2016-02-02 08:05:00.0\"},"
                + "{\"picture\": [[{\"url\": \"" + PHOTO_1 + "\"}]]}]]}"
                + "]").getBytes(StandardCharsets.UTF_8));
        photoFile(archiveRoot, PHOTO_1);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertFalse(report.groups().get(0).photos().get(0).photo().hasLocation());
    }

    @Test
    void photoCaptionShowsTheResolvedPlaceWhenOneIsFound(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        PhotoReport report = generatorWith(ALWAYS_VISTULA).build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals(VISTULA_IN_KRAKOW, report.groups().get(0).photos().get(0).place());
    }

    // --- Display name matches the naming convention decided for Strava uploads ---

    @Test
    void displayNameIsTheJsonNameWhenPresent(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals("Sample ride with photos", report.groups().get(0).displayName());
    }

    @Test
    void jsonSuppliedNameIsNeverEnrichedWithAPlace(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        PhotoReport report = generatorWith(ALWAYS_VISTULA).build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals("Sample ride with photos", report.groups().get(0).displayName(),
                "a human already titled this; a place must not be appended");
    }

    @Test
    void displayNameFallsBackToTimeOfDayAndSportWhenUnnamed(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        Files.write(workouts.resolve("2016-02-02 07_15_00.0.json"), ("["
                + "{\"sport\": \"RUNNING\"},"
                + "{\"source\": \"TRACK_MOBILE\"},"
                + "{\"start_time\": \"2016-02-02 07:15:00.0\"},"
                + "{\"pictures\": [[{\"created_date\": \"2016-02-02 07:20:00.0\"},"
                + "{\"picture\": [[{\"url\": \"" + PHOTO_1 + "\"}]]}]]}"
                + "]").getBytes(StandardCharsets.UTF_8));
        photoFile(archiveRoot, PHOTO_1);

        PhotoReport report = generator.build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals("Morning Run", report.groups().get(0).displayName());
    }

    @Test
    void unnamedWorkoutDisplayNameGainsThePlace(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        Files.write(workouts.resolve("2016-02-02 07_15_00.0.json"), ("["
                + "{\"sport\": \"RUNNING\"},"
                + "{\"source\": \"TRACK_MOBILE\"},"
                + "{\"start_time\": \"2016-02-02 07:15:00.0\"},"
                + "{\"pictures\": [[{\"created_date\": \"2016-02-02 07:20:00.0\"},"
                + "{\"picture\": [[{\"url\": \"" + PHOTO_1 + "\"}]]}]]},"
                + "{\"points\": [[{\"location\": [[{\"latitude\": 50.0614}, {\"longitude\": 19.9366}]]}]]}"
                + "]").getBytes(StandardCharsets.UTF_8));
        photoFile(archiveRoot, PHOTO_1);

        PhotoReport report = generatorWith(ALWAYS_VISTULA).build(archiveRoot, archiveRoot.resolve("out"), Map.of());

        assertEquals("Morning Run along Vistula in Kraków", report.groups().get(0).displayName());
    }

    // --- Rendered file ---

    @Test
    void generatedFileEmbedsWorkingRelativeImagePaths(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        Path outputHtmlFile = root.resolve("data").resolve("photo-report.html");
        generator.generate(archiveRoot, outputHtmlFile, Map.of());

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("pending migration"));
        assertTrue(html.contains("Sample ride with photos"));
        assertTrue(html.contains("archive: 2015-04-11 11_38_17.0"), "the basename stays visible for cross-reference");

        int start = html.indexOf("<img src=\"") + "<img src=\"".length();
        String firstSrc = html.substring(start, html.indexOf('"', start));
        assertTrue(Files.isRegularFile(outputHtmlFile.getParent().resolve(firstSrc)),
                "the img src must resolve to a real file next to the report: " + firstSrc);
    }

    @Test
    void unmatchedPhotoGetsAWorkingThumbnailNotJustAPathListing(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);
        photoFile(archiveRoot, PHOTO_UNMATCHED);

        Path outputHtmlFile = root.resolve("data").resolve("photo-report.html");
        generator.generate(archiveRoot, outputHtmlFile, Map.of());

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("Unmatched photos"));
        assertTrue(html.contains(PHOTO_UNMATCHED), "the archive-relative path stays visible as a caption");

        int start = html.lastIndexOf("<img src=\"") + "<img src=\"".length();
        String lastSrc = html.substring(start, html.indexOf('"', start));
        assertTrue(Files.isRegularFile(outputHtmlFile.getParent().resolve(lastSrc)),
                "the unmatched photo must also be copied next to the report, not linked back into the archive: " + lastSrc);
    }

    @Test
    void generatedFileWiresUpClickToEnlarge(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        Path outputHtmlFile = root.resolve("data").resolve("photo-report.html");
        generator.generate(archiveRoot, outputHtmlFile, Map.of());

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("onclick=\"openLightbox(this.src)\""), "each thumbnail must open the lightbox");
        assertTrue(html.contains("id=\"lightbox\""), "the lightbox overlay must be present");
        assertTrue(html.contains("function openLightbox"), "the lightbox script must be embedded, not linked externally");
    }

    @Test
    void generatedFileShowsThePlaceInPhotoCaptions(@TempDir Path root) throws Exception {
        Path archiveRoot = root.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        writeTrack(workouts, "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);

        Path outputHtmlFile = root.resolve("data").resolve("photo-report.html");
        generatorWith(ALWAYS_VISTULA).generate(archiveRoot, outputHtmlFile, Map.of());

        String html = Files.readString(outputHtmlFile);
        assertTrue(html.contains("Recorded along Vistula in Kraków"));
    }

}
