package pl.kiraga.endomondoexportparser.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import pl.kiraga.endomondoexportparser.dto.endomondo.EndomondoJsonDto;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;

public class WorkoutSummaryTest {

    private final EndomondoJsonParser parser = new EndomondoJsonParser();

    private EndomondoJsonDto parseFixture(String name) throws IOException {
        return parser.parse(getClass().getResourceAsStream("/fixtures/" + name).readAllBytes());
    }

    @Test
    void trackedWorkoutSummaryCarriesAllFields() throws IOException {
        WorkoutSummary summary = WorkoutSummary.from(parseFixture("workout-tracked.json"));

        assertEquals("Sample tracked ride", summary.getName());
        assertEquals("CYCLING_SPORT", summary.getSport());
        assertEquals("TRACK_MOBILE", summary.getSource());
        assertEquals("2011-09-10 12:58:00.0", summary.getStartTime());
        assertEquals("2:09:45", summary.getDuration());
        assertEquals("34.0", summary.getDistanceKm());
        assertEquals("1339.8", summary.getCaloriesKcal());
        assertEquals("15.7", summary.getSpeedAvgKmh());
        assertEquals("33.9", summary.getSpeedMaxKmh());
        assertEquals("236.0", summary.getAltitudeMinM());
        assertEquals("338.0", summary.getAltitudeMaxM());
        assertEquals(8, summary.getPointCount());
        assertEquals(8, summary.getPointsWithLocationCount());
    }

    @Test
    void manualWorkoutSummaryMarksMissingMetricsAsAbsent() throws IOException {
        WorkoutSummary summary = WorkoutSummary.from(parseFixture("workout-manual.json"));

        assertEquals("Sample manual walk", summary.getName());
        assertEquals("WALKING", summary.getSport());
        assertEquals("INPUT_MANUAL", summary.getSource());
        assertEquals("2:00:00", summary.getDuration());
        assertEquals("6.6", summary.getDistanceKm());
        assertEquals("491.3", summary.getCaloriesKcal());
        assertEquals("3.3", summary.getSpeedAvgKmh());
        assertNull(summary.getSpeedMaxKmh());
        assertNull(summary.getAltitudeMinM());
        assertNull(summary.getAltitudeMaxM());
        assertEquals(26, summary.getPointCount());
        assertEquals(26, summary.getPointsWithLocationCount());
    }

}
