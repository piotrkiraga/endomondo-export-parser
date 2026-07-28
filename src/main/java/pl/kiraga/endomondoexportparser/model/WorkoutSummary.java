package pl.kiraga.endomondoexportparser.model;

import java.util.Locale;
import lombok.Getter;
import pl.kiraga.endomondoexportparser.dto.endomondo.EndomondoJsonDto;
import pl.kiraga.endomondoexportparser.dto.endomondo.PointDto;

/**
 * View model for the upload result summary. Display values are pre-formatted
 * strings; null means "not present in the uploaded file" and the template
 * renders the localized absent-value marker instead.
 */
@Getter
public final class WorkoutSummary {

    private final String name;
    private final String sport;
    private final String source;
    private final String startTime;
    private final String duration;
    private final String distanceKm;
    private final String caloriesKcal;
    private final String speedAvgKmh;
    private final String speedMaxKmh;
    private final String altitudeMinM;
    private final String altitudeMaxM;
    private final int pointCount;
    private final int pointsWithLocationCount;

    private WorkoutSummary(EndomondoJsonDto workout) {
        this.name = workout.getName();
        this.sport = workout.getSport();
        this.source = workout.getSource();
        this.startTime = workout.getStart_time();
        this.duration = formatDuration(workout.getDuration_s());
        this.distanceKm = formatNumber(workout.getDistance_km());
        this.caloriesKcal = formatNumber(workout.getCalories_kcal());
        this.speedAvgKmh = formatNumber(workout.getSpeed_avg_kmh());
        this.speedMaxKmh = formatNumber(workout.getSpeed_max_kmh());
        this.altitudeMinM = formatNumber(workout.getAltitude_min_m());
        this.altitudeMaxM = formatNumber(workout.getAltitude_max_m());
        this.pointCount = workout.getPoints().size();
        this.pointsWithLocationCount = (int) workout.getPoints().stream()
                .filter(WorkoutSummary::hasFullLocation)
                .count();
    }

    public static WorkoutSummary from(EndomondoJsonDto workout) {
        return new WorkoutSummary(workout);
    }

    private static boolean hasFullLocation(PointDto point) {
        return point.getLocation() != null
                && point.getLocation().getLatitude() != null
                && point.getLocation().getLongitude() != null;
    }

    private static String formatDuration(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String formatNumber(Double value) {
        if (value == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

}
