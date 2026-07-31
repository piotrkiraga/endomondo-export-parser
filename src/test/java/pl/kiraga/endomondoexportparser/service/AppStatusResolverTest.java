package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.kiraga.endomondoexportparser.model.AppStatus;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.model.PlannedAction;

/**
 * Each stat is exercised on its own against temp-dir-backed stores: the home page has to
 * render on a fresh install where the archive, the ledger, and the location cache are all
 * independently absent.
 */
public class AppStatusResolverTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50), null);

    private MigrationLedger ledger;
    private LocationCache locationCache;

    private AppStatusResolver resolverFor(Path dir) {
        ledger = new MigrationLedger(dir.resolve("ledger.json"), FIXED);
        locationCache = new LocationCache(dir.resolve("location-cache.json"));
        return new AppStatusResolver(new ArchiveScanner(), ledger, locationCache);
    }

    private Path archiveWith(Path dir, String... basenames) throws Exception {
        Path archiveRoot = dir.resolve("archive");
        Path workouts = Files.createDirectories(archiveRoot.resolve("Workouts"));
        byte[] content = getClass().getResourceAsStream("/fixtures/workout-tracked.json").readAllBytes();
        for (String basename : basenames) {
            Files.write(workouts.resolve(basename + ".json"), content);
            Files.write(workouts.resolve(basename + ".tcx"), "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
        }
        return archiveRoot;
    }

    // --- Archive count ---

    @Test
    void archiveCountIsAbsentWhenNoArchiveIsThere(@TempDir Path dir) {
        AppStatusResolver resolver = resolverFor(dir);

        assertNull(resolver.resolve(dir.resolve("archive")).archiveWorkoutCount());
    }

    @Test
    void archiveCountIsTheNumberOfPairedWorkouts(@TempDir Path dir) throws Exception {
        AppStatusResolver resolver = resolverFor(dir);
        Path archiveRoot = archiveWith(dir, "2011-09-10 12_58_59.0", "2014-09-16 09_05_21.0");

        assertEquals(2, resolver.resolve(archiveRoot).archiveWorkoutCount());
    }

    // --- Ledger breakdown ---

    @Test
    void ledgerCountsAreZeroWhenNoMigrationHasEverRun(@TempDir Path dir) {
        AppStatusResolver resolver = resolverFor(dir);

        AppStatus status = resolver.resolve(dir.resolve("archive"));

        assertEquals(0, status.done());
        assertEquals(0, status.failed());
        assertEquals(0, status.skipped());
        assertEquals(0, status.pending());
    }

    @Test
    void ledgerCountsSplitByStatus(@TempDir Path dir) {
        AppStatusResolver resolver = resolverFor(dir);
        ledger.markPending("done-one", PlannedAction.UPLOAD_TCX);
        ledger.markDone("done-one", 42L);
        ledger.markPending("done-two", PlannedAction.UPLOAD_TCX);
        ledger.markDone("done-two", 43L);
        ledger.markPending("failed-one", PlannedAction.UPLOAD_TCX);
        ledger.markFailed("failed-one", "duplicate");
        ledger.markSkipped("skipped-one", PlannedAction.CREATE_MANUAL);
        ledger.markPending("pending-one", PlannedAction.UPLOAD_TCX);

        AppStatus status = resolver.resolve(dir.resolve("archive"));

        assertEquals(2, status.done());
        assertEquals(1, status.failed());
        assertEquals(1, status.skipped());
        assertEquals(1, status.pending());
    }

    // --- Location cache ---

    @Test
    void locationCacheSizeIsZeroWhenNothingWasEverGeocoded(@TempDir Path dir) {
        AppStatusResolver resolver = resolverFor(dir);

        assertEquals(0, resolver.resolve(dir.resolve("archive")).locationCacheSize());
    }

    @Test
    void locationCacheSizeCountsCachedCoordinates(@TempDir Path dir) {
        AppStatusResolver resolver = resolverFor(dir);
        locationCache.put(50.06143, 19.93658, VISTULA_IN_KRAKOW);
        locationCache.put(52.23172, 21.00600, VISTULA_IN_KRAKOW);

        assertEquals(2, resolver.resolve(dir.resolve("archive")).locationCacheSize());
    }

}
