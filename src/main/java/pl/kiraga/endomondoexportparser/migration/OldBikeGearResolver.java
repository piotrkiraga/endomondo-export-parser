package pl.kiraga.endomondoexportparser.migration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the old-bike gear correction (design.md's "Old-bike gear correction" decision):
 * a Ride recorded on or before the configured cutoff date gets the configured old bike's
 * gear id; everything else gets null, meaning "don't touch gear". Shared by
 * {@link MigrationExecutor} (what actually gets sent) and {@link WorkoutReportGenerator}
 * (what the offline preview shows), so the two can never diverge.
 */
@Component
public class OldBikeGearResolver {

    private static final String OLD_BIKE_SPORT_TYPE = "Ride";

    /**
     * Personal, not a secret, but still the user's own account data — set via
     * {@code application-local.properties} (see the .example template), not the
     * checked-in {@code application.properties}, matching Strava credentials' handling.
     * Blank/unset (the default) disables gear assignment entirely: {@link #gearIdFor}
     * returns null, same as before this feature existed.
     */
    @Value("${endomondo.strava.old-bike-gear-id:}")
    private String oldBikeGearId;

    /** ISO {@code yyyy-MM-dd}; a Ride workout on or before this date gets {@link #oldBikeGearId}. */
    @Value("${endomondo.strava.old-bike-cutoff-date:}")
    private String oldBikeCutoffDate;

    /** {@code @Value} fields aren't populated outside a Spring context; tests set them directly. */
    void setOldBikeGearId(String oldBikeGearId) {
        this.oldBikeGearId = oldBikeGearId;
    }

    void setOldBikeCutoffDate(String oldBikeCutoffDate) {
        this.oldBikeCutoffDate = oldBikeCutoffDate;
    }

    /**
     * Returns the configured old bike's gear id for a Ride on or before the configured
     * cutoff date; null otherwise (unconfigured, wrong sport, or too recent).
     */
    public String gearIdFor(String stravaSportType, String startTime) {
        if (oldBikeGearId == null || oldBikeGearId.isBlank()
                || oldBikeCutoffDate == null || oldBikeCutoffDate.isBlank()) {
            return null;
        }
        if (!OLD_BIKE_SPORT_TYPE.equals(stravaSportType)) {
            return null;
        }
        String workoutDate = dateOnly(startTime);
        if (workoutDate == null) {
            return null;
        }
        return workoutDate.compareTo(oldBikeCutoffDate) <= 0 ? oldBikeGearId : null;
    }

    /**
     * The raw configured gear id regardless of sport/date, or null if unconfigured —
     * for resolving its display name once rather than re-deriving it per workout.
     */
    public String configuredGearId() {
        return oldBikeGearId == null || oldBikeGearId.isBlank() ? null : oldBikeGearId;
    }

    /** Endomondo's start_time looks like "2015-04-11 11:37:00.0"; the date is a fixed prefix. */
    private static String dateOnly(String startTime) {
        return (startTime == null || startTime.length() < 10) ? null : startTime.substring(0, 10);
    }

}
