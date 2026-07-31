package pl.kiraga.endomondoexportparser.model;

/**
 * The coordinate a location cache entry is keyed by, parsed back out of the cache's
 * {@code "lat,lon"} key for display.
 */
public record CachedCoordinate(double latitude, double longitude) {
}
