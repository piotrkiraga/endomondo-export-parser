package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkoutDescriptionTest {

    @Test
    void plainStampWithoutAPlace() {
        assertEquals("Migrated from Endomondo (recorded 2015-04-11).",
                WorkoutDescription.build("2015-04-11", null));
    }

    @Test
    void stampGainsASentenceAboutThePlace() {
        PlaceDescription place = new PlaceDescription("Kraków", null, new NearbyFeature("Wawel Castle", FeatureKind.HISTORIC, 80));
        assertEquals("Migrated from Endomondo (recorded 2015-04-11). Recorded near Wawel Castle in Kraków.",
                WorkoutDescription.build("2015-04-11", place));
    }

}
