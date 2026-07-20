package pl.kiraga.endomondoexportparser.migration;

/** A named OpenStreetMap feature found near a coordinate, and how far away it is. */
public record NearbyFeature(String name, FeatureKind kind, double distanceMeters) {
}
