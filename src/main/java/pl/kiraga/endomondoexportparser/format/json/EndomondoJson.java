package pl.kiraga.endomondoexportparser.format.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@ToString
public class EndomondoJson {

    private String name;
    private String sport;
    private String source;
    private String created_date;
    private String start_time;
    private String end_time;
    private Integer duration_s;
    private Double distance_km;
    private Integer calories_kcal;
    private Double altitude_min_m;
    private Double altitude_max_m;
    private Double speed_avg_kmh;
    private Double speed_max_kmh;
    private Double hydration_l;
    private Double ascend_m;
    private Double descend_m;

    List<Point> points = new ArrayList<>();

}
