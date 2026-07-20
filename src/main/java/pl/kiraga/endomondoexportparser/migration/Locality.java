package pl.kiraga.endomondoexportparser.migration;

/** The place a coordinate reverse-geocodes to: a city (or town/village) and, sometimes, a suburb within it. */
public record Locality(String city, String suburb) {
}
