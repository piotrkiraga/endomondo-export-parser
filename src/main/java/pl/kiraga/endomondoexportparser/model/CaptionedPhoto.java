package pl.kiraga.endomondoexportparser.model;

/** A handed-out photo paired with its resolved place, when reverse geocoding found one. */
public record CaptionedPhoto(GeotaggedPhoto photo, PlaceDescription place) {
}
