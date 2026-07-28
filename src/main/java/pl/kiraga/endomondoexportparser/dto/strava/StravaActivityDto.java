package pl.kiraga.endomondoexportparser.dto.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.kiraga.endomondoexportparser.service.StravaClient;

/**
 * The subset of Strava's DetailedActivity this app needs: the id it creates or updates,
 * and (only populated by {@link StravaClient#getActivity}) the gear Strava currently has
 * on file for that activity — read back separately from any create/update call, since
 * those calls' own responses aren't a reliable indicator of what Strava actually stored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaActivityDto(Long id, @JsonProperty("gear_id") String gearId) {
}
