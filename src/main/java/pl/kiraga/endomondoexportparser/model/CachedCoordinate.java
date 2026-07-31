package pl.kiraga.endomondoexportparser.model;

import java.util.Locale;

/**
 * The coordinate a location cache entry is keyed by, parsed back out of the cache's
 * {@code "lat,lon"} key for display.
 */
public record CachedCoordinate(double latitude, double longitude) {

    /** Human-readable pair, e.g. "50.061, 19.937" — not the cache's own comma-only key format. */
    public String display() {
        return String.format(Locale.ROOT, "%.3f, %.3f", latitude, longitude);
    }

}
