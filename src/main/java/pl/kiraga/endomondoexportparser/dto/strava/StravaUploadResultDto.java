package pl.kiraga.endomondoexportparser.dto.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code Upload} shape from Strava's OpenAPI spec, returned identically by both
 * {@code POST /uploads} and {@code GET /uploads/{id}}. Strava does not document the
 * exact contents of {@code status}/{@code error}, nor whether {@code activity_id} is
 * populated on a duplicate-upload rejection — so this is deliberately not matched
 * against exact string values. Per the design decision that a duplicate rejection
 * counts as success, {@link #hasActivityId()} is the only signal that matters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaUploadResultDto(
        Long id,
        @JsonProperty("external_id") String externalId,
        String error,
        String status,
        @JsonProperty("activity_id") Long activityId) {

    public boolean hasActivityId() {
        return activityId != null;
    }

    public boolean failed() {
        return activityId == null && error != null && !error.isBlank();
    }

}
