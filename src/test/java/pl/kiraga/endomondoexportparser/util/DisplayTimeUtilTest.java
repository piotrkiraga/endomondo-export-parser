package pl.kiraga.endomondoexportparser.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DisplayTimeUtilTest {

    @Test
    void convertsUtcToWarsawSummerTimeCest() {
        // 2026-07-27 12:00:00Z is CEST (UTC+2) in Warsaw.
        assertEquals("2026-07-27 14:00:00", DisplayTimeUtil.of(Instant.parse("2026-07-27T12:00:00Z")));
    }

    @Test
    void convertsUtcToWarsawWinterTimeCet() {
        // 2026-01-15 12:00:00Z is CET (UTC+1) in Warsaw — no daylight saving in January.
        assertEquals("2026-01-15 13:00:00", DisplayTimeUtil.of(Instant.parse("2026-01-15T12:00:00Z")));
    }

    @Test
    void parsesAStoredIsoInstantStringFirst() {
        assertEquals("2026-07-27 14:00:00", DisplayTimeUtil.of("2026-07-27T12:00:00Z"));
    }

    @Test
    void dropsSubSecondPrecisionRatherThanShowingIt() {
        assertEquals("2026-07-27 14:00:00", DisplayTimeUtil.of("2026-07-27T12:00:00.231328500Z"));
    }

}
