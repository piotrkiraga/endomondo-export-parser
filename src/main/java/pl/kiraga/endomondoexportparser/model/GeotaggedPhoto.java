package pl.kiraga.endomondoexportparser.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The outcome of handing out one photo: where the copy was written, the coordinates
 * stamped into it (absent when none could be resolved), and whether this pass wrote
 * the EXIF or found it already there from an earlier run.
 */
public record GeotaggedPhoto(Path copy, PhotoLocation location, boolean stampedNow) {

    public Optional<PhotoLocation> resolvedLocation() {
        return Optional.ofNullable(location);
    }

    public boolean hasLocation() {
        return location != null;
    }

}
