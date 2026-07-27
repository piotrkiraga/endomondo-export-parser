package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads back what gear Strava actually has on file for an activity — the only trustworthy
 * check, since the write-side {@code gear_id} correction ({@link OldBikeGearResolver}) is
 * confirmed unreliable (see design.md's "Old-bike gear correction" decision). Shared by
 * {@link pl.kiraga.endomondoexportparser.controller.MigrationReviewController} (per-workout,
 * live) and {@link WorkoutReportGenerator} (per already-migrated entry, best-effort), so the
 * two never diverge.
 */
@Service
public class ConfirmedGearResolver {

    private final StravaClient stravaClient;
    private final StravaDictionary stravaDictionary;

    public ConfirmedGearResolver(StravaClient stravaClient, StravaDictionary stravaDictionary) {
        this.stravaClient = stravaClient;
        this.stravaDictionary = stravaDictionary;
    }

    /**
     * The activity's confirmed gear id straight from Strava. Empty if the read itself
     * failed (network error, insufficient scope, etc.) — callers should fall back to
     * "unknown"/"planned" rather than treating that the same as "confirmed no gear".
     * Present-but-blank means Strava confirmed there is no gear on the activity.
     */
    public Optional<String> gearIdFor(long activityId) {
        try {
            String gearId = stravaClient.getActivity(activityId).gearId();
            return Optional.of(gearId == null ? "" : gearId);
        } catch (StravaApiException e) {
            return Optional.empty();
        }
    }

    /**
     * "{name} ({id})", falling back to the bare id if the name can't be resolved.
     * Checks the cached {@link StravaDictionary} first (a pure in-memory/disk read, no
     * network call, and immune to the per-activity rate-limit exposure a live
     * {@link StravaClient#getGear} call carries) before falling back to a live lookup
     * for a gear id the dictionary doesn't have (e.g. never refreshed, or gear added
     * since the last refresh).
     */
    public String display(String gearId) {
        Optional<String> cachedName = stravaDictionary.gearNameFor(gearId);
        if (cachedName.isPresent()) {
            return cachedName.get() + " (" + gearId + ")";
        }
        try {
            StravaGear gear = stravaClient.getGear(gearId);
            return gear.name() == null || gear.name().isBlank() ? gearId : gear.name() + " (" + gearId + ")";
        } catch (StravaApiException e) {
            return gearId;
        }
    }

}
