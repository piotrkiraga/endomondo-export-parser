package pl.kiraga.endomondoexportparser.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class OsmPlaceLookupTest {

    /**
     * Only one expectation is registered on each mock server. If the cache failed to
     * short-circuit the second {@code lookup()} call, that call would hit the mock
     * server with no matching expectation left and fail immediately — so this test
     * proves caching by the absence of that failure, not by counting calls directly.
     */
    @Test
    void combinesLocalityAndFeatureAndCachesThem(@TempDir Path dir) {

        RestClient.Builder nominatimBuilder = RestClient.builder();
        MockRestServiceServer nominatimServer = MockRestServiceServer.bindTo(nominatimBuilder).build();
        NominatimClient nominatimClient = new NominatimClient(nominatimBuilder);

        RestClient.Builder overpassBuilder = RestClient.builder();
        MockRestServiceServer overpassServer = MockRestServiceServer.bindTo(overpassBuilder).build();
        OverpassClient overpassClient = new OverpassClient(overpassBuilder);

        nominatimServer.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=50.061430&lon=19.936580&zoom=14"))
                .andRespond(withSuccess("""
                        {"address": {"city": "Kraków"}}
                        """, APPLICATION_JSON));
        overpassServer.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withSuccess("""
                        {"elements": [{"type": "way", "tags": {"waterway": "river", "name": "Vistula"}, "center": {"lat": 50.058, "lon": 19.94}}]}
                        """, APPLICATION_JSON));

        OsmPlaceLookup lookup = new OsmPlaceLookup(nominatimClient, overpassClient, new LocationCache(dir.resolve("cache.json")));

        Optional<PlaceDescription> first = lookup.lookup(50.06143, 19.93658);
        Optional<PlaceDescription> second = lookup.lookup(50.06143, 19.93658);

        assertEquals("Kraków", first.orElseThrow().locality());
        assertEquals("Vistula", first.orElseThrow().nearbyFeature().name());
        assertEquals(first, second);

        nominatimServer.verify();
        overpassServer.verify();
    }

    @Test
    void noLocalityMeansNoPlaceAtAll(@TempDir Path dir) {

        RestClient.Builder nominatimBuilder = RestClient.builder();
        MockRestServiceServer nominatimServer = MockRestServiceServer.bindTo(nominatimBuilder).build();
        NominatimClient nominatimClient = new NominatimClient(nominatimBuilder);

        RestClient.Builder overpassBuilder = RestClient.builder();
        OverpassClient overpassClient = new OverpassClient(overpassBuilder);

        nominatimServer.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=0.000000&lon=0.000000&zoom=14"))
                .andRespond(withSuccess("{}", APPLICATION_JSON));

        OsmPlaceLookup lookup = new OsmPlaceLookup(nominatimClient, overpassClient, new LocationCache(dir.resolve("cache.json")));

        assertTrue(lookup.lookup(0, 0).isEmpty(), "Overpass must not even be queried without a locality");
    }

}
