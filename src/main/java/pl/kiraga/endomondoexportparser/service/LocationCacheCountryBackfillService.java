package pl.kiraga.endomondoexportparser.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.model.Locality;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;

/**
 * Fills in the country for entries cached before country tracking existed. {@link OsmPlaceLookup}
 * returns on any cache hit, so an already-cached coordinate is never re-resolved through normal
 * use and the gap would never close on its own — this is the explicit, user-triggered way to
 * close it. Only the country is patched in; locality, suburb and nearby feature stay exactly as
 * cached, and an entry whose re-lookup fails or still yields no country is left untouched while
 * the rest of the batch continues.
 */
@Service
public class LocationCacheCountryBackfillService {

    private final LocationCache locationCache;
    private final NominatimClient nominatimClient;

    public LocationCacheCountryBackfillService(LocationCache locationCache, NominatimClient nominatimClient) {
        this.locationCache = locationCache;
        this.nominatimClient = nominatimClient;
    }

    public int backfillMissingCountries() {

        int updated = 0;

        for (Map.Entry<String, PlaceDescription> entry : locationCache.entries().entrySet()) {

            PlaceDescription place = entry.getValue();
            if (place.countryCode() != null) {
                continue;
            }

            String[] parts = entry.getKey().split(",");
            double latitude = Double.parseDouble(parts[0]);
            double longitude = Double.parseDouble(parts[1]);

            Optional<Locality> locality = nominatimClient.reverseGeocode(latitude, longitude);
            if (locality.isEmpty() || locality.get().countryCode() == null) {
                continue;
            }

            locationCache.put(latitude, longitude, new PlaceDescription(
                    place.locality(), place.suburb(), place.nearbyFeature(), locality.get().countryCode()));
            updated++;

        }

        return updated;

    }

}
