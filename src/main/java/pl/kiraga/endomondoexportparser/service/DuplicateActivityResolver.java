package pl.kiraga.endomondoexportparser.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.dto.strava.StravaActivitySummaryDto;

/**
 * Recovers the activity id behind a duplicate-upload rejection that carried none, by reading
 * the athlete's own activities back from Strava — the same "don't trust the write call, read
 * the truth back" posture as {@link ConfirmedGearResolver}. Ambiguity always resolves to "not
 * confirmed": a wrong activity id in the ledger would be worse than a workout recorded failed.
 */
@Service
public class DuplicateActivityResolver {

    private static final Duration WINDOW = Duration.ofMinutes(2);
    private static final double DISTANCE_TOLERANCE_METERS = 50.0;

    private final StravaClient stravaClient;

    public DuplicateActivityResolver(StravaClient stravaClient) {
        this.stravaClient = stravaClient;
    }

    /**
     * The id of the one activity Strava already has for this workout, or empty when that
     * cannot be established beyond doubt. A match must start at exactly the same second (both
     * sides derive from the same original TCX timestamp) and, when both distances are known,
     * agree on distance to within {@value #DISTANCE_TOLERANCE_METERS} metres.
     */
    public Optional<Long> findExistingActivity(Instant startTime, Double distanceKm) {

        List<StravaActivitySummaryDto> matches =
                stravaClient.listActivities(startTime.minus(WINDOW), startTime.plus(WINDOW)).stream()
                        .filter(activity -> startTime.equals(activity.startDateInstant()))
                        .toList();

        if (matches.size() != 1) {
            return Optional.empty();
        }

        StravaActivitySummaryDto match = matches.get(0);
        if (distanceKm != null && match.distance() != null
                && Math.abs(match.distance() - distanceKm * 1000.0) > DISTANCE_TOLERANCE_METERS) {
            return Optional.empty();
        }

        return Optional.ofNullable(match.id());

    }

}
