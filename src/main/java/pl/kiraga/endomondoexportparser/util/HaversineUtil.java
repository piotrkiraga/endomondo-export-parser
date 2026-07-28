package pl.kiraga.endomondoexportparser.util;

/** Great-circle distance between two coordinates, used to rank Overpass results by proximity. */
public final class HaversineUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private HaversineUtil() {
    }

    public static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

}
