package pl.kiraga.endomondoexportparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.model.Locality;

/** Uses {@link MockRestServiceServer} bound to the RestClient.Builder; no test here touches the real network. */
public class NominatimClientTest {

    @Test
    void parsesCityAndSuburbFromTheAddressBlock() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NominatimClient client = new NominatimClient(builder);

        server.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=50.061430&lon=19.936580&zoom=14"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"address": {"city": "Kraków", "suburb": "Stare Miasto"}}
                        """, APPLICATION_JSON));

        Optional<Locality> result = client.reverseGeocode(50.06143, 19.93658);

        assertEquals("Kraków", result.orElseThrow().city());
        assertEquals("Stare Miasto", result.orElseThrow().suburb());
        server.verify();
    }

    @Test
    void fallsBackThroughTownVillageAndMunicipality() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NominatimClient client = new NominatimClient(builder);

        server.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=50.500000&lon=20.500000&zoom=14"))
                .andRespond(withSuccess("""
                        {"address": {"village": "Someplace"}}
                        """, APPLICATION_JSON));

        Optional<Locality> result = client.reverseGeocode(50.5, 20.5);

        assertEquals("Someplace", result.orElseThrow().city());
        assertNull(result.orElseThrow().suburb());
    }

    @Test
    void aBilingualNameIsTrimmedToItsFirstHalf() {
        // Officially bilingual places (e.g. Brussels municipalities) carry a "French
        // name - Dutch name" city field regardless of any language negotiation.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NominatimClient client = new NominatimClient(builder);

        server.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=50.837000&lon=4.409000&zoom=14"))
                .andRespond(withSuccess("""
                        {"address": {"city": "Woluwe-Saint-Pierre - Sint-Pieters-Woluwe"}}
                        """, APPLICATION_JSON));

        Optional<Locality> result = client.reverseGeocode(50.837, 4.409);

        assertEquals("Woluwe-Saint-Pierre", result.orElseThrow().city());
    }

    @Test
    void noAddressBlockYieldsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NominatimClient client = new NominatimClient(builder);

        server.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=0.000000&lon=0.000000&zoom=14"))
                .andRespond(withSuccess("{}", APPLICATION_JSON));

        assertTrue(client.reverseGeocode(0, 0).isEmpty());
    }

    @Test
    void serverErrorDegradesToEmptyRatherThanThrowing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NominatimClient client = new NominatimClient(builder);

        server.expect(requestTo(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=1.000000&lon=1.000000&zoom=14"))
                .andRespond(withServerError());

        assertTrue(client.reverseGeocode(1, 1).isEmpty());
    }

}
