package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HaversineTest {

    @Test
    void sameCoordinateIsZero() {
        assertEquals(0, Haversine.metersBetween(50.06143, 19.93658, 50.06143, 19.93658), 0.01);
    }

    @Test
    void oneDegreeOfLatitudeIsRoughlyOneHundredElevenKilometers() {
        double meters = Haversine.metersBetween(50.0, 19.0, 51.0, 19.0);
        assertEquals(111_000, meters, 2_000);
    }

    @Test
    void wawelCastleToKazimierzIsAFewHundredMeters() {
        // Wawel Castle (50.0544, 19.9354) to a point in Kazimierz (50.0512, 19.9445): ~750m.
        double meters = Haversine.metersBetween(50.0544, 19.9354, 50.0512, 19.9445);
        assertEquals(750, meters, 150);
    }

}
