package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the "{time of day} {sport}" fallback convention decided in design.md, including
 * the exact scenario written into strava-migration/spec.md, plus the place-enrichment
 * rules decided when location lookup was added.
 */
public class WorkoutNamingTest {

    private static final PlaceDescription VISTULA_IN_KRAKOW =
            new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50));
    private static final PlaceDescription WOLUWE_IN_BRUSSELS = new PlaceDescription("Brussels", "Woluwe-Saint-Pierre", null);

    @Test
    void existingNameWinsOverGeneration() {
        assertEquals("Sample ride", WorkoutNaming.resolve("Sample ride", "2020-01-01 07:15:00.0", "Run"));
    }

    @Test
    void unnamedMorningRunFromTheSpecScenario() {
        assertEquals("Morning Run", WorkoutNaming.resolve(null, "2020-01-01 07:15:00.0", "Run"));
    }

    @Test
    void blankNameIsTreatedAsAbsent() {
        assertEquals("Afternoon Ride", WorkoutNaming.resolve("   ", "2020-01-01 14:00:00.0", "Ride"));
    }

    @Test
    void eachTimeOfDayBucket() {
        assertEquals("Morning Walk", WorkoutNaming.resolve(null, "2020-01-01 05:00:00.0", "Walk"));
        assertEquals("Afternoon Walk", WorkoutNaming.resolve(null, "2020-01-01 12:00:00.0", "Walk"));
        assertEquals("Evening Walk", WorkoutNaming.resolve(null, "2020-01-01 17:00:00.0", "Walk"));
        assertEquals("Night Walk", WorkoutNaming.resolve(null, "2020-01-01 21:00:00.0", "Walk"));
        assertEquals("Night Walk", WorkoutNaming.resolve(null, "2020-01-01 04:59:00.0", "Walk"));
    }

    @Test
    void missingStartTimeDropsTheTimeOfDayPrefix() {
        assertEquals("Ride", WorkoutNaming.resolve(null, null, "Ride"));
        assertEquals("Ride", WorkoutNaming.resolve(null, "not a timestamp", "Ride"));
    }

    @Test
    void missingSportFallsBackToWorkout() {
        assertEquals("Morning Workout", WorkoutNaming.resolve(null, "2020-01-01 07:15:00.0", null));
    }

    @Test
    void generatedNameGainsANearbyFeatureAndLocality() {
        assertEquals("Evening Ride along Vistula in Kraków",
                WorkoutNaming.resolve(null, "2020-01-01 18:00:00.0", "Ride", VISTULA_IN_KRAKOW));
    }

    @Test
    void generatedNameGainsASuburbWhenNoFeatureIsNearby() {
        assertEquals("Afternoon Walk in Woluwe-Saint-Pierre, Brussels",
                WorkoutNaming.resolve(null, "2020-01-01 14:00:00.0", "Walk", WOLUWE_IN_BRUSSELS));
    }

    @Test
    void aSuppliedNameIsNeverEnrichedWithAPlace() {
        assertEquals("Sample ride", WorkoutNaming.resolve("Sample ride", "2020-01-01 07:15:00.0", "Run", VISTULA_IN_KRAKOW));
    }

    @Test
    void absentPlaceLeavesTheNamePlain() {
        assertEquals("Morning Run", WorkoutNaming.resolve(null, "2020-01-01 07:15:00.0", "Run", null));
    }

}
