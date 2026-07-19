package pl.kiraga.endomondoexportparser.migration;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.Location;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Writes a photo's location into a handed-out copy, because Strava's API cannot accept
 * photos and the user attaches them by hand — a geotagged file carries its own place.
 *
 * Coordinates are resolved from three sources in order: the photo's existing EXIF, the
 * picture's "point" in the workout JSON, then the owning workout's first track point.
 * Checking EXIF first also makes regeneration idempotent, since a copy stamped by an
 * earlier run is read back rather than re-derived.
 *
 * Archive originals are never modified: every write targets a copy. The archive is the
 * irreplaceable export, so it is treated as read-only.
 */
@Service
public class PhotoGeotagger {

    /** Endomondo's created_date format, e.g. "2015-04-11 14:34:19.0". */
    private static final int ENDOMONDO_DATE_LENGTH = "yyyy-MM-dd HH:mm:ss".length();

    /**
     * Copies {@code source} to {@code copy}, stamping resolved coordinates into the
     * copy's EXIF. Returns what happened, including which source won.
     */
    public GeotaggedPhoto handOut(Path source, Path copy, Location picturePoint, Location workoutFirstPoint,
                                  String createdDate) {

        Optional<PhotoLocation> alreadyStamped = readGpsLocation(copy);
        if (alreadyStamped.isPresent()) {
            return new GeotaggedPhoto(copy, alreadyStamped.get(), false);
        }

        Optional<PhotoLocation> resolved = resolve(source, picturePoint, workoutFirstPoint);

        try {
            Files.createDirectories(copy.getParent());
            if (resolved.isEmpty()) {
                Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
                return new GeotaggedPhoto(copy, null, false);
            }
            write(source, copy, resolved.get(), createdDate);
        } catch (IOException e) {
            // ImagingException extends IOException, so EXIF-writing failures land here too
            throw new UncheckedIOException("Could not write photo copy " + copy, e);
        }

        return new GeotaggedPhoto(copy, resolved.get(), true);

    }

    /**
     * Resolves coordinates by precedence. Package-private so the precedence itself can
     * be tested without writing files.
     */
    Optional<PhotoLocation> resolve(Path source, Location picturePoint, Location workoutFirstPoint) {

        Optional<PhotoLocation> fromExif = readGpsLocation(source);
        if (fromExif.isPresent()) {
            return fromExif;
        }
        Optional<PhotoLocation> fromPicture = toLocation(picturePoint, LocationSource.PICTURE_POINT);
        if (fromPicture.isPresent()) {
            return fromPicture;
        }
        return toLocation(workoutFirstPoint, LocationSource.WORKOUT_TRACK);

    }

    /** Reads EXIF GPS from a JPEG; empty when the file is absent, has no EXIF, or has no GPS. */
    Optional<PhotoLocation> readGpsLocation(Path photo) {

        if (photo == null || !Files.isRegularFile(photo)) {
            return Optional.empty();
        }

        try {
            ImageMetadata metadata = Imaging.getMetadata(photo.toFile());
            if (!(metadata instanceof JpegImageMetadata jpegMetadata)) {
                return Optional.empty();
            }
            TiffImageMetadata exif = jpegMetadata.getExif();
            if (exif == null) {
                return Optional.empty();
            }
            TiffImageMetadata.GpsInfo gps = exif.getGpsInfo();
            if (gps == null) {
                return Optional.empty();
            }
            return Optional.of(new PhotoLocation(
                    gps.getLatitudeAsDegreesNorth(), gps.getLongitudeAsDegreesEast(), LocationSource.EXIF));
        } catch (IOException e) {
            // an unreadable or non-JPEG file simply yields no location; the next source applies
            return Optional.empty();
        }

    }

    private void write(Path source, Path copy, PhotoLocation location, String createdDate) throws IOException {

        TiffOutputSet outputSet = existingOutputSet(source).orElseGet(TiffOutputSet::new);

        // Commons Imaging takes longitude first; the round-trip test pins this order.
        outputSet.setGpsInDegrees(location.longitude(), location.latitude());

        exifTimestamp(createdDate).ifPresent(timestamp -> {
            try {
                TiffOutputDirectory exif = outputSet.getOrCreateExifDirectory();
                exif.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                exif.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, timestamp);
            } catch (ImagingException e) {
                throw new IllegalStateException("Could not set EXIF timestamp on " + copy, e);
            }
        });

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(copy))) {
            new ExifRewriter().updateExifMetadataLossless(source.toFile(), out, outputSet);
        }

    }

    private Optional<TiffOutputSet> existingOutputSet(Path source) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(source.toFile());
            if (metadata instanceof JpegImageMetadata jpegMetadata && jpegMetadata.getExif() != null) {
                return Optional.ofNullable(jpegMetadata.getExif().getOutputSet());
            }
        } catch (IOException e) {
            // no readable EXIF to preserve; a fresh output set is created instead
        }
        return Optional.empty();
    }

    /** Converts "2015-04-11 14:34:19.0" to EXIF's "2015:04:11 14:34:19". */
    private Optional<String> exifTimestamp(String createdDate) {
        if (createdDate == null || createdDate.length() < ENDOMONDO_DATE_LENGTH) {
            return Optional.empty();
        }
        String trimmed = createdDate.substring(0, ENDOMONDO_DATE_LENGTH);
        return Optional.of(trimmed.substring(0, 10).replace('-', ':') + trimmed.substring(10));
    }

    private Optional<PhotoLocation> toLocation(Location location, LocationSource source) {
        if (location == null || location.getLatitude() == null || location.getLongitude() == null) {
            return Optional.empty();
        }
        return Optional.of(new PhotoLocation(location.getLatitude(), location.getLongitude(), source));
    }

}
