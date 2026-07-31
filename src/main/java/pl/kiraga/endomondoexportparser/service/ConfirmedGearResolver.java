package pl.kiraga.endomondoexportparser.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.dto.strava.StravaGearDto;
import pl.kiraga.endomondoexportparser.exception.StravaApiException;

/**
 * Reads back what gear Strava actually has on file for an activity — the only trustworthy
 * check, since the write-side {@code gear_id} correction ({@link OldBikeGearResolver}) is
 * confirmed unreliable (see design.md's "Old-bike gear correction" decision). Shared by
 * {@link pl.kiraga.endomondoexportparser.controller.MigrationReviewController} (per-workout,
 * always live) and {@link WorkoutReportGenerator} (per already-migrated entry, best-effort and
 * cache-first), so the two never diverge.
 */
@Service
public class ConfirmedGearResolver {

    private final StravaClient stravaClient;
    private final StravaDictionaryService stravaDictionary;
    private final ConfirmedGearCache confirmedGearCache;

    public ConfirmedGearResolver(StravaClient stravaClient, StravaDictionaryService stravaDictionary,
                                  ConfirmedGearCache confirmedGearCache) {
        this.stravaClient = stravaClient;
        this.stravaDictionary = stravaDictionary;
        this.confirmedGearCache = confirmedGearCache;
    }

    /**
     * The activity's confirmed gear id straight from Strava, always a live read — the review
     * page has to show a gear correction just made on Strava's own site, so it must never be
     * served from {@link ConfirmedGearCache} (which this only writes to, see
     * {@link #cachedGearIdFor}). Empty if the read itself failed (network error, insufficient
     * scope, etc.) — callers should fall back to "unknown"/"planned" rather than treating
     * that the same as "confirmed no gear". Present-but-blank means Strava confirmed there
     * is no gear on the activity.
     */
    public Optional<String> gearIdFor(long activityId) {
        try {
            String gearId = stravaClient.getActivity(activityId).gearId();
            String confirmed = gearId == null ? "" : gearId;
            confirmedGearCache.put(activityId, confirmed);
            return Optional.of(confirmed);
        } catch (StravaApiException e) {
            return Optional.empty();
        }
    }

    /**
     * The gear id confirmed for this activity on some earlier occasion, read only from
     * {@link ConfirmedGearCache} — no Strava call, so no rate-limit exposure and no throttle
     * wait for the caller. Empty means nothing has been cached for the activity yet, not that
     * it has no gear; callers wanting an answer either way fall back to {@link #gearIdFor}.
     */
    public Optional<String> cachedGearIdFor(long activityId) {
        return confirmedGearCache.get(activityId);
    }

    /**
     * "{name} ({id})", falling back to the bare id if the name can't be resolved.
     * Checks the cached {@link StravaDictionaryService} first (a pure in-memory/disk read, no
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
            StravaGearDto gear = stravaClient.getGear(gearId);
            return gear.name() == null || gear.name().isBlank() ? gearId : gear.name() + " (" + gearId + ")";
        } catch (StravaApiException e) {
            return gearId;
        }
    }

}
