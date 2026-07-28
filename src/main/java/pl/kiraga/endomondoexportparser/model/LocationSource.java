package pl.kiraga.endomondoexportparser.model;

/**
 * Where a photo's coordinates came from, in resolution order.
 */
public enum LocationSource {

    /**
     * The photo file's own EXIF GPS tags. None of the archive's exported JPEGs carry
     * EXIF, so on a first pass this never fires — but a copy stamped by an earlier run
     * does, which is what makes regeneration idempotent.
     */
    EXIF,

    /** The picture entry's "point" in the workout JSON; present on 11 of 80 pictures. */
    PICTURE_POINT,

    /** The owning workout's first track point, used when the picture has no point. */
    WORKOUT_TRACK

}
