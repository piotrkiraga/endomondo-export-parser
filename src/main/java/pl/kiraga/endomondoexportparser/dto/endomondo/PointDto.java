package pl.kiraga.endomondoexportparser.dto.endomondo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class PointDto {

    private LocationDto location;
    private Double distance_km;
    private Double speed_kmh;
    private Double altitude;
    private String timestamp;

}
