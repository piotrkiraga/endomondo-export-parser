package pl.kiraga.endomondoexportparser.model;

import pl.kiraga.endomondoexportparser.service.AppStatusResolver;

/**
 * A snapshot of what the app's local data currently holds, as shown on the home page:
 * how big the archive is, how its workouts are spread across {@link LedgerStatus}, and
 * how many coordinates geocoding has cached. {@code archiveWorkoutCount} is null when no
 * archive is configured or present — the only stat that can be absent rather than zero,
 * since an unreadable archive is a different thing from an empty one. Assembled by
 * {@link AppStatusResolver}.
 */
public record AppStatus(
        Integer archiveWorkoutCount,
        int done,
        int failed,
        int skipped,
        int pending,
        int locationCacheSize) {
}
