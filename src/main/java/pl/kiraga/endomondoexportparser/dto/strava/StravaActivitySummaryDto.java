package pl.kiraga.endomondoexportparser.dto.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import pl.kiraga.endomondoexportparser.service.StravaClient;

/**
 * The subset of Strava's SummaryActivity that {@link StravaClient#listActivities} needs to
 * recognise an activity the archive was already uploaded to: its id, when it started, and
 * how far it went (metres). {@code start_date} stays a raw string and is parsed on demand,
 * matching this codebase's convention of manual date parsing over a Jackson date module.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaActivitySummaryDto(
        Long id,
        @JsonProperty("start_date") String startDate,
        Double distance) {

    public Instant startDateInstant() {
        return startDate == null ? null : Instant.parse(startDate);
    }

}
