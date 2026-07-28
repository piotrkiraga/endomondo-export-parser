package pl.kiraga.endomondoexportparser.dto.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The subset of Strava's DetailedGear this app needs: id and its human-readable name. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaGearDto(String id, String name) {
}
