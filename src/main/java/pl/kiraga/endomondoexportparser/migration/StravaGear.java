package pl.kiraga.endomondoexportparser.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The subset of Strava's DetailedGear this app needs: id and its human-readable name. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaGear(String id, String name) {
}
