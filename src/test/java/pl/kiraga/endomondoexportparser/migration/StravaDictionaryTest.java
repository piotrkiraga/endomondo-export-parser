package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Uses {@link MockRestServiceServer}; no test here touches the real network. */
public class StravaDictionaryTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);

    private record Rig(StravaDictionary dictionary, MockRestServiceServer server) {
    }

    private Rig rigFor(Path dir) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StravaTokenStore tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        tokenStore.save(new StravaTokens("t", "refresh-1", FIXED.instant().plusSeconds(3600).getEpochSecond()));
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", FIXED, millis -> { });
        StravaDictionaryCache cache = new StravaDictionaryCache(dir.resolve("dictionary.json"));
        return new Rig(new StravaDictionary(stravaClient, cache, FIXED), server);
    }

    @Test
    void refreshCombinesBikesAndShoesIntoOneGearNameMap(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/athlete"))
                .andRespond(withSuccess("""
                        {"id": 555, "firstname": "Piotr", "lastname": "Kiraga", "profile": "https://example.com/p.jpg",
                         "city": "Kraków", "country": "Poland",
                         "bikes": [{"id": "b18387038", "name": "Decathlon Riverside 5 Man"}],
                         "shoes": [{"id": "g1", "name": "Trail Shoes"}]}
                        """, APPLICATION_JSON));

        StravaDictionarySnapshot snapshot = rig.dictionary().refresh();

        assertEquals("Piotr", snapshot.firstname());
        assertEquals("Decathlon Riverside 5 Man", snapshot.gearNames().get("b18387038"));
        assertEquals("Trail Shoes", snapshot.gearNames().get("g1"));
        assertEquals("2026-07-27T12:00:00Z", snapshot.refreshedAt());
        rig.server().verify();
    }

    @Test
    void refreshPersistsSoItSurvivesAFreshInstance(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/athlete"))
                .andRespond(withSuccess("""
                        {"id": 555, "firstname": "Piotr", "lastname": "Kiraga", "bikes": [], "shoes": []}
                        """, APPLICATION_JSON));
        rig.dictionary().refresh();

        StravaDictionary freshInstance = new StravaDictionary(null, new StravaDictionaryCache(dir.resolve("dictionary.json")), FIXED);

        assertEquals("Piotr", freshInstance.current().orElseThrow().firstname());
    }

    @Test
    void gearNameForIsEmptyWhenNeverRefreshed(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        assertTrue(rig.dictionary().gearNameFor("b18387038").isEmpty());
    }

    @Test
    void gearNameForIsEmptyForAnUnknownGearId(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        rig.server().expect(requestTo("https://www.strava.com/api/v3/athlete"))
                .andRespond(withSuccess("""
                        {"id": 555, "bikes": [{"id": "b18387038", "name": "Decathlon Riverside 5 Man"}], "shoes": []}
                        """, APPLICATION_JSON));
        rig.dictionary().refresh();

        assertTrue(rig.dictionary().gearNameFor("b99999999").isEmpty());
        assertEquals("Decathlon Riverside 5 Man", rig.dictionary().gearNameFor("b18387038").orElseThrow());
    }

}
