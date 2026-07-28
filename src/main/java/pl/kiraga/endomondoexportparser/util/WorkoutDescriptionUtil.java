package pl.kiraga.endomondoexportparser.util;

import pl.kiraga.endomondoexportparser.model.PlaceDescription;

/**
 * The description migration sets on every activity: when a place resolves, a sentence
 * describing roughly where the workout happened, leading, followed by a stamp naming
 * the original recording date and crediting the tool (not a person — reworded
 * 2026-07-21, at the user's request, from an earlier two-paragraph "Migrated from
 * Endomondo (recorded ...)." plus a separate "Migrated by Piotr Kiraga using ..." line).
 */
public final class WorkoutDescriptionUtil {

    private static final String STAMP_TEMPLATE =
            "This workout (recorded on %s) is migrated from Endomondo export data by "
                    + "endomondo-export-parser: https://github.com/piotrkiraga/endomondo-export-parser.";

    private WorkoutDescriptionUtil() {
    }

    /** {@code recordedAt} is a date-time string, e.g. "2015-04-11 11:37:00" (added 2026-07-21, previously date-only). */
    public static String build(String recordedAt, PlaceDescription place) {
        String stamp = String.format(STAMP_TEMPLATE, recordedAt);
        if (place == null) {
            return stamp;
        }
        return "Recorded " + place.phrase() + ".\n\n" + stamp;
    }

}
