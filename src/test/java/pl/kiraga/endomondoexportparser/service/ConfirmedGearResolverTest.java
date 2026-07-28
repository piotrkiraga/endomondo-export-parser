package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.dto.strava.StravaDictionarySnapshotDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaTokensDto;

/** Uses {@link MockRestServiceServer}; no test here touches the real network. */
public class ConfirmedGearResolverTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);

    private record Rig(ConfirmedGearResolver resolver, MockRestServiceServer server) {
    }

    private Rig rigFor(Path dir) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StravaTokenStore tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        tokenStore.save(new StravaTokensDto("t", "refresh-1", FIXED.instant().plusSeconds(3600).getEpochSecond()));
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", FIXED, millis -> { });
        StravaDictionaryCache cache = new StravaDictionaryCache(dir.resolve("dictionary.json"));
        StravaDictionaryService dictionary = new StravaDictionaryService(stravaClient, cache, FIXED);
        return new Rig(new ConfirmedGearResolver(stravaClient, dictionary), server);
    }

    @Test
    void displayUsesTheCachedDictionaryNameWithoutAnyNetworkCall(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        new StravaDictionaryCache(dir.resolve("dictionary.json")).save(new StravaDictionarySnapshotDto(
                555L, "Piotr", "Kiraga", null, null, null, null,
                Map.of("b18387038", "Decathlon Riverside 5 Man"), "2026-07-27T12:00:00Z"));
        // No server.expect(...) at all: a cache hit must not call Strava.

        String display = rig.resolver().display("b18387038");

        assertEquals("Decathlon Riverside 5 Man (b18387038)", display);
        rig.server().verify();
    }

    @Test
    void displayFallsBackToALiveLookupWhenTheGearIdIsNotInTheCache(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/gear/b18305600"))
                .andRespond(withSuccess("{\"id\": \"b18305600\", \"name\": \"Trek Cross Dual Sport 3\"}", APPLICATION_JSON));

        String display = rig.resolver().display("b18305600");

        assertEquals("Trek Cross Dual Sport 3 (b18305600)", display);
        rig.server().verify();
    }

    @Test
    void displayFallsBackToTheBareIdWhenNeitherCachedNorLiveLookupWorks(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/gear/unknown"))
                .andRespond(withStatus(NOT_FOUND));

        String display = rig.resolver().display("unknown");

        assertEquals("unknown", display);
    }

}
