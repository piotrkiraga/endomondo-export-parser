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

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds archives from the fixtures used elsewhere in the migration package, so the
 * grouping/uniqueness logic is exercised the same way {@link MigrationPlannerTest} does.
 */
public class PhotoReportGeneratorTest {

    private static final String PHOTO_1 = "resources/gfx/image/10000001/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/big.jpg";
    private static final String PHOTO_2 = "resources/gfx/image/10000002/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/big.jpg";
    private static final String PHOTO_UNMATCHED = "resources/gfx/image/99999999/cccccccccccccccccccccccccccccccc/big.jpg";

    private final PhotoReportGenerator generator =
            new PhotoReportGenerator(new ArchiveScanner(), new EndomondoJsonParser(), new PhotoGeotagger());

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

        assertFalse(report.groups().get(0).photos().get(0).hasLocation());
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

        int start = html.indexOf("<img src=\"") + "<img src=\"".length();
        String firstSrc = html.substring(start, html.indexOf('"', start));
        assertTrue(Files.isRegularFile(outputHtmlFile.getParent().resolve(firstSrc)),
                "the img src must resolve to a real file next to the report: " + firstSrc);
    }

}
