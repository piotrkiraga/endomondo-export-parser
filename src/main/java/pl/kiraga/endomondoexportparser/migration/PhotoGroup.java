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
        String stravaSportType,
        PlaceDescription place,
        Optional<String> activityId,
        List<CaptionedPhoto> photos) {

    /** The name as it will appear once migrated: the JSON name, or the generated fallback. */
    public String displayName() {
        return WorkoutNaming.resolve(name, startTime, stravaSportType, place);
    }

}
