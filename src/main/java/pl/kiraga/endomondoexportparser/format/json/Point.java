package pl.kiraga.endomondoexportparser.format.json;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class Point {

    private Location location;
    private Double distance_km;
    private Double speed_kmh;
    private Double altitude;
    private String timestamp;

}
