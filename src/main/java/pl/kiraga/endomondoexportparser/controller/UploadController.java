package pl.kiraga.endomondoexportparser.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.format.json.JsonKeys;
import pl.kiraga.endomondoexportparser.format.json.Location;
import pl.kiraga.endomondoexportparser.format.json.Point;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping(value = "/upload")
public class UploadController extends BaseController {

    @RequestMapping(value = "", method = RequestMethod.GET)
    public String view() {
        return "upload";
    }

    @RequestMapping(value = "/process", method = RequestMethod.POST)
    public ModelAndView process(ModelAndView modelAndView, @RequestParam("file") MultipartFile file) throws IOException {

        List errorMessages = new ArrayList();
        List infoMessages = new ArrayList();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);

        if (file != null && file.isEmpty()) {
            errorMessages.add(message("errorMessage.form.field.required.file"));
        }

        if (errorMessages.isEmpty()) {

            try {
                EndomondoJson endomondoJson = processEndomondoJson(file);
                logger.debug("Processed file " + file + ": " + endomondoJson);
                infoMessages.add(message("infoMessage.form.processed", file.getOriginalFilename()));
            } catch (JsonParseException e) {
                logger.debug("Exception occurred (handled, probably incorrect file format recognized): ", e);
                errorMessages.add(message("errorMessage.form.invalidJsonFormat", file.getOriginalFilename()));
            }

        }

        modelAndView.setViewName("upload");

        return modelAndView;

    }

    EndomondoJson processEndomondoJson(MultipartFile file) throws IOException {

        List<Map> dataFromJson;
        EndomondoJson endomondoJson = new EndomondoJson();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        byte[] fileBytes = file.getBytes();
        dataFromJson = mapper.readValue(fileBytes, List.class);

        for (Map endomondoJsonProperties : (List<Map>) dataFromJson) {

            Set<String> keys = endomondoJsonProperties.keySet();

            for (String key : keys) {

                if (key.equals(JsonKeys.NAME)) {
                    endomondoJson.setName((String) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.SPORT)) {
                    endomondoJson.setSport((String) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.SOURCE)) {
                    endomondoJson.setSource((String) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.CREATED_DATE)) {
                    endomondoJson.setCreated_date((String) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.START_TIME)) {
                    endomondoJson.setStart_time((String) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.END_TIME)) {
                    endomondoJson.setEnd_time((String) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.DURATION_S)) {
                    endomondoJson.setDuration_s((Integer) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.DISTANCE_KM)) {
                    endomondoJson.setDistance_km((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.CALORIES_KCAL)) {
                    endomondoJson.setCalories_kcal((Integer) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.ALTITUDE_MIN_M)) {
                    endomondoJson.setAltitude_min_m((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.ALTITUDE_MAX_M)) {
                    endomondoJson.setAltitude_max_m((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.SPEED_AVG_KMH)) {
                    endomondoJson.setSpeed_avg_kmh((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.SPEED_MAX_KMH)) {
                    endomondoJson.setSpeed_max_kmh((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.HYDRATION_L)) {
                    endomondoJson.setHydration_l((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.ASCEND_M)) {
                    endomondoJson.setAscend_m((Double) endomondoJsonProperties.get(key));
                    continue;
                }
                if (key.equals(JsonKeys.DESCEND_M)) {
                    endomondoJson.setDescend_m((Double) endomondoJsonProperties.get(key));
                    continue;
                }

                List<Point> points = new ArrayList<>();

                if (key.equals(JsonKeys.POINTS)) {

                    for (List<Map> pointPropertyListHolder : (List<List<Map>>) endomondoJsonProperties.get(key)) {

                        Point point = new Point();

                        // TODO: Piotr Kiraga: always only first map entity is taken
                        Map pointProperties = pointPropertyListHolder.get(0);
                        Set<String> pointPropertyKeys = pointProperties.keySet();

                        for (String pointPropertyKey : pointPropertyKeys) {

                            if (pointPropertyKey.equals(JsonKeys.ALTITUDE)) {
                                point.setAltitude((Double) endomondoJsonProperties.get(pointPropertyKey));
                                continue;
                            }
                            if (pointPropertyKey.equals(JsonKeys.DISTANCE_KM)) {
                                point.setDistance_km((Double) endomondoJsonProperties.get(pointPropertyKey));
                                continue;
                            }
                            if (pointPropertyKey.equals(JsonKeys.SPEED_KMH)) {
                                point.setSpeed_kmh((Integer) endomondoJsonProperties.get(pointPropertyKey));
                                continue;
                            }
                            if (pointPropertyKey.equals(JsonKeys.TIMESTAMP)) {
                                point.setTimestamp((String) endomondoJsonProperties.get(pointPropertyKey));
                                continue;
                            }

                            if (pointPropertyKey.equals(JsonKeys.LOCATION)) {

                                for (List<Map> locationPropertyListHolder : (List<List<Map>>) pointProperties.get(pointPropertyKey)) {

                                    Location location = new Location();
                                    point.setLocation(location);

                                    // TODO: Piotr Kiraga: always only first map entity is taken
                                    Map locationProperties = locationPropertyListHolder.get(0);
                                    Set<String> locationPropertyKeys = locationProperties.keySet();

                                    for (String locationKey : locationPropertyKeys) {

                                        if (locationKey.equals(JsonKeys.LATITUDE)) {
                                            location.setLatitude((Double) locationProperties.get(locationKey));
                                            continue;
                                        }
                                        if (locationKey.equals(JsonKeys.LONGITUDE)) {
                                            location.setLongitude((Double) locationProperties.get(locationKey));
                                            continue;
                                        }

                                    }

                                    point.setLocation(location);
                                    logger.debug("Generated: " + point);

                                }

                                continue;

                            }

                        }

                        points.add(point);
                        logger.debug("Added point: " + point);

                    }

                    continue;

                }

                logger.debug("Found " + points.size() + " points on: " + points);
                endomondoJson.setPoints(points);

            }
        }

        return endomondoJson;

    }

}
