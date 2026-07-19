package pl.kiraga.endomondoexportparser.migration;

/**
 * Coordinates resolved for a photo, together with which source supplied them.
 */
public record PhotoLocation(double latitude, double longitude, LocationSource source) {
}
