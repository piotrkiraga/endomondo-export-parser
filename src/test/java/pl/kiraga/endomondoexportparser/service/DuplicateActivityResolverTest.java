package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.dto.strava.StravaTokensDto;

/** Uses {@link MockRestServiceServer}; no test here touches the real network. */
public class DuplicateActivityResolverTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    /** The archive's own start time for the tracked sample workout, 34.04 km long. */
    private static final Instant START = Instant.parse("2011-09-10T12:58:00Z");
    private static final Double DISTANCE_KM = 34.04;

    /** The 2-minute window around START, as epoch seconds. */
    private static final String WINDOW_QUERY = "?after=1315659360&before=1315659600&per_page=30";

    private record Rig(DuplicateActivityResolver resolver, MockRestServiceServer server) {
    }

    private Rig rigFor(Path dir) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StravaTokenStore tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        tokenStore.save(new StravaTokensDto("t", "refresh-1", FIXED.instant().plusSeconds(3600).getEpochSecond()));
        StravaClient stravaClient = new StravaClient(builder, tokenStore, "client-id", "client-secret", FIXED,
                millis -> { });
        return new Rig(new DuplicateActivityResolver(stravaClient), server);
    }

    private void respondWith(Rig rig, String activitiesJson) {
        rig.server().expect(requestTo("https://www.strava.com/api/v3/athlete/activities" + WINDOW_QUERY))
                .andExpect(method(GET))
                .andRespond(withSuccess(activitiesJson, APPLICATION_JSON));
    }

    @Test
    void theSingleActivityMatchingStartTimeAndDistanceIsTheDuplicate(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:58:00Z", "distance": 34040.0}]
                """);

        assertEquals(1234567890L, rig.resolver().findExistingActivity(START, DISTANCE_KM).orElseThrow());
        rig.server().verify();
    }

    @Test
    void noActivityWithThatExactStartTimeIsNotAMatch(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:59:12Z", "distance": 34040.0}]
                """);

        assertTrue(rig.resolver().findExistingActivity(START, DISTANCE_KM).isEmpty());
        rig.server().verify();
    }

    @Test
    void twoActivitiesSharingTheStartTimeAreAmbiguousAndNeverGuessed(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:58:00Z", "distance": 34040.0},
                 {"id": 1234567891, "start_date": "2011-09-10T12:58:00Z", "distance": 34040.0}]
                """);

        assertTrue(rig.resolver().findExistingActivity(START, DISTANCE_KM).isEmpty());
    }

    @Test
    void aStartTimeMatchWhoseDistanceIsTooFarOffIsRejected(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:58:00Z", "distance": 21870.0}]
                """);

        assertTrue(rig.resolver().findExistingActivity(START, DISTANCE_KM).isEmpty());
    }

    @Test
    void aSmallDistanceDifferenceStillCountsAsTheSameActivity(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:58:00Z", "distance": 34018.0}]
                """);

        assertEquals(1234567890L, rig.resolver().findExistingActivity(START, DISTANCE_KM).orElseThrow());
    }

    @Test
    void aStartTimeMatchStandsAloneWhenTheWorkoutHasNoKnownDistance(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:58:00Z", "distance": 34040.0}]
                """);

        assertEquals(1234567890L, rig.resolver().findExistingActivity(START, null).orElseThrow());
    }

    @Test
    void aStartTimeMatchStandsAloneWhenStravaReportsNoDistance(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, """
                [{"id": 1234567890, "start_date": "2011-09-10T12:58:00Z"}]
                """);

        assertEquals(1234567890L, rig.resolver().findExistingActivity(START, DISTANCE_KM).orElseThrow());
    }

    @Test
    void anEmptyActivityListIsNoMatch(@TempDir Path dir) {
        Rig rig = rigFor(dir);
        respondWith(rig, "[]");

        assertTrue(rig.resolver().findExistingActivity(START, DISTANCE_KM).isEmpty());
        rig.server().verify();
    }

}
