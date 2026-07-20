package pl.kiraga.endomondoexportparser.migration;

import java.util.Optional;

/**
 * Resolves a coordinate to a place. An interface so tests can supply canned results
 * instead of exercising the real network-bound implementation ({@link OsmPlaceLookup}).
 */
public interface PlaceLookup {

    Optional<PlaceDescription> lookup(double latitude, double longitude);

}
