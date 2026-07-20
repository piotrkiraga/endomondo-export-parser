package pl.kiraga.endomondoexportparser.migration;

/**
 * A resolved place: the enclosing locality, and, when one was found nearby, the single
 * most notable feature (see {@link FeatureKind}'s priority order). {@code nearbyFeature}
 * is null rather than absent when there was none within range.
 */
public record PlaceDescription(String locality, String suburb, NearbyFeature nearbyFeature) {

    /** "{feature} in {suburb}, {locality}", e.g. "along Vistula in Dębniki, Kraków". */
    public String phrase() {
        StringBuilder phrase = new StringBuilder();
        if (nearbyFeature != null) {
            phrase.append(nearbyFeature.kind().preposition()).append(' ').append(nearbyFeature.name()).append(' ');
        }
        phrase.append("in ");
        if (suburb != null && !suburb.isBlank() && !suburb.equalsIgnoreCase(locality)) {
            phrase.append(suburb).append(", ");
        }
        phrase.append(locality);
        return phrase.toString();
    }

}
