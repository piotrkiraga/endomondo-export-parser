package pl.kiraga.endomondoexportparser.migration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The one place this app formats a timestamp for a human to read: the user's own local
 * time zone (Europe/Warsaw), "yyyy-MM-dd HH:mm:ss" — no letter T/Z, no sub-second noise.
 * Everything the app generates itself (ledger entries, the Strava dictionary refresh) is
 * stored as a raw UTC ISO-8601 instant string (unambiguous, sortable, the standard choice
 * for storage) and only ever converted to this format at the point of display, here.
 * <p>
 * Workout timestamps recorded by Endomondo itself ({@code ResolvedWorkout.startTime()})
 * are a different kind of data entirely — historical, device-local, with no reliable time
 * zone attached at all — and are deliberately never run through this formatter:
 * converting a workout recorded somewhere else to Europe/Warsaw would misrepresent it,
 * not fix it. They're shown exactly as Endomondo recorded them.
 */
public final class DisplayTime {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(DISPLAY_ZONE);

    private DisplayTime() {
    }

    public static String of(Instant instant) {
        return FORMAT.format(instant);
    }

    /** As {@link #of(Instant)}, parsing a stored ISO-8601 instant string first. */
    public static String of(String isoInstant) {
        return of(Instant.parse(isoInstant));
    }

}
