package pl.kiraga.endomondoexportparser.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.Point;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests: these pin the parser's CURRENT behavior, defects included,
 * so the platform upgrade and the parser rewrite can be diffed against a known baseline.
 * Tests marked "pinned defect" assert behavior that is wrong on purpose; the parser-fix
 * change is expected to update them deliberately, together with the
 * workout-json-parsing spec requirements they mirror.
 */
public class UploadControllerCharacterizationTest {

    private final UploadController controller = new UploadController();

    private EndomondoJson parse(byte[] content) throws Exception {
        return controller.processEndomondoJson(new MockMultipartFile("file", content));
    }

    private byte[] fixture(String name) throws Exception {
        return getClass().getResourceAsStream("/fixtures/" + name).readAllBytes();
    }

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    // --- Pinned defect: blind numeric casts crash on real export files ---

    @Test
    void manualWorkoutFixtureCrashesOnDecimalCalories() throws Exception {
        // calories_kcal is 491.268 in the file but Integer in the model
        assertThrows(ClassCastException.class, () -> parse(fixture("workout-manual.json")));
    }

    @Test
    void trackedWorkoutFixtureCrashesOnDecimalCalories() throws Exception {
        // calories_kcal is 1339.81 in the file but Integer in the model
        assertThrows(ClassCastException.class, () -> parse(fixture("workout-tracked.json")));
    }

    // --- Scalar fields parse correctly when JSON number kinds match the model types ---

    @Test
    void typeConformingDocumentPopulatesScalarFields() throws Exception {
        EndomondoJson result = parse(bytes("["
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
        assertEquals(491, result.getCalories_kcal());
        assertEquals(3.3, result.getSpeed_avg_kmh());
    }

    // --- Pinned defect: parsed points are never attached to the result ---

    @Test
    void pointsAreNeverAttachedToTheResult() throws Exception {
        EndomondoJson result = parse(bytes("["
                + "{\"name\": \"x\"},"
                + "{\"points\": [[{\"location\": [[{\"latitude\": 1.5}, {\"longitude\": 2.5}]]}]]}"
                + "]"));

        List<Point> points = result.getPoints();
        assertTrue(points.isEmpty(), "points are built internally but never set on the result");
    }

    // --- Error handling for malformed input ---

    @Test
    void nonJsonInputThrowsJsonParseException() {
        // the upload flow catches exactly this type to render a user-facing error
        assertThrows(JsonParseException.class, () -> parse(bytes("this is not json")));
    }

    @Test
    void wrongShapeJsonThrowsMismatchedInputException() {
        // valid JSON that is not an array is NOT a JsonParseException,
        // so the upload flow does not handle it
        assertThrows(MismatchedInputException.class, () -> parse(bytes("{}")));
    }

    @Test
    void emptyArrayReturnsEmptyWorkout() throws Exception {
        EndomondoJson result = parse(bytes("[]"));

        assertNull(result.getName());
        assertNull(result.getDuration_s());
        assertNull(result.getDistance_km());
        assertTrue(result.getPoints().isEmpty());
    }

}
