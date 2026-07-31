package pl.kiraga.endomondoexportparser.model;

import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlaceDescriptionTest {

    @Test
    void localityOnly() {
        PlaceDescription place = new PlaceDescription("Kraków", null, null, null);
        assertEquals("in Kraków", place.phrase());
    }

    @Test
    void suburbAndLocality() {
        PlaceDescription place = new PlaceDescription("Brussels", "Woluwe-Saint-Pierre", null, null);
        assertEquals("in Woluwe-Saint-Pierre, Brussels", place.phrase());
    }

    @Test
    void featureAndLocality() {
        PlaceDescription place =
                new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50), null);
        assertEquals("along Vistula in Kraków", place.phrase());
    }

    @Test
    void featureSuburbAndLocality() {
        PlaceDescription place = new PlaceDescription("Brussels", "Woluwe-Saint-Pierre",
                new NearbyFeature("Théâtre Botanique", FeatureKind.HISTORIC, 90), null);
        assertEquals("near Théâtre Botanique in Woluwe-Saint-Pierre, Brussels", place.phrase());
    }

    @Test
    void suburbSameAsLocalityIsNotRepeated() {
        // Real Nominatim result observed for Tervuren: address.suburb == address.city == "Tervuren".
        PlaceDescription place = new PlaceDescription("Tervuren", "Tervuren", null, null);
        assertEquals("in Tervuren", place.phrase());
    }

    @Test
    void readsNaturallyWithBothASuburbAndAFeature() {
        PlaceDescription place =
                new PlaceDescription("Kraków", "Dębniki", new NearbyFeature("Wisła", FeatureKind.WATER, 40), null);
        assertEquals("along Wisła in Dębniki, Kraków", place.phrase());
    }

    @Test
    void summaryNamesTheCountryInTheGivenDisplayLocale() {
        PlaceDescription place = new PlaceDescription("Kraków", "Dębniki", null, "PL");
        assertEquals("Dębniki, Kraków, Poland", place.summary(Locale.ENGLISH));
        assertEquals("Dębniki, Kraków, Polska", place.summary(Locale.forLanguageTag("pl")));
    }

    @Test
    void summaryIgnoresTheNearbyFeature() {
        PlaceDescription place = new PlaceDescription("Brussels", "Woluwe-Saint-Pierre",
                new NearbyFeature("Théâtre Botanique", FeatureKind.HISTORIC, 90), "BE");
        assertEquals("Woluwe-Saint-Pierre, Brussels, Belgium", place.summary(Locale.ENGLISH));
    }

    @Test
    void summaryOmitsTheCountryWhenNoCodeIsStored() {
        PlaceDescription place = new PlaceDescription("Kraków", "Dębniki", null, null);
        assertEquals("Dębniki, Kraków", place.summary(Locale.ENGLISH));
    }

    @Test
    void summaryOmitsTheCountryWhenTheCodeDoesNotResolve() {
        // QQ is unassigned in the JDK's locale data, so getDisplayCountry echoes it back unchanged.
        PlaceDescription place = new PlaceDescription("Kraków", "Dębniki", null, "QQ");
        assertEquals("Dębniki, Kraków", place.summary(Locale.ENGLISH));
    }

    @Test
    void summaryOmitsAMissingBlankOrRepeatedSuburb() {
        assertEquals("Kraków, Poland", new PlaceDescription("Kraków", null, null, "PL").summary(Locale.ENGLISH));
        assertEquals("Kraków, Poland", new PlaceDescription("Kraków", "  ", null, "PL").summary(Locale.ENGLISH));
        assertEquals("Tervuren, Belgium",
                new PlaceDescription("Tervuren", "tervuren", null, "BE").summary(Locale.ENGLISH));
    }

}
