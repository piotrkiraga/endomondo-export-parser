package pl.kiraga.endomondoexportparser.migration;

/**
 * The description migration sets on every activity: the "Migrated from Endomondo"
 * stamp decided in design.md, plus, when a place resolves, a sentence describing
 * roughly where the workout happened.
 */
public final class WorkoutDescription {

    private WorkoutDescription() {
    }

    public static String build(String originalDate, PlaceDescription place) {
        StringBuilder description = new StringBuilder("Migrated from Endomondo (recorded ")
                .append(originalDate).append(").");
        if (place != null) {
            description.append(" Recorded ").append(place.phrase()).append('.');
        }
        return description.toString();
    }

}
