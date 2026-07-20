package pl.kiraga.endomondoexportparser.migration;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Combines Nominatim (locality) and Overpass (the single best nearby feature) behind
 * {@link LocationCache}, so the same real-world coordinate is only ever resolved once.
 */
@Service
public class OsmPlaceLookup implements PlaceLookup {

    private final NominatimClient nominatimClient;
    private final OverpassClient overpassClient;
    private final LocationCache cache;

    public OsmPlaceLookup(NominatimClient nominatimClient, OverpassClient overpassClient, LocationCache cache) {
        this.nominatimClient = nominatimClient;
        this.overpassClient = overpassClient;
        this.cache = cache;
    }

    @Override
    public Optional<PlaceDescription> lookup(double latitude, double longitude) {

        Optional<PlaceDescription> cached = cache.get(latitude, longitude);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Locality> locality = nominatimClient.reverseGeocode(latitude, longitude);
        if (locality.isEmpty()) {
            return Optional.empty();
        }

        NearbyFeature feature = overpassClient.nearestNotableFeature(latitude, longitude).orElse(null);
        PlaceDescription place = new PlaceDescription(locality.get().city(), locality.get().suburb(), feature);

        cache.put(latitude, longitude, place);
        return Optional.of(place);

    }

}
