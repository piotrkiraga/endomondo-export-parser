package pl.kiraga.endomondoexportparser.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The subset of Strava's DetailedAthlete this app needs: enough to show a profile
 * summary and, more importantly, {@code bikes}/{@code shoes} — both come back fully
 * populated (id and name) on the one {@code GET /athlete} call, which is what makes a
 * one-shot gear dictionary refresh possible instead of one lookup per gear id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaAthlete(
        Long id,
        String firstname,
        String lastname,
        @JsonProperty("profile") String profilePictureUrl,
        String city,
        String state,
        String country,
        List<StravaGear> bikes,
        List<StravaGear> shoes) {
}
