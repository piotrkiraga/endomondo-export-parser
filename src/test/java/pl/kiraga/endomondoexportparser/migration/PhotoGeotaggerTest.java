package pl.kiraga.endomondoexportparser.migration;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.format.json.Location;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The archive's photos carry no EXIF at all, so the fixtures here are plain
 * ImageIO-written JPEGs — same situation: JFIF only, no APP1 segment to edit.
 */
public class PhotoGeotaggerTest {

    private static final double KRAKOW_LATITUDE = 50.061389;
    private static final double KRAKOW_LONGITUDE = 19.937222;
    private static final double TOLERANCE = 0.00001;

    private final PhotoGeotagger geotagger = new PhotoGeotagger();

    private Path plainJpeg(Path directory, String name) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();

        Path photo = directory.resolve(name);
        Files.createDirectories(directory);
        ImageIO.write(image, "jpg", photo.toFile());
        return photo;
    }

    private Location location(double latitude, double longitude) {
        Location location = new Location();
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    // --- Precedence ---

    @Test
    void pictureCoordinatesAreUsedWhenThePhotoHasNoExif(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path copy = work.resolve("out/photo.jpg");

        GeotaggedPhoto result = geotagger.handOut(source, copy,
                location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), location(1.0, 2.0), null);

        assertEquals(LocationSource.PICTURE_POINT, result.location().source());
        assertTrue(result.stampedNow());
        assertEquals(KRAKOW_LATITUDE, result.location().latitude(), TOLERANCE);
    }

    @Test
    void workoutTrackIsUsedWhenThePictureHasNoPoint(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path copy = work.resolve("out/photo.jpg");

        GeotaggedPhoto result = geotagger.handOut(source, copy, null,
                location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), null);

        assertEquals(LocationSource.WORKOUT_TRACK, result.location().source());
        assertEquals(KRAKOW_LATITUDE, result.location().latitude(), TOLERANCE);
    }

    @Test
    void existingExifOutranksBothJsonSources(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path stamped = work.resolve("out/photo.jpg");
        geotagger.handOut(source, stamped, location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), null, null);

        Optional<PhotoLocation> resolved = geotagger.resolve(stamped, location(1.0, 2.0), location(3.0, 4.0));

        assertEquals(LocationSource.EXIF, resolved.orElseThrow().source());
        assertEquals(KRAKOW_LATITUDE, resolved.orElseThrow().latitude(), TOLERANCE);
    }

    @Test
    void photoWithNoLocationAnywhereIsHandedOutUngeotagged(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path copy = work.resolve("out/photo.jpg");

        GeotaggedPhoto result = geotagger.handOut(source, copy, null, null, null);

        assertFalse(result.hasLocation());
        assertTrue(result.resolvedLocation().isEmpty());
        assertTrue(Files.isRegularFile(copy), "the photo is still handed out, just without coordinates");
    }

    // --- Writing ---

    @Test
    void latitudeAndLongitudeSurviveTheRoundTripUnswapped(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path copy = work.resolve("out/photo.jpg");

        geotagger.handOut(source, copy, location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), null, null);

        PhotoLocation readBack = geotagger.readGpsLocation(copy).orElseThrow();
        assertEquals(KRAKOW_LATITUDE, readBack.latitude(), TOLERANCE, "latitude must not be swapped with longitude");
        assertEquals(KRAKOW_LONGITUDE, readBack.longitude(), TOLERANCE);
    }

    @Test
    void createdDateBecomesTheExifTimestamp(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path copy = work.resolve("out/photo.jpg");

        geotagger.handOut(source, copy, location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), null, "2015-04-11 14:34:19.0");

        JpegImageMetadata metadata = (JpegImageMetadata) Imaging.getMetadata(copy.toFile());
        TiffField timestamp = metadata.findExifValueWithExactMatch(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
        assertEquals("2015:04:11 14:34:19", timestamp.getStringValue());
    }

    // --- Safety and idempotency ---

    @Test
    void regeneratingLeavesAnAlreadyStampedCopyUntouched(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        Path copy = work.resolve("out/photo.jpg");

        geotagger.handOut(source, copy, location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), null, null);
        byte[] afterFirstPass = Files.readAllBytes(copy);

        GeotaggedPhoto second = geotagger.handOut(source, copy, location(1.0, 2.0), location(3.0, 4.0), null);

        assertFalse(second.stampedNow(), "the second pass should read EXIF back, not rewrite it");
        assertEquals(LocationSource.EXIF, second.location().source());
        assertArrayEquals(afterFirstPass, Files.readAllBytes(copy), "the copy must be byte-identical");
    }

    @Test
    void theArchiveOriginalIsNeverModified(@TempDir Path work) throws Exception {
        Path source = plainJpeg(work.resolve("archive"), "photo.jpg");
        byte[] before = Files.readAllBytes(source);

        geotagger.handOut(source, work.resolve("out/photo.jpg"),
                location(KRAKOW_LATITUDE, KRAKOW_LONGITUDE), null, "2015-04-11 14:34:19.0");

        assertArrayEquals(before, Files.readAllBytes(source), "archive files are read-only");
        assertTrue(geotagger.readGpsLocation(source).isEmpty(), "the original still has no GPS");
    }

}
