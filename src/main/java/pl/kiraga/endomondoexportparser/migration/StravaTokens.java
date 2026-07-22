package pl.kiraga.endomondoexportparser.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Access/refresh token pair for the Strava API, persisted to {@code data/generated/strava-tokens.json}.
 * Also doubles as the binding target for {@code POST /oauth/token} responses (which
 * additionally carry {@code expires_in} and {@code token_type}; both ignored here as
 * redundant with {@code expires_at} / constant, per developers.strava.com/swagger).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaTokens(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_at") long expiresAtEpochSeconds) {

    public boolean expiresWithin(Duration buffer, Clock clock) {
        return Instant.ofEpochSecond(expiresAtEpochSeconds).isBefore(clock.instant().plus(buffer));
    }

}
