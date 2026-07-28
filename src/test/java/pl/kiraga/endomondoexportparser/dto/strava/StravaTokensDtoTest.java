package pl.kiraga.endomondoexportparser.dto.strava;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StravaTokensDtoTest {

    private static final Clock FIXED_NOON =
            Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void notExpiringWhenFarInTheFuture() {
        StravaTokensDto tokens = new StravaTokensDto("a", "r", Instant.parse("2026-07-20T13:00:00Z").getEpochSecond());
        assertFalse(tokens.expiresWithin(Duration.ofMinutes(5), FIXED_NOON));
    }

    @Test
    void expiringWithinTheBuffer() {
        StravaTokensDto tokens = new StravaTokensDto("a", "r", Instant.parse("2026-07-20T12:03:00Z").getEpochSecond());
        assertTrue(tokens.expiresWithin(Duration.ofMinutes(5), FIXED_NOON));
    }

    @Test
    void alreadyExpiredCountsAsExpiring() {
        StravaTokensDto tokens = new StravaTokensDto("a", "r", Instant.parse("2026-07-20T11:00:00Z").getEpochSecond());
        assertTrue(tokens.expiresWithin(Duration.ofMinutes(5), FIXED_NOON));
    }

}
