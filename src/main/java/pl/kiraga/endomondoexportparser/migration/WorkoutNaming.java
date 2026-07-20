package pl.kiraga.endomondoexportparser.migration;

/**
 * The name a workout gets when its JSON carries none: "{time of day} {sport}", the same
 * convention Strava itself uses for nameless uploads (decided 2026-07-20 after finding
 * 104 of 162 archive workouts, 64%, unnamed — see design.md).
 *
 * Shared between the photo report and the eventual Strava client (task 3.1) so the name
 * shown in the report always matches the name the migrated activity will carry, letting
 * the two be matched up by eye after migration.
 */
public final class WorkoutNaming {

    private WorkoutNaming() {
    }

    public static String resolve(String name, String startTime, String stravaSportType) {
        return resolve(name, startTime, stravaSportType, null);
    }

    /**
     * As {@link #resolve(String, String, String)}, plus an approximate place appended to
     * a *generated* name (never to a JSON-supplied one, which a human already titled).
     */
    public static String resolve(String name, String startTime, String stravaSportType, PlaceDescription place) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        String sport = (stravaSportType == null || stravaSportType.isBlank()) ? "Workout" : stravaSportType;
        String timeOfDay = timeOfDay(startTime);
        String base = timeOfDay.isEmpty() ? sport : timeOfDay + " " + sport;
        return place == null ? base : base + " " + place.phrase();
    }

    private static String timeOfDay(String startTime) {
        Integer hour = hourOf(startTime);
        if (hour == null) {
            return "";
        }
        if (hour >= 5 && hour < 12) {
            return "Morning";
        }
        if (hour >= 12 && hour < 17) {
            return "Afternoon";
        }
        if (hour >= 17 && hour < 21) {
            return "Evening";
        }
        return "Night";
    }

    /** Endomondo's start_time looks like "2015-04-11 11:37:00.0"; the hour is a fixed substring. */
    private static Integer hourOf(String startTime) {
        if (startTime == null || startTime.length() < 13) {
            return null;
        }
        try {
            return Integer.parseInt(startTime.substring(11, 13));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
