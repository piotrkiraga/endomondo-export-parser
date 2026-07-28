package pl.kiraga.endomondoexportparser.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlaceDescriptionTest {

    @Test
    void localityOnly() {
        PlaceDescription place = new PlaceDescription("Kraków", null, null);
        assertEquals("in Kraków", place.phrase());
    }

    @Test
    void suburbAndLocality() {
        PlaceDescription place = new PlaceDescription("Brussels", "Woluwe-Saint-Pierre", null);
        assertEquals("in Woluwe-Saint-Pierre, Brussels", place.phrase());
    }

    @Test
    void featureAndLocality() {
        PlaceDescription place = new PlaceDescription("Kraków", null, new NearbyFeature("Vistula", FeatureKind.WATER, 50));
        assertEquals("along Vistula in Kraków", place.phrase());
    }

    @Test
    void featureSuburbAndLocality() {
        PlaceDescription place = new PlaceDescription("Brussels", "Woluwe-Saint-Pierre",
                new NearbyFeature("Théâtre Botanique", FeatureKind.HISTORIC, 90));
        assertEquals("near Théâtre Botanique in Woluwe-Saint-Pierre, Brussels", place.phrase());
    }

    @Test
    void suburbSameAsLocalityIsNotRepeated() {
        // Real Nominatim result observed for Tervuren: address.suburb == address.city == "Tervuren".
        PlaceDescription place = new PlaceDescription("Tervuren", "Tervuren", null);
        assertEquals("in Tervuren", place.phrase());
    }

    @Test
    void readsNaturallyWithBothASuburbAndAFeature() {
        PlaceDescription place = new PlaceDescription("Kraków", "Dębniki", new NearbyFeature("Wisła", FeatureKind.WATER, 40));
        assertEquals("along Wisła in Dębniki, Kraków", place.phrase());
    }

}
