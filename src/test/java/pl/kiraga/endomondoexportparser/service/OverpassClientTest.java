package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.model.FeatureKind;
import pl.kiraga.endomondoexportparser.model.NearbyFeature;

/** Uses {@link MockRestServiceServer} bound to the RestClient.Builder; no test here touches the real network. */
public class OverpassClientTest {

    @Test
    void waterOutranksACloserHistoricSite() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"elements": [
                          {"type": "node", "tags": {"historic": "monument", "name": "Old Monument"}, "lat": 50.0545, "lon": 19.9355},
                          {"type": "way", "tags": {"waterway": "river", "name": "Vistula"}, "center": {"lat": 50.058, "lon": 19.94}}
                        ]}
                        """, APPLICATION_JSON));

        Optional<NearbyFeature> feature = client.nearestNotableFeature(50.0544, 19.9354);

        assertEquals("Vistula", feature.orElseThrow().name());
        assertEquals(FeatureKind.WATER, feature.orElseThrow().kind());
    }

    @Test
    void tourismAttractionCountsAsHistoric() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withSuccess("""
                        {"elements": [
                          {"type": "node", "tags": {"tourism": "castle", "name": "Wawel Castle"}, "lat": 50.0544, "lon": 19.9354}
                        ]}
                        """, APPLICATION_JSON));

        Optional<NearbyFeature> feature = client.nearestNotableFeature(50.0544, 19.9354);

        assertEquals("Wawel Castle", feature.orElseThrow().name());
        assertEquals(FeatureKind.HISTORIC, feature.orElseThrow().kind());
    }

    @Test
    void nameEnIsPreferredOverAPossiblyBilingualPlainName() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withSuccess("""
                        {"elements": [
                          {"type": "node", "tags": {"historic": "monument",
                            "name": "Monument de la Cavalerie - Monument van de cavalerie",
                            "name:en": "Cavalry Monument"}, "lat": 50.837, "lon": 4.409}
                        ]}
                        """, APPLICATION_JSON));

        Optional<NearbyFeature> feature = client.nearestNotableFeature(50.837, 4.409);

        assertEquals("Cavalry Monument", feature.orElseThrow().name());
    }

    @Test
    void aBilingualPlainNameIsTrimmedWhenNoTranslationExists() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withSuccess("""
                        {"elements": [
                          {"type": "node", "tags": {"historic": "monument",
                            "name": "Monument de la Cavalerie - Monument van de cavalerie"}, "lat": 50.837, "lon": 4.409}
                        ]}
                        """, APPLICATION_JSON));

        Optional<NearbyFeature> feature = client.nearestNotableFeature(50.837, 4.409);

        assertEquals("Monument de la Cavalerie", feature.orElseThrow().name());
    }

    @Test
    void unnamedFeaturesAreIgnored() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withSuccess("""
                        {"elements": [
                          {"type": "way", "tags": {"waterway": "river"}, "center": {"lat": 50.058, "lon": 19.94}}
                        ]}
                        """, APPLICATION_JSON));

        assertTrue(client.nearestNotableFeature(50.0544, 19.9354).isEmpty());
    }

    @Test
    void noElementsYieldsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withSuccess("{\"elements\": []}", APPLICATION_JSON));

        assertTrue(client.nearestNotableFeature(50.0544, 19.9354).isEmpty());
    }

    @Test
    void serverErrorDegradesToEmptyRatherThanThrowing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OverpassClient client = new OverpassClient(builder);

        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withServerError());

        assertTrue(client.nearestNotableFeature(50.0544, 19.9354).isEmpty());
    }

}
