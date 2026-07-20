package pl.kiraga.endomondoexportparser.migration;

import java.util.List;
import java.util.Optional;

/**
 * One picture-bearing workout's photos, as they appear in the handout report.
 * {@code activityId} is empty before migration, when every link reads "pending migration".
 */
public record PhotoGroup(
        String basename,
        String name,
        String startTime,
        Optional<String> activityId,
        List<GeotaggedPhoto> photos) {
}
