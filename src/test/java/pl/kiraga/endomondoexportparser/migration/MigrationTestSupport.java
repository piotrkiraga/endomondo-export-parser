package pl.kiraga.endomondoexportparser.migration;

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

/**
 * Builds a fully isolated, mocked-network rig of the migration collaborators — no test
 * touches the real filesystem's {@code data/} directory or the real Strava network —
 * for tests outside this package (controller tests) that can't reach the
 * package-private test constructors ({@code MigrationLedger(Path, Clock)},
 * {@code StravaTokenStore(Path)}, {@code StravaClient(..., Clock, LongConsumer)},
 * {@code RequestThrottle(long)}) directly. Public deliberately: a cross-package test
 * seam, not part of the application's real API surface.
 */
public final class MigrationTestSupport {

    private MigrationTestSupport() {
    }

    public record Rig(MockRestServiceServer server, MigrationExecutor executor, MigrationLedger ledger,
                       WorkoutPhotoResolver photoResolver, StravaTokenStore tokenStore) {
    }

    /** {@code dir} is a @TempDir; tokens/ledger files are written under it, never under the real data/. */
    public static Rig build(Path dir, Clock clock) {

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        StravaTokenStore tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        tokenStore.save(new StravaTokens("t", "refresh-1", clock.instant().plusSeconds(3600).getEpochSecond()));
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", clock, millis -> { });

        MigrationPlanner planner = new MigrationPlanner(new ArchiveScanner(), new EndomondoJsonParser());
        PlaceLookup noPlaces = (lat, lon) -> Optional.empty();
        WorkoutResolver resolver = new WorkoutResolver(new ArchiveScanner(), new EndomondoJsonParser(), noPlaces);
        MigrationLedger ledger = new MigrationLedger(dir.resolve("ledger.json"), clock);
        MigrationExecutor executor = new MigrationExecutor(planner, resolver, ledger, stravaClient, new RequestThrottle(0), millis -> { });
        WorkoutPhotoResolver photoResolver = new WorkoutPhotoResolver(new EndomondoJsonParser(), new PhotoGeotagger());

        return new Rig(server, executor, ledger, photoResolver, tokenStore);

    }

}
