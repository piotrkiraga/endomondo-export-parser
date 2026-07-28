package pl.kiraga.endomondoexportparser.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SportMappingUtilTest {

    @Test
    void everySportPresentInTheArchiveMaps() {
        for (String archiveSport : SportMappingUtil.ARCHIVE_SPORTS.keySet()) {
            assertTrue(SportMappingUtil.stravaSportType(archiveSport).isPresent(),
                    "archive sport has no Strava mapping: " + archiveSport);
        }
    }

    @Test
    void archiveSportCountsCoverEveryWorkout() {
        int total = SportMappingUtil.ARCHIVE_SPORTS.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(162, total, "surveyed sport counts should account for all 162 archived workouts");
    }

    @Test
    void bothCyclingVariantsBecomeRide() {
        assertEquals("Ride", SportMappingUtil.stravaSportType("CYCLING_SPORT").orElseThrow());
        assertEquals("Ride", SportMappingUtil.stravaSportType("CYCLING_TRANSPORTATION").orElseThrow());
    }

    @Test
    void footSportsKeepTheirDistinctStravaTypes() {
        assertEquals("Run", SportMappingUtil.stravaSportType("RUNNING").orElseThrow());
        assertEquals("Walk", SportMappingUtil.stravaSportType("WALKING").orElseThrow());
        assertEquals("Hike", SportMappingUtil.stravaSportType("HIKING").orElseThrow());
    }

    @Test
    void unknownSportIsNotGuessed() {
        assertTrue(SportMappingUtil.stravaSportType("KITESURFING").isEmpty());
        assertTrue(SportMappingUtil.stravaSportType(null).isEmpty());
    }

}
