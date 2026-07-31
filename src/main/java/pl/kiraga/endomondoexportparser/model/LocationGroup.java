package pl.kiraga.endomondoexportparser.model;

import java.util.List;

/**
 * One distinct resolved place in the location cache listing, with every cached coordinate
 * that resolves to it. {@code place} is an already-rendered, locale-aware summary.
 */
public record LocationGroup(String place, List<CachedCoordinate> coordinates) {

    public int count() {
        return coordinates.size();
    }

}
