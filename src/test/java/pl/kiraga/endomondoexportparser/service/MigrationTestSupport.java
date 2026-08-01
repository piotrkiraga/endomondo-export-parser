package pl.kiraga.endomondoexportparser.service;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.dto.strava.StravaTokensDto;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.util.RequestThrottleUtil;

/**
 * Builds a fully isolated, mocked-network rig of the migration collaborators — no test
 * touches the real filesystem's {@code data/} directory or the real Strava network —
 * for tests outside this package (controller tests) that can't reach the
 * package-private test constructors ({@code MigrationLedger(Path, Clock)},
 * {@code StravaTokenStore(Path)}, {@code StravaClient(..., Clock, LongConsumer)},
 * {@code RequestThrottleUtil(long)}) directly. Public deliberately: a cross-package test
 * seam, not part of the application's real API surface.
 */
public final class MigrationTestSupport {

    private MigrationTestSupport() {
    }

    public record Rig(MockRestServiceServer server, MigrationExecutor executor, MigrationLedger ledger,
                       WorkoutPhotoResolver photoResolver, StravaTokenStore tokenStore, StravaClient stravaClient,
                       ConfirmedGearResolver confirmedGearResolver, StravaDictionaryService stravaDictionary,
                       OldBikeGearResolver oldBikeGearResolver, AppStatusResolver appStatusResolver,
                       LocationCache locationCache) {
    }

    /** As {@link #build(Path, Clock, boolean)}, connected (a token is already saved). */
    public static Rig build(Path dir, Clock clock) {
        return build(dir, clock, true);
    }

    /** {@code dir} is a @TempDir; tokens/ledger files are written under it, never under the real data/. */
    public static Rig build(Path dir, Clock clock, boolean connected) {

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        StravaTokenStore tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        if (connected) {
            tokenStore.save(new StravaTokensDto("t", "refresh-1", clock.instant().plusSeconds(3600).getEpochSecond()));
        }
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", clock, millis -> { });

        MigrationPlanner planner = new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser());
        PlaceLookup noPlaces = (lat, lon) -> Optional.empty();
        WorkoutResolver resolver = new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), noPlaces);
        MigrationLedger ledger = new MigrationLedger(dir.resolve("ledger.json"), clock);
        OldBikeGearResolver oldBikeGearResolver = new OldBikeGearResolver();
        MigrationExecutor executor = new MigrationExecutor(planner, resolver, ledger, stravaClient, oldBikeGearResolver,
                new DuplicateActivityResolver(stravaClient), new RequestThrottleUtil(0), millis -> { });
        WorkoutPhotoResolver photoResolver = new WorkoutPhotoResolver(new EndomondoJsonParser(), new PhotoGeotagger());
        StravaDictionaryService stravaDictionary = new StravaDictionaryService(stravaClient,
                new StravaDictionaryCache(dir.resolve("dictionary.json")), clock);
        ConfirmedGearResolver confirmedGearResolver = new ConfirmedGearResolver(stravaClient, stravaDictionary,
                new ConfirmedGearCache(dir.resolve("confirmed-gear-cache.json")));
        LocationCache locationCache = new LocationCache(dir.resolve("location-cache.json"));
        AppStatusResolver appStatusResolver = new AppStatusResolver(new ArchiveScanner(), ledger, locationCache);

        return new Rig(server, executor, ledger, photoResolver, tokenStore, stravaClient, confirmedGearResolver,
                stravaDictionary, oldBikeGearResolver, appStatusResolver, locationCache);

    }

    /** {@link OldBikeGearResolver}'s setters are package-private; this is the cross-package seam for them. */
    public static void configureOldBike(OldBikeGearResolver resolver, String gearId, String cutoffDate) {
        resolver.setOldBikeGearId(gearId);
        resolver.setOldBikeCutoffDate(cutoffDate);
    }

}
