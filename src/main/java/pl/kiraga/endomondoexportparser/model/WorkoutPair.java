package pl.kiraga.endomondoexportparser.model;

import java.nio.file.Path;

/**
 * The two halves of one archived workout, sharing a basename such as
 * "2011-09-10 12_58_59.0". The JSON is required; the TCX is absent when the export
 * wrote no track file, in which case the workout cannot be uploaded.
 */
public record WorkoutPair(String basename, Path json, Path tcx) {

    public boolean hasTrack() {
        return tcx != null;
    }

}
