package pl.kiraga.endomondoexportparser.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;

public class WorkoutDescriptionUtilTest {

    private static final String STAMP =
            "This workout (recorded on 2015-04-11) is migrated from Endomondo export data by "
                    + "endomondo-export-parser: https://github.com/piotrkiraga/endomondo-export-parser.";

    @Test
    void plainStampWithoutAPlace() {
        assertEquals(STAMP, WorkoutDescriptionUtil.build("2015-04-11", null));
    }

    @Test
    void stampGainsALeadingSentenceAboutThePlace() {
        PlaceDescription place = new PlaceDescription("Kraków", null, new NearbyFeature("Wawel Castle", FeatureKind.HISTORIC, 80));
        assertEquals("Recorded near Wawel Castle in Kraków.\n\n" + STAMP,
                WorkoutDescriptionUtil.build("2015-04-11", place));
    }

}
