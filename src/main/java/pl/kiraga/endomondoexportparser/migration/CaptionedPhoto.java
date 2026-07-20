package pl.kiraga.endomondoexportparser.migration;

/** A handed-out photo paired with its resolved place, when reverse geocoding found one. */
public record CaptionedPhoto(GeotaggedPhoto photo, PlaceDescription place) {
}
