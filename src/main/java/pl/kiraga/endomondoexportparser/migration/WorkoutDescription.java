package pl.kiraga.endomondoexportparser.migration;

/**
 * The description migration sets on every activity: the "Migrated from Endomondo"
 * stamp decided in design.md, plus, when a place resolves, a sentence describing
 * roughly where the workout happened, plus a credit line pointing back at the tool
 * (added 2026-07-21, at the user's request).
 */
public final class WorkoutDescription {

    private static final String CREDIT_LINE =
            "Migrated by Piotr Kiraga using endomondo-export-parser: "
                    + "https://github.com/piotrkiraga/endomondo-export-parser";

    private WorkoutDescription() {
    }

    public static String build(String originalDate, PlaceDescription place) {
        StringBuilder description = new StringBuilder("Migrated from Endomondo (recorded ")
                .append(originalDate).append(").");
        if (place != null) {
            description.append(" Recorded ").append(place.phrase()).append('.');
        }
        description.append("\n\n").append(CREDIT_LINE);
        return description.toString();
    }

}
