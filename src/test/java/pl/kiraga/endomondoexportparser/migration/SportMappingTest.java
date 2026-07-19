package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SportMappingTest {

    @Test
    void everySportPresentInTheArchiveMaps() {
        for (String archiveSport : SportMapping.ARCHIVE_SPORTS.keySet()) {
            assertTrue(SportMapping.stravaSportType(archiveSport).isPresent(),
                    "archive sport has no Strava mapping: " + archiveSport);
        }
    }

    @Test
    void archiveSportCountsCoverEveryWorkout() {
        int total = SportMapping.ARCHIVE_SPORTS.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(162, total, "surveyed sport counts should account for all 162 archived workouts");
    }

    @Test
    void bothCyclingVariantsBecomeRide() {
        assertEquals("Ride", SportMapping.stravaSportType("CYCLING_SPORT").orElseThrow());
        assertEquals("Ride", SportMapping.stravaSportType("CYCLING_TRANSPORTATION").orElseThrow());
    }

    @Test
    void footSportsKeepTheirDistinctStravaTypes() {
        assertEquals("Run", SportMapping.stravaSportType("RUNNING").orElseThrow());
        assertEquals("Walk", SportMapping.stravaSportType("WALKING").orElseThrow());
        assertEquals("Hike", SportMapping.stravaSportType("HIKING").orElseThrow());
    }

    @Test
    void unknownSportIsNotGuessed() {
        assertTrue(SportMapping.stravaSportType("KITESURFING").isEmpty());
        assertTrue(SportMapping.stravaSportType(null).isEmpty());
    }

}
