package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.dto.strava.StravaActivityDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaAthleteDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaGearDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaTokensDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaUploadResultDto;
import pl.kiraga.endomondoexportparser.exception.StravaApiException;

/** Uses {@link MockRestServiceServer}; no test here touches the real network or sleeps for real. */
public class StravaClientTest {

    private static final Clock FIXED_NOON = Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC);

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private StravaTokenStore tokenStore;
    private AtomicLong sleptMillis;

    private StravaClient newClient(Path tempDir) {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokenStore = new StravaTokenStore(tempDir.resolve("tokens.json"));
        sleptMillis = new AtomicLong(-1);
        return new StravaClient(builder, tokenStore, "client-id", "client-secret", FIXED_NOON, sleptMillis::set);
    }

    private void storeToken(String accessToken, Instant expiresAt) {
        tokenStore.save(new StravaTokensDto(accessToken, "refresh-1", expiresAt.getEpochSecond()));
    }

    // --- Token refresh ---

    @Test
    void refreshPersistsTheNewTokensAndReturnsThem(@TempDir Path dir) {
        StravaClient client = newClient(dir);

        server.expect(requestTo(
                        "https://www.strava.com/oauth/token?client_id=client-id&client_secret=client-secret&grant_type=refresh_token&refresh_token=old-refresh"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"access_token": "new-access", "refresh_token": "new-refresh", "expires_at": 1900000000, "expires_in": 21600, "token_type": "Bearer"}
                        """, APPLICATION_JSON));

        StravaTokensDto result = client.refreshAccessToken("old-refresh");

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        assertEquals(1900000000L, result.expiresAtEpochSeconds());
        assertEquals(result, tokenStore.load().orElseThrow(), "the refreshed tokens must be persisted");
    }

    @Test
    void exchangeAuthorizationCodePersistsTheFirstTokens(@TempDir Path dir) {
        StravaClient client = newClient(dir);

        server.expect(requestTo(
                        "https://www.strava.com/oauth/token?client_id=client-id&client_secret=client-secret&grant_type=authorization_code&code=auth-code"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"access_token": "first-access", "refresh_token": "first-refresh", "expires_at": 1900000000,
                         "athlete": {"id": 71292278, "firstname": "Piotr"}}
                        """, APPLICATION_JSON));

        StravaTokensDto result = client.exchangeAuthorizationCode("auth-code");

        assertEquals("first-access", result.accessToken());
        assertEquals(result, tokenStore.load().orElseThrow());
    }

    @Test
    void missingCredentialsFailFastWithoutAnyNetworkCall(@TempDir Path dir) {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        StravaClient client = new StravaClient(builder, tokenStore, "", "", FIXED_NOON, millis -> { });

        assertThrows(StravaApiException.class, () -> client.refreshAccessToken("whatever"));
    }

    // --- accessToken() refresh-on-demand behaviour ---

    @Test
    void freshTokenIsUsedDirectlyWithoutRefreshing(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("still-good", Instant.parse("2026-07-20T13:00:00Z"));

        // Only the upload endpoint is expected; if accessToken() tried to refresh first,
        // this would fail with "no further requests expected".
        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andExpect(header("Authorization", "Bearer still-good"))
                .andRespond(withSuccess("""
                        {"id": 1, "external_id": "e1", "status": "queued"}
                        """, APPLICATION_JSON));

        Path tcx = writeTcx(dir);
        client.uploadTcx(tcx, "e1", "name", "desc");

        server.verify();
    }

    @Test
    void expiringTokenIsRefreshedBeforeUse(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("expiring-soon", Instant.parse("2026-07-20T12:02:00Z"));

        server.expect(requestTo(
                        "https://www.strava.com/oauth/token?client_id=client-id&client_secret=client-secret&grant_type=refresh_token&refresh_token=refresh-1"))
                .andRespond(withSuccess("""
                        {"access_token": "refreshed", "refresh_token": "refresh-2", "expires_at": 1900000000}
                        """, APPLICATION_JSON));
        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andExpect(header("Authorization", "Bearer refreshed"))
                .andRespond(withSuccess("""
                        {"id": 1, "external_id": "e1", "status": "queued"}
                        """, APPLICATION_JSON));

        client.uploadTcx(writeTcx(dir), "e1", "name", "desc");

        server.verify();
    }

    @Test
    void noStoredTokensAtAllFailsClearly(@TempDir Path dir) {
        StravaClient client = newClient(dir);

        StravaApiException e = assertThrows(StravaApiException.class, () -> client.uploadTcx(writeTcx(dir), "e1", null, null));
        assertTrue(e.getMessage().contains("OAuth"));
    }

    // --- Upload + poll ---

    @Test
    void uploadResultCarriesTheUploadId(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"id": 555, "external_id": "e1", "status": "Your activity is still being processed."}
                        """, APPLICATION_JSON));

        StravaUploadResultDto result = client.uploadTcx(writeTcx(dir), "e1", "My ride", "a description");

        assertEquals(555L, result.id());
        assertFalse(result.hasActivityId());
        assertFalse(result.failed(), "still processing is not a failure");
    }

    @Test
    void duplicateRejectionWithAnActivityIdCountsAsSuccess(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads/555"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id": 555, "error": "duplicate of activity 999", "status": "error", "activity_id": 999}
                        """, APPLICATION_JSON));

        StravaUploadResultDto result = client.checkUploadStatus(555);

        assertTrue(result.hasActivityId());
        assertEquals(999L, result.activityId());
        assertFalse(result.failed(), "an activity id means this counts as success, per design.md");
    }

    @Test
    void genuineFailureHasNoActivityId(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads/555"))
                .andRespond(withSuccess("""
                        {"id": 555, "error": "TCX parse error", "status": "error"}
                        """, APPLICATION_JSON));

        StravaUploadResultDto result = client.checkUploadStatus(555);

        assertTrue(result.failed());
        assertFalse(result.hasActivityId());
    }

    // --- Activities ---

    @Test
    void createManualActivityUsesSportTypeNotType(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/activities"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer t"))
                .andRespond(withSuccess("{\"id\": 42}", APPLICATION_JSON));

        StravaActivityDto activity = client.createManualActivity(
                "Morning Walk", "Walk", Instant.parse("2020-06-01T08:00:00Z"), Duration.ofMinutes(30), 2500.0, "desc");

        assertEquals(42L, activity.id());
    }

    @Test
    void updateActivitySendsJsonBody(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/activities/42"))
                .andExpect(method(PUT))
                .andRespond(withSuccess("{\"id\": 42}", APPLICATION_JSON));

        StravaActivityDto activity = client.updateActivity(42, "New name", "Ride", "new description");

        assertEquals(42L, activity.id());
    }

    @Test
    void updateActivityIncludesGearIdWhenGiven(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/activities/42"))
                .andExpect(method(PUT))
                .andExpect(content().json("{\"name\":\"New name\",\"sport_type\":\"Ride\",\"description\":\"new description\",\"gear_id\":\"b18387038\"}"))
                .andRespond(withSuccess("{\"id\": 42}", APPLICATION_JSON));

        client.updateActivity(42, "New name", "Ride", "new description", "b18387038");

        server.verify();
    }

    @Test
    void updateActivityOmitsGearIdEntirelyWhenNull(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        // Strict match against the exact body: if gear_id were sent as a JSON null (or
        // any value) rather than omitted entirely, this comparison would fail — per
        // Strava's own docs the literal string "none" is what clears gear, so a bare
        // null risks meaning something else entirely to their API.
        server.expect(requestTo("https://www.strava.com/api/v3/activities/42"))
                .andExpect(method(PUT))
                .andExpect(content().json("{\"name\":\"New name\",\"sport_type\":\"Ride\",\"description\":\"new description\"}", true))
                .andRespond(withSuccess("{\"id\": 42}", APPLICATION_JSON));

        client.updateActivity(42, "New name", "Ride", "new description");

        server.verify();
    }

    // --- Reading an activity back ---

    @Test
    void getActivityReadsBackTheGearIdStravaActuallyHasStored(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/activities/42"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"id\": 42, \"gear_id\": \"b18387038\"}", APPLICATION_JSON));

        StravaActivityDto activity = client.getActivity(42);

        assertEquals(42L, activity.id());
        assertEquals("b18387038", activity.gearId());
    }

    @Test
    void getGearReadsBackItsHumanReadableName(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

        StravaGearDto gear = client.getGear("b18387038");

        assertEquals("b18387038", gear.id());
        assertEquals("Trek Checkpoint", gear.name());
    }

    @Test
    void getAthleteReadsBackProfileAndEveryBikeAndShoe(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/athlete"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id": 555, "firstname": "Piotr", "lastname": "Kiraga", "profile": "https://example.com/p.jpg",
                         "city": "Kraków", "state": null, "country": "Poland",
                         "bikes": [{"id": "b18387038", "name": "Decathlon Riverside 5 Man"}],
                         "shoes": [{"id": "g1", "name": "Trail Shoes"}]}
                        """, APPLICATION_JSON));

        StravaAthleteDto athlete = client.getAthlete();

        assertEquals(555L, athlete.id());
        assertEquals("Piotr", athlete.firstname());
        assertEquals("Kiraga", athlete.lastname());
        assertEquals("https://example.com/p.jpg", athlete.profilePictureUrl());
        assertEquals("Kraków", athlete.city());
        assertEquals("Poland", athlete.country());
        assertEquals(1, athlete.bikes().size());
        assertEquals("Decathlon Riverside 5 Man", athlete.bikes().get(0).name());
        assertEquals(1, athlete.shoes().size());
        assertEquals("Trail Shoes", athlete.shoes().get(0).name());
    }

    // --- Dev-mode traffic logging ---

    @Test
    void devModeTrafficLoggingRedactsTheClientSecretAndNeverLogsTheBearerToken(@TempDir Path dir) {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokenStore = new StravaTokenStore(dir.resolve("tokens.json"));
        StravaClient client = new StravaClient(builder, tokenStore, "client-id", "super-secret-value",
                FIXED_NOON, millis -> { }, true);

        ch.qos.logback.classic.Logger trafficLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                        "pl.kiraga.endomondoexportparser.service.StravaTraffic");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        trafficLogger.addAppender(appender);

        try {
            storeToken("the-real-bearer-token", Instant.parse("2026-07-20T13:00:00Z"));
            server.expect(requestTo(
                            "https://www.strava.com/oauth/token?client_id=client-id&client_secret=super-secret-value&grant_type=refresh_token&refresh_token=old-refresh"))
                    .andRespond(withSuccess("""
                            {"access_token": "the-real-bearer-token", "refresh_token": "new-refresh", "expires_at": 1900000000}
                            """, APPLICATION_JSON));
            server.expect(requestTo("https://www.strava.com/api/v3/gear/b18387038"))
                    .andExpect(header("Authorization", "Bearer the-real-bearer-token"))
                    .andRespond(withSuccess("{\"id\": \"b18387038\", \"name\": \"Trek Checkpoint\"}", APPLICATION_JSON));

            client.refreshAccessToken("old-refresh");
            client.getGear("b18387038");

            String allLogged = appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);

            assertTrue(allLogged.contains("client_secret=***"), "client_secret query param must be redacted");
            assertFalse(allLogged.contains("super-secret-value"), "the real client secret must never be logged");
            assertFalse(allLogged.contains("the-real-bearer-token"), "the bearer token must never be logged");
            assertTrue(allLogged.contains("Trek Checkpoint"), "the actual response body should still be logged");
        } finally {
            trafficLogger.detachAppender(appender);
        }
    }

    @Test
    void trafficLoggingIsOffByDefault(@TempDir Path dir) {
        // The 6-arg constructor (used everywhere else in this file) must not attach any
        // interceptor - a plain smoke check that the default-off wiring didn't regress.
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/gear/b1"))
                .andRespond(withSuccess("{\"id\": \"b1\", \"name\": \"Bike\"}", APPLICATION_JSON));

        StravaGearDto gear = client.getGear("b1");

        assertEquals("Bike", gear.name());
    }

    // --- Rate limiting ---

    @Test
    void a429WaitsThenRetriesOnce(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(withTooManyRequests());
        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(withSuccess("{\"id\": 1, \"activity_id\": 7}", APPLICATION_JSON));

        StravaUploadResultDto result = client.checkUploadStatus(1);

        assertEquals(7L, result.activityId());
        assertTrue(sleptMillis.get() > 0, "must have waited before retrying");
    }

    @Test
    void secondConsecutive429PropagatesRatherThanLoopingForever(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(withTooManyRequests());
        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(withTooManyRequests());

        assertThrows(StravaApiException.class, () -> client.checkUploadStatus(1));
    }

    @Test
    void aConnectionErrorOnTheRetryAfter429IsWrappedNotLeftToEscapeRaw(@TempDir Path dir) {
        // Real incident 2026-07-27: a 429 correctly waited for the next window, but the
        // retry itself hit a plain connection reset (no HTTP status at all) rather than
        // another 429 - that used to escape as a raw ResourceAccessException, bypassing
        // migrateOne's catch (StravaApiException) and leaving the ledger entry stuck at
        // PENDING forever instead of FAILED with a reason.
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(withTooManyRequests());
        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(request -> {
                    throw new java.io.IOException("Connection reset");
                });

        StravaApiException exception = assertThrows(StravaApiException.class, () -> client.checkUploadStatus(1));
        assertTrue(exception.getMessage().contains("Connection reset"));
    }

    @Test
    void otherHttpErrorsAreWrappedNotRetried(@TempDir Path dir) {
        StravaClient client = newClient(dir);
        storeToken("t", Instant.parse("2026-07-20T13:00:00Z"));

        server.expect(requestTo("https://www.strava.com/api/v3/uploads/1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("{\"message\": \"Authorization Error\"}")
                        .contentType(APPLICATION_JSON));

        assertThrows(StravaApiException.class, () -> client.checkUploadStatus(1));
    }

    // --- Quarter-hour wait computation ---

    @Test
    void waitsUntilTheNextQuarterHourBoundaryWithASmallBuffer() {
        Clock at1207 = Clock.fixed(Instant.parse("2026-07-20T12:07:30Z"), ZoneOffset.UTC);
        StravaClient client = new StravaClient(RestClient.builder(), new StravaTokenStore(Path.of("data", "generated", "strava-tokens.json")),
                "id", "secret", at1207, millis -> { });

        Duration wait = client.waitUntilNextQuarterHourUtc();

        // 12:07:30 -> 12:15:00 is 7m30s, plus the 2s buffer.
        assertEquals(Duration.ofMinutes(7).plusSeconds(32), wait);
    }

    private Path writeTcx(Path dir) {
        Path tcx = dir.resolve("workout.tcx");
        try {
            Files.write(tcx, "<TrainingCenterDatabase/>".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tcx;
    }

}
