package pl.kiraga.endomondoexportparser.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CachedCoordinateTest {

    @Test
    void displayRendersThreeDecimalPlaces() {
        assertEquals("50.061, 19.937", new CachedCoordinate(50.06143, 19.93658).display());
    }

    @Test
    void displayPadsToThreeDecimalPlaces() {
        assertEquals("50.000, -4.500", new CachedCoordinate(50.0, -4.5).display());
    }

    @Test
    void displayUsesADotRegardlessOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"));
            assertEquals("50.061, 19.937", new CachedCoordinate(50.06143, 19.93658).display());
        } finally {
            Locale.setDefault(original);
        }
    }

}
