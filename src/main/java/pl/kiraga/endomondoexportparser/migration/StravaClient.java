package pl.kiraga.endomondoexportparser.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Strava API v3 client: refreshes tokens, uploads TCX files and polls their processing
 * status, and creates/updates activities. Every endpoint/field name here is taken from
 * Strava's official OpenAPI spec (developers.strava.com/swagger/swagger.json), verified
 * 2026-07-20 — not folklore. Genuine gaps in what Strava documents (exact upload status
 * strings, whether a duplicate rejection populates activity_id) are handled defensively:
 * see {@link StravaUploadResult}.
 */
@Service
public class StravaClient {

    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(5);

    /** Dev-mode traffic log — enable with endomondo.strava.log-traffic=true, see the .example file. */
    private static final Logger TRAFFIC_LOG = LoggerFactory.getLogger(
            "pl.kiraga.endomondoexportparser.migration.StravaTraffic");
    private static final int MAX_LOGGED_BODY_CHARS = 4000;
    private static final Pattern CLIENT_SECRET_PARAM = Pattern.compile("(client_secret=)[^&]*");
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "\"(access_token|refresh_token|client_secret)\"\\s*:\\s*\"[^\"]*\"");

    private final RestClient restClient;
    private final StravaTokenStore tokenStore;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final LongConsumer sleeper;

    @Autowired
    public StravaClient(RestClient.Builder builder, StravaTokenStore tokenStore,
                         @Value("${STRAVA_CLIENT_ID:}") String clientId,
                         @Value("${STRAVA_CLIENT_SECRET:}") String clientSecret,
                         @Value("${endomondo.strava.log-traffic:false}") boolean logTraffic) {
        this(builder, tokenStore, clientId, clientSecret, Clock.systemUTC(), StravaClient::realSleep, logTraffic);
    }

    StravaClient(RestClient.Builder builder, StravaTokenStore tokenStore, String clientId, String clientSecret,
                 Clock clock, LongConsumer sleeper) {
        this(builder, tokenStore, clientId, clientSecret, clock, sleeper, false);
    }

    StravaClient(RestClient.Builder builder, StravaTokenStore tokenStore, String clientId, String clientSecret,
                 Clock clock, LongConsumer sleeper, boolean logTraffic) {
        RestClient.Builder configured = builder.baseUrl("https://www.strava.com");
        if (logTraffic) {
            configured = configured.requestInterceptor((request, body, execution) -> {
                TRAFFIC_LOG.info(">> {} {}\n{}", request.getMethod(), redact(request.getURI()), truncate(body));
                ClientHttpResponse response = execution.execute(request, body);
                byte[] responseBody = response.getBody().readAllBytes();
                TRAFFIC_LOG.info("<< {} {} {}\n{}", response.getStatusCode(), request.getMethod(),
                        redact(request.getURI()), truncate(responseBody));
                return new BufferedClientHttpResponse(response, responseBody);
            });
        }
        this.restClient = configured.build();
        this.tokenStore = tokenStore;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    private static String redact(URI uri) {
        return CLIENT_SECRET_PARAM.matcher(uri.toString()).replaceAll("$1***");
    }

    private static String truncate(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        text = SENSITIVE_JSON_FIELD.matcher(text).replaceAll(m -> "\"" + m.group(1) + "\":\"***\"");
        return text.length() > MAX_LOGGED_BODY_CHARS
                ? text.substring(0, MAX_LOGGED_BODY_CHARS) + "... (truncated, " + text.length() + " chars total)"
                : text;
    }

    /**
     * A request's response body can only be read once; this replays the buffered bytes to
     * the real caller after the interceptor has logged them.
     */
    private static final class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }

    private static void realSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Tokens ---

    /** A valid access token, refreshing first if the stored one is expiring within {@link #REFRESH_BUFFER}. */
    String accessToken() {
        StravaTokens current = tokenStore.load()
                .orElseThrow(() -> new StravaApiException(
                        "No Strava tokens stored; complete the OAuth connect flow first"));
        if (!current.expiresWithin(REFRESH_BUFFER, clock)) {
            return current.accessToken();
        }
        return refreshAccessToken(current.refreshToken()).accessToken();
    }

    /** Exchanges a refresh token for a fresh access/refresh token pair, persisting the result. */
    public StravaTokens refreshAccessToken(String refreshToken) {
        return exchangeForTokens("refresh_token", "refresh_token", refreshToken);
    }

    /**
     * Exchanges an OAuth authorization code (from the `/strava/callback` redirect, task
     * 3.2) for the first access/refresh token pair, persisting the result. Same response
     * shape as a refresh — Strava's token endpoint additionally returns an "athlete"
     * object on this grant type, harmlessly ignored by {@link StravaTokens}.
     */
    public StravaTokens exchangeAuthorizationCode(String code) {
        return exchangeForTokens("authorization_code", "code", code);
    }

    private StravaTokens exchangeForTokens(String grantType, String grantParamName, String grantParamValue) {

        requireCredentials();

        StravaTokens tokens = withRetryOn429(() -> restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/token")
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .queryParam("grant_type", grantType)
                        .queryParam(grantParamName, grantParamValue)
                        .build())
                .retrieve()
                .body(StravaTokens.class));

        if (tokens == null) {
            throw new StravaApiException("Strava token exchange returned no body");
        }
        tokenStore.save(tokens);
        return tokens;

    }

    private void requireCredentials() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new StravaApiException(
                    "STRAVA_CLIENT_ID and STRAVA_CLIENT_SECRET environment variables must be set");
        }
    }

    // --- Uploads ---

    /** Uploads a TCX file with a deterministic external_id (the workout's archive basename). */
    public StravaUploadResult uploadTcx(Path tcxFile, String externalId, String name, String description) {

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new FileSystemResource(tcxFile));
        parts.add("data_type", "tcx");
        parts.add("external_id", externalId);
        if (name != null) {
            parts.add("name", name);
        }
        if (description != null) {
            parts.add("description", description);
        }

        return withRetryOn429(() -> restClient.post()
                .uri("/api/v3/uploads")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(StravaUploadResult.class));

    }

    /**
     * Checks an upload's processing status. Poll until {@link StravaUploadResult#hasActivityId()}
     * (done — whether a fresh upload or Strava's duplicate rejection, both count as success
     * per design.md) or {@link StravaUploadResult#failed()}.
     */
    public StravaUploadResult checkUploadStatus(long uploadId) {
        return withRetryOn429(() -> restClient.get()
                .uri("/api/v3/uploads/{id}", uploadId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(StravaUploadResult.class));
    }

    // --- Activities ---

    /** Creates a manual (trackless) activity from workout metadata. */
    public StravaActivity createManualActivity(String name, String sportType, Instant startDateLocal,
                                                Duration elapsedTime, Double distanceMeters, String description) {

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("name", name);
        form.add("sport_type", sportType);
        form.add("start_date_local", startDateLocal.toString());
        form.add("elapsed_time", String.valueOf(elapsedTime.toSeconds()));
        if (distanceMeters != null) {
            form.add("distance", String.valueOf(distanceMeters));
        }
        if (description != null) {
            form.add("description", description);
        }

        return withRetryOn429(() -> restClient.post()
                .uri("/api/v3/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(StravaActivity.class));

    }

    /** As {@link #updateActivity(long, String, String, String, String)}, with no gear assignment. */
    public StravaActivity updateActivity(long activityId, String name, String sportType, String description) {
        return updateActivity(activityId, name, sportType, description, null);
    }

    /**
     * Sets name/sport type/description on an already-created activity (uploaded or
     * manual), and optionally its gear. {@code gearId} is omitted from the request
     * entirely when null, never sent as a JSON {@code null} — per Strava's own docs the
     * literal string {@code "none"} is what clears gear, so a bare null could mean
     * something else entirely to their API and risk clearing gear on an activity we
     * never meant to touch.
     */
    public StravaActivity updateActivity(long activityId, String name, String sportType, String description,
                                          String gearId) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("sport_type", sportType);
        body.put("description", description);
        if (gearId != null) {
            body.put("gear_id", gearId);
        }

        return withRetryOn429(() -> restClient.put()
                .uri("/api/v3/activities/{id}", activityId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(StravaActivity.class));

    }

    /**
     * Reads an activity back as Strava currently has it stored — in particular
     * {@link StravaActivity#gearId}, which a create/update call's own response echoes
     * back unreliably (see the "old-bike gear correction" decision in design.md: the
     * public API has accepted and 200'd a {@code gear_id} that it silently never
     * applied). A separate GET is the only trustworthy way to see what Strava actually
     * has on file.
     */
    public StravaActivity getActivity(long activityId) {
        return withRetryOn429(() -> restClient.get()
                .uri("/api/v3/activities/{id}", activityId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(StravaActivity.class));
    }

    /** Looks up a piece of gear's human-readable name, e.g. for displaying alongside its bare id. */
    public StravaGear getGear(String gearId) {
        return withRetryOn429(() -> restClient.get()
                .uri("/api/v3/gear/{id}", gearId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(StravaGear.class));
    }

    /**
     * The connected athlete's profile, including every bike and shoe with its name —
     * the one call {@link StravaDictionary#refresh} uses to populate the whole gear
     * dictionary at once, rather than one {@link #getGear} call per gear id.
     */
    public StravaAthlete getAthlete() {
        return withRetryOn429(() -> restClient.get()
                .uri("/api/v3/athlete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(StravaAthlete.class));
    }

    // --- Rate limiting ---

    /**
     * Strava documents no Retry-After header; the 15-minute window is documented to
     * reset on the clock at :00/:15/:30/:45 UTC, so on a 429 this waits until the next
     * such boundary and retries exactly once. A second 429 propagates — a personal
     * migration tool has no business retrying indefinitely; resuming from the ledger
     * tomorrow (tasks 4.2/4.3) is the real recovery path for a day-limit exhaustion.
     */
    private <T> T withRetryOn429(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests e) {
            sleeper.accept(waitUntilNextQuarterHourUtc().toMillis());
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests stillLimited) {
                throw new StravaApiException("Still rate-limited after waiting for the next window", stillLimited);
            } catch (HttpStatusCodeException e2) {
                throw new StravaApiException(describeFault(e2), e2);
            }
        } catch (HttpStatusCodeException e) {
            throw new StravaApiException(describeFault(e), e);
        } catch (RestClientException e) {
            throw new StravaApiException("Strava API call failed: " + e.getMessage(), e);
        }
    }

    Duration waitUntilNextQuarterHourUtc() {
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        int minutesToNext = 15 - (now.getMinute() % 15);
        ZonedDateTime next = now.plusMinutes(minutesToNext).withSecond(0).withNano(0);
        return Duration.between(now, next).plusSeconds(2);
    }

    private String describeFault(HttpStatusCodeException e) {
        return "Strava API call failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString();
    }

}
