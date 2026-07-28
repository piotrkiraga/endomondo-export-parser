package pl.kiraga.endomondoexportparser.service;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.kiraga.endomondoexportparser.model.Locality;
import pl.kiraga.endomondoexportparser.util.OsmNamesUtil;
import pl.kiraga.endomondoexportparser.util.RequestThrottleUtil;
import tools.jackson.databind.JsonNode;

/**
 * Reverse-geocodes a coordinate to its enclosing city/suburb via OpenStreetMap's
 * Nominatim. Self-throttled to Nominatim's usage-policy limit of one request per
 * second; any failure (network error, no result) degrades to {@code Optional.empty()}
 * rather than throwing, since a missing place must never block a workout's migration.
 * The injected {@code builder} carries connect/read timeouts from {@code spring.http.clients.*}
 * (see application.properties) — set globally rather than per-client so
 * {@code MockRestServiceServer.bindTo(builder)} in tests isn't overridden by a competing
 * {@code requestFactory()} call here.
 */
@Service
public class NominatimClient {

    static final String USER_AGENT =
            "endomondo-export-parser (personal migration tool; https://github.com/piotrkiraga/endomondo-export-parser)";

    private final RestClient restClient;
    private final RequestThrottleUtil throttle = new RequestThrottleUtil(1000);

    public NominatimClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public Optional<Locality> reverseGeocode(double latitude, double longitude) {

        throttle.await();

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", String.format(Locale.ROOT, "%.6f", latitude))
                            .queryParam("lon", String.format(Locale.ROOT, "%.6f", longitude))
                            .queryParam("zoom", "14")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

    }

    private Optional<Locality> parse(JsonNode response) {

        if (response == null || !response.has("address")) {
            return Optional.empty();
        }

        JsonNode address = response.get("address");
        String city = firstNonBlank(address, "city", "town", "village", "municipality");
        if (city == null) {
            return Optional.empty();
        }

        String suburb = firstNonBlank(address, "suburb", "neighbourhood", "city_district");
        return Optional.of(new Locality(city, suburb));

    }

    private String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asString().isBlank()) {
                return OsmNamesUtil.primary(value.asString());
            }
        }
        return null;
    }

}
