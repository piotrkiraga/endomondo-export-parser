package pl.kiraga.endomondoexportparser.migration;

import java.nio.file.Path;
import java.util.List;

/**
 * Every archived photo, accounted for exactly once: either under the workout that
 * references it, or in {@code unmatchedPhotos} when no workout does.
 */
public record PhotoReport(List<PhotoGroup> groups, List<Path> unmatchedPhotos) {

    public int photoCount() {
        return groups.stream().mapToInt(group -> group.photos().size()).sum();
    }

}
