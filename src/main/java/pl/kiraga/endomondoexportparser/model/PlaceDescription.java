package pl.kiraga.endomondoexportparser.model;

import java.util.Locale;

/**
 * A resolved place: the enclosing locality, and, when one was found nearby, the single
 * most notable feature (see {@link FeatureKind}'s priority order). {@code nearbyFeature}
 * is null rather than absent when there was none within range. {@code countryCode} is an
 * ISO 3166-1 alpha-2 code, null for entries cached before country tracking existed.
 */
public record PlaceDescription(String locality, String suburb, NearbyFeature nearbyFeature, String countryCode) {

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

    /** "{suburb}, {locality}, {country}", e.g. "Dębniki, Kraków, Polska" — country named in {@code displayLocale}. */
    public String summary(Locale displayLocale) {

        StringBuilder summary = new StringBuilder();
        if (suburb != null && !suburb.isBlank() && !suburb.equalsIgnoreCase(locality)) {
            summary.append(suburb).append(", ");
        }
        summary.append(locality);

        String country = countryName(displayLocale);
        if (country != null) {
            summary.append(", ").append(country);
        }

        return summary.toString();

    }

    private String countryName(Locale displayLocale) {

        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }

        // getDisplayCountry echoes the code back unchanged when it doesn't recognise it.
        String name = new Locale("", countryCode).getDisplayCountry(displayLocale);
        return name.isBlank() || name.equalsIgnoreCase(countryCode) ? null : name;

    }

}
