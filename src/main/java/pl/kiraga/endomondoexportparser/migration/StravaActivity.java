package pl.kiraga.endomondoexportparser.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The subset of Strava's DetailedActivity this app needs: just the id it creates or updates. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaActivity(Long id) {
}
