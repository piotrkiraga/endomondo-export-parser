package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.dto.endomondo.PictureDto;
import pl.kiraga.endomondoexportparser.model.GeotaggedPhoto;
import pl.kiraga.endomondoexportparser.model.LocationSource;
import pl.kiraga.endomondoexportparser.model.MigrationPlan;
import pl.kiraga.endomondoexportparser.model.PhotoLocation;
import pl.kiraga.endomondoexportparser.model.PlannedAction;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.util.SportMappingUtil;

/**
 * Dry run over the real Endomondo archive, pinning the numbers the migration is
 * expected to produce. The archive is personal data and git-ignored, so this test
 * skips wherever it is absent (CI, a fresh clone) rather than failing.
 */
public class RealArchiveDryRunTest {

    private static final Path WORKOUTS =
            Path.of("data", "endomondo-strava-exports", "endomondo-2020-11-01", "Workouts");

    private final MigrationPlanner planner = new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser());

    @Test
    void realArchivePlansEveryWorkoutWithNothingSkipped() {
        assumeTrue(Files.isDirectory(WORKOUTS), "archive not present; skipping real-archive dry run");

        MigrationPlan plan = planner.plan(WORKOUTS);

        assertEquals(162, plan.workouts().size(), "archived workouts");
        assertEquals(137, plan.count(PlannedAction.UPLOAD_TCX), "TCX uploads (94 mobile + 42 gear + 1 imported GPX)");
        assertEquals(25, plan.count(PlannedAction.CREATE_MANUAL), "manual activities");
        assertEquals(0, plan.count(PlannedAction.SKIP), "no workout should be skipped: " + plan.skipped());
        assertTrue(plan.tracksWithoutMetadata().isEmpty(), "every TCX should have a JSON");
    }

    /**
     * The archive's JPEGs have no EXIF segment at all, so this proves Commons Imaging
     * can create one on the real files rather than only on test-generated images.
     */
    @Test
    void realArchivePhotoIsGeotaggedFromItsPictureCoordinates(@TempDir Path handout) throws Exception {
        assumeTrue(Files.isDirectory(WORKOUTS), "archive not present; skipping real-archive geotagging");

        Path archiveRoot = WORKOUTS.getParent();
        EndomondoJsonParser parser = new EndomondoJsonParser();
        PhotoGeotagger geotagger = new PhotoGeotagger();

        PictureDto located = null;
        try (Stream<Path> files = Files.list(WORKOUTS)) {
            for (Path json : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                Optional<PictureDto> withPoint = parser.parse(Files.readAllBytes(json)).getPictures().stream()
                        .filter(picture -> picture.getPoint() != null && picture.getUrl() != null)
                        .findFirst();
                if (withPoint.isPresent()) {
                    located = withPoint.get();
                    break;
                }
            }
        }
        assumeTrue(located != null, "no picture with coordinates found in the archive");

        Path original = archiveRoot.resolve(located.getUrl());
        assumeTrue(Files.isRegularFile(original), "referenced photo missing from archive: " + original);

        assertTrue(geotagger.readGpsLocation(original).isEmpty(), "archive photos should carry no EXIF GPS");

        byte[] before = Files.readAllBytes(original);
        GeotaggedPhoto result = geotagger.handOut(original, handout.resolve("photo.jpg"),
                located.getPoint(), null, located.getCreated_date());

        assertEquals(LocationSource.PICTURE_POINT, result.location().source());
        assertEquals(located.getPoint().getLatitude(), result.location().latitude(), 0.00001);

        PhotoLocation readBack = geotagger.readGpsLocation(result.copy()).orElseThrow();
        assertEquals(located.getPoint().getLatitude(), readBack.latitude(), 0.00001);
        assertEquals(located.getPoint().getLongitude(), readBack.longitude(), 0.00001);

        assertArrayEquals(before, Files.readAllBytes(original), "the archive original must be untouched");
    }

    @Test
    void realArchivePicturesAndSportsMatchTheSurvey() {
        assumeTrue(Files.isDirectory(WORKOUTS), "archive not present; skipping real-archive dry run");

        MigrationPlan plan = planner.plan(WORKOUTS);

        assertEquals(80, plan.totalPictures(), "pictures referenced by workouts");
        assertEquals(29, plan.workoutsWithPictures(), "picture-bearing workouts");
        assertEquals(SportMappingUtil.ARCHIVE_SPORTS, plan.sportCounts(), "sport distribution");
    }

}
