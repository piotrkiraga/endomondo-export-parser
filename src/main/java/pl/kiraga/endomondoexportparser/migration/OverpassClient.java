package pl.kiraga.endomondoexportparser.migration;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds named, notable features (water, historic sites/landmarks, parks, named roads)
 * within a small radius of a coordinate, via OpenStreetMap's Overpass API. Only the
 * single best-ranked feature is surfaced — see {@link FeatureKind}'s priority order —
 * which is enough to name "the river it runs along", not a full survey of the area.
 */
@Service
public class OverpassClient {

    private static final int RADIUS_METERS = 300;

    private final RestClient restClient;
    private final RequestThrottle throttle = new RequestThrottle(1000);

    public OverpassClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://overpass-api.de/api/interpreter")
                .defaultHeader("User-Agent", NominatimClient.USER_AGENT)
                .build();
    }

    public Optional<NearbyFeature> nearestNotableFeature(double latitude, double longitude) {

        throttle.await();

        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("data", query(latitude, longitude));

            JsonNode response = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            return best(response, latitude, longitude);
        } catch (RuntimeException e) {
            return Optional.empty();
        }

    }

    private String query(double lat, double lon) {
        String around = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", RADIUS_METERS, lat, lon);
        return "[out:json][timeout:25];("
                + "way[\"waterway\"~\"river|canal|stream\"][\"name\"]" + around + ";"
                + "way[\"natural\"=\"water\"][\"name\"]" + around + ";"
                + "node[\"historic\"][\"name\"]" + around + ";"
                + "way[\"historic\"][\"name\"]" + around + ";"
                + "node[\"tourism\"~\"attraction|viewpoint|castle\"][\"name\"]" + around + ";"
                + "way[\"leisure\"=\"park\"][\"name\"]" + around + ";"
                + "way[\"highway\"][\"name\"]" + around + ";"
                + ");out center;";
    }

    private Optional<NearbyFeature> best(JsonNode response, double lat, double lon) {

        if (response == null || !response.has("elements")) {
            return Optional.empty();
        }

        List<NearbyFeature> candidates = new ArrayList<>();
        for (JsonNode element : response.get("elements")) {
            NearbyFeature feature = toFeature(element, lat, lon);
            if (feature != null) {
                candidates.add(feature);
            }
        }

        return candidates.stream().min(
                Comparator.comparingInt((NearbyFeature f) -> f.kind().priority())
                        .thenComparingDouble(NearbyFeature::distanceMeters));

    }

    private NearbyFeature toFeature(JsonNode element, double lat, double lon) {

        JsonNode tags = element.get("tags");
        if (tags == null) {
            return null;
        }

        String name = featureName(tags);
        if (name == null || name.isBlank()) {
            return null;
        }

        FeatureKind kind = kindOf(tags);
        if (kind == null) {
            return null;
        }

        double elementLat = coordinate(element, "lat");
        double elementLon = coordinate(element, "lon");
        return new NearbyFeature(name, kind, Haversine.metersBetween(lat, lon, elementLat, elementLon));

    }

    private FeatureKind kindOf(JsonNode tags) {
        if (tags.has("waterway") || "water".equals(textOf(tags, "natural"))) {
            return FeatureKind.WATER;
        }
        if (tags.has("historic") || isNotableTourism(tags)) {
            return FeatureKind.HISTORIC;
        }
        if ("park".equals(textOf(tags, "leisure"))) {
            return FeatureKind.PARK;
        }
        if (tags.has("highway")) {
            return FeatureKind.BOULEVARD;
        }
        return null;
    }

    /** Prefers an explicit "name:en" tag over the possibly-bilingual default "name". */
    private String featureName(JsonNode tags) {
        String english = textOf(tags, "name:en");
        String name = (english != null && !english.isBlank()) ? english : textOf(tags, "name");
        return OsmNames.primary(name);
    }

    private boolean isNotableTourism(JsonNode tags) {
        String tourism = textOf(tags, "tourism");
        return "attraction".equals(tourism) || "viewpoint".equals(tourism) || "castle".equals(tourism);
    }

    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || !value.isTextual()) ? null : value.asString();
    }

    /** Nodes carry lat/lon directly; ways carry them under "center" (requested via "out center"). */
    private double coordinate(JsonNode element, String field) {
        JsonNode direct = element.get(field);
        if (direct != null && direct.isNumber()) {
            return direct.asDouble();
        }
        JsonNode center = element.get("center");
        if (center != null) {
            JsonNode nested = center.get(field);
            if (nested != null && nested.isNumber()) {
                return nested.asDouble();
            }
        }
        return Double.NaN;
    }

}
