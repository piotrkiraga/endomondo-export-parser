package pl.kiraga.endomondoexportparser.model;

/**
 * The place a coordinate reverse-geocodes to: a city (or town/village), sometimes a suburb
 * within it, and the enclosing country as an ISO 3166-1 alpha-2 code (uppercase).
 */
public record Locality(String city, String suburb, String countryCode) {
}
