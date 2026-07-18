package pl.kiraga.endomondoexportparser.service;

import org.springframework.stereotype.Service;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Parses the frozen Endomondo workout export format: a JSON array of single-key
 * objects, where "points" holds an array of arrays of single-key maps and each
 * point's "location" holds another array of arrays of single-key maps.
 *
 * The document is normalized by merging every array-of-single-key-objects into a
 * plain object node (last key wins), then bound to the DTOs in one step.
 */
@Service
public class EndomondoJsonParser {

    private static final String POINTS = "points";
    private static final String LOCATION = "location";

    private final JsonMapper mapper = JsonMapper.builder().build();

    public EndomondoJson parse(byte[] content) {

        JsonNode root;
        try {
            root = mapper.readTree(content);
        } catch (JacksonException e) {
            throw new InvalidWorkoutJsonException("Content is not valid JSON", e);
        }

        if (!root.isArray()) {
            throw new InvalidWorkoutJsonException(
                    "Expected a JSON array of workout properties but got " + root.getNodeType());
        }

        try {
            ObjectNode workout = mergeSingleKeyObjects(root);
            workout.set(POINTS, normalizePoints(workout.get(POINTS)));
            return mapper.treeToValue(workout, EndomondoJson.class);
        } catch (JacksonException e) {
            throw new InvalidWorkoutJsonException("JSON does not match the workout export structure", e);
        }

    }

    private ArrayNode normalizePoints(JsonNode pointsNode) {

        ArrayNode normalized = mapper.createArrayNode();
        if (pointsNode == null || !pointsNode.isArray()) {
            return normalized;
        }

        for (JsonNode pointWrapper : pointsNode) {
            ObjectNode point = mergeSingleKeyObjects(pointWrapper);
            JsonNode location = point.get(LOCATION);
            if (location != null && location.isArray() && !location.isEmpty()) {
                point.set(LOCATION, mergeSingleKeyObjects(location.get(0)));
            }
            normalized.add(point);
        }

        return normalized;

    }

    private ObjectNode mergeSingleKeyObjects(JsonNode arrayOfObjects) {

        ObjectNode merged = mapper.createObjectNode();
        if (!arrayOfObjects.isArray()) {
            return merged;
        }

        for (JsonNode element : arrayOfObjects) {
            if (element.isObject()) {
                for (Map.Entry<String, JsonNode> property : element.properties()) {
                    merged.set(property.getKey(), property.getValue());
                }
            }
        }

        return merged;

    }

}
