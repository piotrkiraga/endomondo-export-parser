package pl.kiraga.endomondoexportparser.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.dto.strava.StravaAthleteDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaDictionarySnapshotDto;
import pl.kiraga.endomondoexportparser.dto.strava.StravaGearDto;
import pl.kiraga.endomondoexportparser.util.DisplayTimeUtil;

/**
 * Strava's rarely-changing "dictionary" data — the athlete's profile and every gear
 * id's name — refreshed explicitly by the user (never automatically, matching every
 * other Strava write/read in this app) via one {@code GET /athlete} call rather than
 * one {@code GET /gear/{id}} call per id. {@link ConfirmedGearResolver} consults this
 * first before falling back to a live per-id lookup. {@code refreshedAt} is stored as a
 * raw UTC ISO-8601 instant, like every other timestamp this app generates — see
 * {@link DisplayTimeUtil} for where that's converted to something a human reads.
 */
@Service
public class StravaDictionaryService {

    private final StravaClient stravaClient;
    private final StravaDictionaryCache cache;
    private final Clock clock;

    @Autowired
    public StravaDictionaryService(StravaClient stravaClient, StravaDictionaryCache cache) {
        this(stravaClient, cache, Clock.systemUTC());
    }

    StravaDictionaryService(StravaClient stravaClient, StravaDictionaryCache cache, Clock clock) {
        this.stravaClient = stravaClient;
        this.cache = cache;
        this.clock = clock;
    }

    /** One Strava call; replaces whatever was cached before entirely. */
    public StravaDictionarySnapshotDto refresh() {

        StravaAthleteDto athlete = stravaClient.getAthlete();

        Map<String, String> gearNames = new LinkedHashMap<>();
        gearNames.putAll(gearNamesOf(athlete.bikes()));
        gearNames.putAll(gearNamesOf(athlete.shoes()));

        StravaDictionarySnapshotDto snapshot = new StravaDictionarySnapshotDto(
                athlete.id(), athlete.firstname(), athlete.lastname(), athlete.profilePictureUrl(),
                athlete.city(), athlete.state(), athlete.country(), gearNames, clock.instant().toString());

        cache.save(snapshot);
        return snapshot;

    }

    public Optional<StravaDictionarySnapshotDto> current() {
        return cache.load();
    }

    /** Empty if never refreshed, the gear id is unknown, or its cached name is blank. */
    public Optional<String> gearNameFor(String gearId) {
        return cache.load()
                .map(StravaDictionarySnapshotDto::gearNames)
                .map(names -> names.get(gearId))
                .filter(name -> name != null && !name.isBlank());
    }

    private static Map<String, String> gearNamesOf(List<StravaGearDto> gear) {
        Map<String, String> names = new LinkedHashMap<>();
        if (gear != null) {
            gear.forEach(g -> names.put(g.id(), g.name()));
        }
        return names;
    }

}
