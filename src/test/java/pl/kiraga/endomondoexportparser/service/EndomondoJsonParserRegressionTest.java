package pl.kiraga.endomondoexportparser.service;

import org.junit.jupiter.api.Test;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.Point;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression suite for the Endomondo JSON parser. Grew out of the characterization
 * tests that pinned the old parser's defects (see the fix-json-parser OpenSpec change
 * for the before/after contract); assertions now state intended behavior.
 */
public class EndomondoJsonParserRegressionTest {

    private final EndomondoJsonParser parser = new EndomondoJsonParser();

    private byte[] fixture(String name) throws Exception {
        return getClass().getResourceAsStream("/fixtures/" + name).readAllBytes();
    }

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    // --- Real fixtures parse end-to-end ---

    @Test
    void manualWorkoutFixtureParsesFully() throws Exception {
        EndomondoJson result = parser.parse(fixture("workout-manual.json"));

        assertEquals("Sample manual walk", result.getName());
        assertEquals("WALKING", result.getSport());
        assertEquals("INPUT_MANUAL", result.getSource());
        assertEquals("2014-09-16 09:05:21.0", result.getCreated_date());
        assertEquals(7200, result.getDuration_s());
        assertEquals(6.6, result.getDistance_km());
        assertEquals(491.268, result.getCalories_kcal());
        assertEquals(3.3, result.getSpeed_avg_kmh());

        List<Point> points = result.getPoints();
        assertEquals(26, points.size());
        for (Point point : points) {
            assertNotNull(point.getLocation());
            assertNotNull(point.getLocation().getLatitude());
            assertNotNull(point.getLocation().getLongitude());
            assertNull(point.getAltitude());
            assertNull(point.getDistance_km());
            assertNull(point.getSpeed_kmh());
            assertNull(point.getTimestamp());
        }
    }

    @Test
    void trackedWorkoutFixtureParsesFully() throws Exception {
        EndomondoJson result = parser.parse(fixture("workout-tracked.json"));

        assertEquals("Sample tracked ride", result.getName());
        assertEquals("CYCLING_SPORT", result.getSport());
        assertEquals("TRACK_MOBILE", result.getSource());
        assertEquals(7785, result.getDuration_s());
        assertEquals(34.04, result.getDistance_km());
        assertEquals(1339.81, result.getCalories_kcal());
        // integer JSON values bind to the decimal model fields
        assertEquals(236.0, result.getAltitude_min_m());
        assertEquals(338.0, result.getAltitude_max_m());
        assertEquals(247.0, result.getAscend_m());
        assertEquals(286.0, result.getDescend_m());
        assertEquals(33.9, result.getSpeed_max_kmh());

        List<Point> points = result.getPoints();
        assertEquals(8, points.size());

        // first point has no altitude/speed entries in the file
        Point first = points.get(0);
        assertEquals(47.503514, first.getLocation().getLatitude());
        assertEquals(14.903923, first.getLocation().getLongitude());
        assertEquals(0.0, first.getDistance_km());
        assertNotNull(first.getTimestamp());
        assertNull(first.getAltitude());
        assertNull(first.getSpeed_kmh());

        Point second = points.get(1);
        assertEquals(302.0, second.getAltitude());
        assertEquals(0.0, second.getDistance_km());
        assertEquals(0.0, second.getSpeed_kmh());
        assertNotNull(second.getTimestamp());
        assertNotNull(second.getLocation().getLatitude());
        assertNotNull(second.getLocation().getLongitude());
    }

    // --- Scalar fields and points from minimal documents ---

    @Test
    void typeConformingDocumentPopulatesScalarFields() {
        EndomondoJson result = parser.parse(bytes("["
                + "{\"name\": \"Sample manual walk\"},"
                + "{\"sport\": \"WALKING\"},"
                + "{\"source\": \"INPUT_MANUAL\"},"
                + "{\"created_date\": \"2014-09-16 09:05:21.0\"},"
                + "{\"start_time\": \"2014-09-16 09:00:00.0\"},"
                + "{\"end_time\": \"2014-09-16 11:00:00.0\"},"
                + "{\"duration_s\": 7200},"
                + "{\"distance_km\": 6.6},"
                + "{\"calories_kcal\": 491},"
                + "{\"speed_avg_kmh\": 3.3}"
                + "]"));

        assertEquals("Sample manual walk", result.getName());
        assertEquals("WALKING", result.getSport());
        assertEquals("INPUT_MANUAL", result.getSource());
        assertEquals("2014-09-16 09:05:21.0", result.getCreated_date());
        assertEquals("2014-09-16 09:00:00.0", result.getStart_time());
        assertEquals("2014-09-16 11:00:00.0", result.getEnd_time());
        assertEquals(7200, result.getDuration_s());
        assertEquals(6.6, result.getDistance_km());
        assertEquals(491.0, result.getCalories_kcal());
        assertEquals(3.3, result.getSpeed_avg_kmh());
    }

    @Test
    void pointsAreAttachedWithFullLocations() {
        EndomondoJson result = parser.parse(bytes("["
                + "{\"name\": \"x\"},"
                + "{\"points\": [[{\"location\": [[{\"latitude\": 1.5}, {\"longitude\": 2.5}]]}]]}"
                + "]"));

        List<Point> points = result.getPoints();
        assertEquals(1, points.size());
        assertEquals(1.5, points.get(0).getLocation().getLatitude());
        assertEquals(2.5, points.get(0).getLocation().getLongitude());
    }

    // --- Error handling for malformed input ---

    @Test
    void nonJsonInputThrowsInvalidWorkoutJsonException() {
        assertThrows(InvalidWorkoutJsonException.class, () -> parser.parse(bytes("this is not json")));
    }

    @Test
    void wrongShapeJsonThrowsInvalidWorkoutJsonException() {
        assertThrows(InvalidWorkoutJsonException.class, () -> parser.parse(bytes("{}")));
    }

    @Test
    void emptyArrayReturnsEmptyWorkout() {
        EndomondoJson result = parser.parse(bytes("[]"));

        assertNull(result.getName());
        assertNull(result.getDuration_s());
        assertNull(result.getDistance_km());
        assertTrue(result.getPoints().isEmpty());
    }

}
