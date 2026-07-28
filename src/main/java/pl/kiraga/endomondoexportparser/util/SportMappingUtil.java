package pl.kiraga.endomondoexportparser.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Total mapping from the Endomondo sport values present in the archive to Strava
 * sport types. Deliberately total rather than defaulting: a sport this table does not
 * know skips its workout with a reason instead of being guessed into the wrong
 * activity type, and the dry-run surfaces the whole table for review before any upload.
 *
 * The archive survey (2026-07-19, 162 workouts) found exactly five distinct values;
 * {@link #ARCHIVE_SPORTS} pins them so a future archive introducing a sixth fails a
 * test rather than silently skipping workouts.
 */
public final class SportMappingUtil {

    /*
     * Target values verified 2026-07-20 against the Strava API v3 SportType enumeration:
     * Ride, Run, Walk and Hike are all exact members. CYCLING_TRANSPORTATION maps to plain
     * Ride rather than EBikeRide or GravelRide — commuting says nothing about the bike.
     */
    private static final Map<String, String> ENDOMONDO_TO_STRAVA = Map.of(
            "CYCLING_SPORT", "Ride",
            "CYCLING_TRANSPORTATION", "Ride",
            "RUNNING", "Run",
            "WALKING", "Walk",
            "HIKING", "Hike");

    /** Distinct sport values observed in the archive, with their workout counts. */
    public static final Map<String, Integer> ARCHIVE_SPORTS = Map.of(
            "CYCLING_SPORT", 107,
            "RUNNING", 26,
            "WALKING", 24,
            "HIKING", 3,
            "CYCLING_TRANSPORTATION", 2);

    private SportMappingUtil() {
    }

    public static Optional<String> stravaSportType(String endomondoSport) {
        return Optional.ofNullable(endomondoSport).map(ENDOMONDO_TO_STRAVA::get);
    }

    public static Set<String> mappedSports() {
        return ENDOMONDO_TO_STRAVA.keySet();
    }

    public static Map<String, String> table() {
        return ENDOMONDO_TO_STRAVA;
    }

}
