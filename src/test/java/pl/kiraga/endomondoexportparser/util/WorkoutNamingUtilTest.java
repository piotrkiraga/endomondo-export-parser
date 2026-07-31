package pl.kiraga.endomondoexportparser.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;

/**
 * Pins the "{time of day} {sport}" fallback convention decided in design.md, including
 * the exact scenario written into strava-migration/spec.md, plus the place-enrichment
 * rules decided when location lookup was added.
 */
public class WorkoutNamingUtilTest {

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50), null);
    private static final PlaceDescription WOLUWE_IN_BRUSSELS =
            new PlaceDescription("Brussels", "Woluwe-Saint-Pierre", null, null);

    @Test
    void existingNameWinsOverGeneration() {
        assertEquals("Sample ride", WorkoutNamingUtil.resolve("Sample ride", "2020-01-01 07:15:00.0", "Run"));
    }

    @Test
    void unnamedMorningRunFromTheSpecScenario() {
        assertEquals("Morning Run", WorkoutNamingUtil.resolve(null, "2020-01-01 07:15:00.0", "Run"));
    }

    @Test
    void blankNameIsTreatedAsAbsent() {
        assertEquals("Afternoon Ride", WorkoutNamingUtil.resolve("   ", "2020-01-01 14:00:00.0", "Ride"));
    }

    @Test
    void eachTimeOfDayBucket() {
        assertEquals("Morning Walk", WorkoutNamingUtil.resolve(null, "2020-01-01 05:00:00.0", "Walk"));
        assertEquals("Afternoon Walk", WorkoutNamingUtil.resolve(null, "2020-01-01 12:00:00.0", "Walk"));
        assertEquals("Evening Walk", WorkoutNamingUtil.resolve(null, "2020-01-01 17:00:00.0", "Walk"));
        assertEquals("Night Walk", WorkoutNamingUtil.resolve(null, "2020-01-01 21:00:00.0", "Walk"));
        assertEquals("Night Walk", WorkoutNamingUtil.resolve(null, "2020-01-01 04:59:00.0", "Walk"));
    }

    @Test
    void missingStartTimeDropsTheTimeOfDayPrefix() {
        assertEquals("Ride", WorkoutNamingUtil.resolve(null, null, "Ride"));
        assertEquals("Ride", WorkoutNamingUtil.resolve(null, "not a timestamp", "Ride"));
    }

    @Test
    void missingSportFallsBackToWorkout() {
        assertEquals("Morning Workout", WorkoutNamingUtil.resolve(null, "2020-01-01 07:15:00.0", null));
    }

    @Test
    void generatedNameGainsANearbyFeatureAndLocality() {
        assertEquals("Evening Ride along Vistula in Kraków",
                WorkoutNamingUtil.resolve(null, "2020-01-01 18:00:00.0", "Ride", VISTULA_IN_KRAKOW));
    }

    @Test
    void generatedNameGainsASuburbWhenNoFeatureIsNearby() {
        assertEquals("Afternoon Walk in Woluwe-Saint-Pierre, Brussels",
                WorkoutNamingUtil.resolve(null, "2020-01-01 14:00:00.0", "Walk", WOLUWE_IN_BRUSSELS));
    }

    @Test
    void aSuppliedNameIsNeverEnrichedWithAPlace() {
        assertEquals("Sample ride", WorkoutNamingUtil.resolve("Sample ride", "2020-01-01 07:15:00.0", "Run", VISTULA_IN_KRAKOW));
    }

    @Test
    void absentPlaceLeavesTheNamePlain() {
        assertEquals("Morning Run", WorkoutNamingUtil.resolve(null, "2020-01-01 07:15:00.0", "Run", null));
    }

}
