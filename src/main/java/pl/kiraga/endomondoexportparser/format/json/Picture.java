package pl.kiraga.endomondoexportparser.format.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * One entry of a workout's "pictures". The export nests the file reference under
 * "picture" and the coordinates under "point"; the parser flattens both, so "url"
 * is the archive-relative path into resources/gfx/ and "point" is where the photo
 * was taken.
 *
 * "point" is the second of three location sources, behind the photo file's own EXIF
 * and ahead of the workout's first track point; it is absent on most pictures.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@ToString
public class Picture {

    private String created_date;
    private String url;
    private Location point;

}
