package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StravaDictionarySnapshotTest {

    private static StravaDictionarySnapshot withLocation(String city, String state, String country) {
        return new StravaDictionarySnapshot(1L, "Piotr", "Kiraga", null, city, state, country, Map.of(), "now");
    }

    @Test
    void joinsAllThreePartsWhenAllPresent() {
        assertEquals("Kraków, Małopolskie, Poland", withLocation("Kraków", "Małopolskie", "Poland").location());
    }

    @Test
    void skipsANullPartRatherThanRenderingTheLiteralWordNull() {
        assertEquals("Kraków, Poland", withLocation("Kraków", null, "Poland").location());
    }

    @Test
    void skipsABlankPartToo() {
        assertEquals("Kraków, Poland", withLocation("Kraków", "", "Poland").location());
    }

    @Test
    void nullWhenNothingAtAllIsSet() {
        assertNull(withLocation(null, null, null).location());
    }

    @Test
    void oneNonNullPartNeedsNoSeparator() {
        assertEquals("Kraków", withLocation("Kraków", null, null).location());
    }

}
