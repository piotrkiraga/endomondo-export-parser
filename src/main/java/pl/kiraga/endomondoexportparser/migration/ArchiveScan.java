package pl.kiraga.endomondoexportparser.migration;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of scanning the archive's Workouts directory: the workouts keyed by their
 * JSON, plus any TCX files with no matching JSON. A track without metadata carries no
 * name, sport, or source, so it is reported rather than migrated blindly.
 */
public record ArchiveScan(List<WorkoutPair> workouts, List<Path> tracksWithoutMetadata) {
}
