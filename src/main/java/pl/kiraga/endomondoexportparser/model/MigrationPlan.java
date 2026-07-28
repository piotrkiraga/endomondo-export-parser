package pl.kiraga.endomondoexportparser.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The complete dry-run result: what would happen to every workout in the archive.
 * Holding only decisions, it is safe to produce and display without credentials.
 */
public record MigrationPlan(List<WorkoutPlan> workouts, List<Path> tracksWithoutMetadata) {

    public long count(PlannedAction action) {
        return workouts.stream().filter(workout -> workout.action() == action).count();
    }

    public int totalPictures() {
        return workouts.stream().mapToInt(WorkoutPlan::pictureCount).sum();
    }

    public long workoutsWithPictures() {
        return workouts.stream().filter(workout -> workout.pictureCount() > 0).count();
    }

    public List<WorkoutPlan> skipped() {
        return workouts.stream().filter(workout -> workout.action() == PlannedAction.SKIP).toList();
    }

    /** Endomondo sport values in the plan with their workout counts, for the review table. */
    public Map<String, Integer> sportCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (WorkoutPlan workout : workouts) {
            if (workout.endomondoSport() != null) {
                counts.merge(workout.endomondoSport(), 1, Integer::sum);
            }
        }
        return counts;
    }

}
