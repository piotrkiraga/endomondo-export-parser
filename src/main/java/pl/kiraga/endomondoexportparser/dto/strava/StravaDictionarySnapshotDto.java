package pl.kiraga.endomondoexportparser.dto.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.stream.Stream;
import pl.kiraga.endomondoexportparser.service.StravaDictionaryService;

/**
 * A point-in-time snapshot of Strava's rarely-changing "dictionary" data: the athlete's
 * profile basics and every gear id's name (bikes and shoes combined). Replaced whole on
 * every {@link StravaDictionaryService#refresh}, not merged incrementally — this data changes
 * so infrequently that a full, honest replace is simpler and safer than reconciling.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaDictionarySnapshotDto(
        Long athleteId,
        String firstname,
        String lastname,
        String profilePictureUrl,
        String city,
        String state,
        String country,
        Map<String, String> gearNames,
        String refreshedAt) {

    /**
     * "{city}, {state}, {country}" with any blank/null part dropped rather than
     * rendered literally — plain concatenation of a null field (Strava frequently
     * leaves state/country unset) produces a bare "null" in the output, which is what
     * this exists to avoid. Null, not blank, if nothing at all is set.
     */
    public String location() {
        return Stream.of(city, state, country)
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

}
