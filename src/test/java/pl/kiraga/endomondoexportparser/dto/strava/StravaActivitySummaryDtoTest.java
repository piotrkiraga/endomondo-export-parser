package pl.kiraga.endomondoexportparser.dto.strava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class StravaActivitySummaryDtoTest {

    @Test
    void startDateInstantParsesStravasIsoTimestamp() {
        StravaActivitySummaryDto activity =
                new StravaActivitySummaryDto(1234567890L, "2011-09-10T12:58:00Z", 34040.0);

        assertEquals(Instant.parse("2011-09-10T12:58:00Z"), activity.startDateInstant());
    }

    @Test
    void startDateInstantIsNullWhenStravaReturnedNoStartDate() {
        StravaActivitySummaryDto activity = new StravaActivitySummaryDto(1234567890L, null, 34040.0);

        assertNull(activity.startDateInstant());
    }

}
