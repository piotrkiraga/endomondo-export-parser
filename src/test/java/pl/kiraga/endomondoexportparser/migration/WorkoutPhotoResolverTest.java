package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mirrors {@link PhotoReportGeneratorTest}'s fixtures/helpers, scoped to one workout. */
public class WorkoutPhotoResolverTest {

    private static final String PHOTO_1 = "resources/gfx/image/10000001/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/big.jpg";
    private static final String PHOTO_2 = "resources/gfx/image/10000002/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/big.jpg";

    private final WorkoutPhotoResolver resolver = new WorkoutPhotoResolver(new EndomondoJsonParser(), new PhotoGeotagger());

    private void copyFixture(Path workoutsDirectory, String fixture, String basename) throws Exception {
        byte[] content = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        Files.write(workoutsDirectory.resolve(basename + ".json"), content);
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

    @Test
    void resolvesBothPhotosAsGeotaggedCopies(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);
        Path copiesDir = archiveRoot.resolve("copies");

        List<Path> copies = resolver.resolve(archiveRoot, workouts.resolve("2015-04-11 11_38_17.0.json"), copiesDir);

        assertEquals(2, copies.size());
        for (Path copy : copies) {
            assertTrue(Files.isRegularFile(copy), "a geotagged copy must actually be written: " + copy);
        }
    }

    @Test
    void aWorkoutWithNoPicturesResolvesToNoPhotos(@TempDir Path archiveRoot) throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-tracked.json", "2011-09-10 12_58_59.0");

        List<Path> copies = resolver.resolve(archiveRoot, workouts.resolve("2011-09-10 12_58_59.0.json"),
                archiveRoot.resolve("copies"));

        assertEquals(List.of(), copies);
    }

    @Test
    void aSecondResolveReusesTheAlreadyStampedCopyRatherThanFailingOnMissingSourceReread(@TempDir Path archiveRoot)
            throws Exception {
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        copyFixture(workouts, "workout-with-pictures.json", "2015-04-11 11_38_17.0");
        photoFile(archiveRoot, PHOTO_1);
        photoFile(archiveRoot, PHOTO_2);
        Path copiesDir = archiveRoot.resolve("copies");
        Path jsonFile = workouts.resolve("2015-04-11 11_38_17.0.json");

        List<Path> first = resolver.resolve(archiveRoot, jsonFile, copiesDir);
        List<Path> second = resolver.resolve(archiveRoot, jsonFile, copiesDir);

        assertEquals(first, second);
    }

}
