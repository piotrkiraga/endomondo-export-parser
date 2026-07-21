package pl.kiraga.endomondoexportparser.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * Drives the real migration: for every workout {@link MigrationPlanner} decided to
 * upload or create, calls {@link StravaClient} exactly once per {@link MigrationLedger}
 * entry — the ledger's pending/done/failed lifecycle is what makes a re-run resumable
 * and duplicate-safe, not luck. Sequential and throttled to ~1 request/second
 * (design.md's mitigation for 429s/daily caps on a 162-activity run), the same posture
 * the OpenStreetMap clients already use.
 *
 * <p>Every created/uploaded activity gets one trailing {@link StravaClient#updateActivity}
 * call regardless of path, because a TCX upload cannot carry Strava's precise sport type
 * at all (the upload endpoint has no {@code sport_type} parameter; TCX's own
 * {@code Sport} element is limited to Running|Biking|Other) — only the update endpoint
 * can correct it to the archive's actual mapped sport. That trailing call is treated as
 * best-effort: the ledger marks {@code DONE} as soon as an activity id exists (before
 * the update call), since that id is what prevents a duplicate on the next run, not
 * whether the cosmetic correction succeeded. The same call also optionally corrects
 * gear on old Rides, see {@link #gearIdFor}.
 */
@Service
public class MigrationExecutor {

    private static final String WORKOUTS_DIR = "Workouts";
    private static final DateTimeFormatter START_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S", Locale.ROOT);
    private static final int MAX_UPLOAD_POLL_ATTEMPTS = 30;
    private static final long UPLOAD_POLL_INTERVAL_MILLIS = 2000;

    private static final String OLD_BIKE_SPORT_TYPE = "Ride";

    private final MigrationPlanner planner;
    private final WorkoutResolver resolver;
    private final MigrationLedger ledger;
    private final StravaClient stravaClient;
    private final RequestThrottle throttle;
    private final LongConsumer sleeper;

    /**
     * Personal, not a secret, but still the user's own account data — set via
     * {@code application-local.properties} (see the .example template), not the
     * checked-in {@code application.properties}, matching Strava credentials' handling.
     * Blank/unset (the default) disables gear assignment entirely: {@link #gearIdFor}
     * returns null and {@link StravaClient#updateActivity} is called without a gear_id,
     * same as before this feature existed.
     */
    @Value("${endomondo.strava.old-bike-gear-id:}")
    private String oldBikeGearId;

    /** ISO {@code yyyy-MM-dd}; a Ride workout on or before this date gets {@link #oldBikeGearId}. */
    @Value("${endomondo.strava.old-bike-cutoff-date:}")
    private String oldBikeCutoffDate;

    @Autowired
    public MigrationExecutor(MigrationPlanner planner, WorkoutResolver resolver, MigrationLedger ledger,
                              StravaClient stravaClient) {
        this(planner, resolver, ledger, stravaClient, new RequestThrottle(1000), MigrationExecutor::realSleep);
    }

    MigrationExecutor(MigrationPlanner planner, WorkoutResolver resolver, MigrationLedger ledger,
                       StravaClient stravaClient, RequestThrottle throttle, LongConsumer sleeper) {
        this.planner = planner;
        this.resolver = resolver;
        this.ledger = ledger;
        this.stravaClient = stravaClient;
        this.throttle = throttle;
        this.sleeper = sleeper;
    }

    /** {@code @Value} fields aren't populated outside a Spring context; tests set them directly. */
    void setOldBikeGearId(String oldBikeGearId) {
        this.oldBikeGearId = oldBikeGearId;
    }

    void setOldBikeCutoffDate(String oldBikeCutoffDate) {
        this.oldBikeCutoffDate = oldBikeCutoffDate;
    }

    private static void realSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@code archiveRoot} is the archive root (containing "Workouts"), matching the
     * reports. Bulk pass over the whole plan; a {@code DONE} or {@code SKIPPED} entry is
     * left alone (see {@link MigrationLedger}) — use {@link #migrateOne} directly, as the
     * interactive review page (task 6.1) does, to force a re-migrate regardless of
     * either state.
     */
    public MigrationRunSummary run(Path archiveRoot) {

        List<ResolvedWorkout> resolved = resolveAll(archiveRoot);

        int uploaded = 0;
        int manual = 0;
        int alreadyDone = 0;
        int skippedByPlanner = 0;
        int skippedByUser = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        List<String> metadataWarnings = new ArrayList<>();

        for (ResolvedWorkout workout : resolved) {

            if (workout.action() == PlannedAction.SKIP) {
                skippedByPlanner++;
                continue;
            }

            if (ledger.isDone(workout.basename())) {
                alreadyDone++;
                continue;
            }

            if (ledger.isSkipped(workout.basename())) {
                skippedByUser++;
                continue;
            }

            WorkoutMigrationOutcome outcome = migrateOne(workout);

            if (outcome.success()) {
                if (workout.action() == PlannedAction.UPLOAD_TCX) {
                    uploaded++;
                } else {
                    manual++;
                }
                if (outcome.metadataWarning() != null) {
                    metadataWarnings.add(outcome.metadataWarning());
                }
            } else {
                failed++;
                failures.add(workout.basename() + ": " + outcome.failureReason());
            }

        }

        return new MigrationRunSummary(uploaded, manual, alreadyDone, skippedByPlanner, skippedByUser, failed,
                List.copyOf(failures), List.copyOf(metadataWarnings));

    }

    /** {@code archiveRoot} is the archive root (containing "Workouts"), matching {@link #run}. */
    public List<ResolvedWorkout> resolveAll(Path archiveRoot) {
        Path workoutsDirectory = archiveRoot.resolve(WORKOUTS_DIR);
        MigrationPlan plan = planner.plan(workoutsDirectory);
        return resolver.resolve(workoutsDirectory, plan);
    }

    /**
     * Migrates exactly one workout, regardless of its current ledger state — used both
     * by {@link #run}'s bulk pass (which checks {@code isDone}/{@code isSkipped} first)
     * and directly by the interactive review page for a first attempt or an explicit
     * user-requested re-migrate of an already-{@code DONE}/{@code SKIPPED}/{@code FAILED}
     * workout. Never call this for a {@link PlannedAction#SKIP} workout — the planner
     * already decided there is nothing to send; the caller is responsible for that check.
     *
     * <p>If the workout is already {@code DONE}, this does <em>not</em> re-send the
     * track: Strava's own upload endpoint rejects a repeat of the same TCX as a
     * duplicate (its {@code external_id} dedup), so there is nothing to gain and a
     * failure to record. Instead it re-sends only the trailing metadata-correction call
     * (name/sport type/description/gear) against the existing activity id, so a
     * re-click of Migrate is how you refresh Strava after fixing something in the
     * archive-derived data (e.g. the gear cutoff), not just a way to retry a failure.
     */
    public WorkoutMigrationOutcome migrateOne(ResolvedWorkout workout) {

        if (workout.name() == null) {
            String reason = "could not resolve name/description for this workout";
            ledger.markFailed(workout.basename(), reason);
            return WorkoutMigrationOutcome.failure(reason);
        }

        Long existingActivityId = ledger.find(workout.basename())
                .filter(entry -> entry.status() == LedgerStatus.DONE)
                .map(LedgerEntry::activityId)
                .orElse(null);

        long activityId;
        if (existingActivityId != null) {
            activityId = existingActivityId;
        } else {
            ledger.markPending(workout.basename(), workout.action());
            try {
                throttle.await();
                activityId = workout.action() == PlannedAction.UPLOAD_TCX ? uploadAndPoll(workout) : createManual(workout);
            } catch (StravaApiException e) {
                ledger.markFailed(workout.basename(), e.getMessage());
                return WorkoutMigrationOutcome.failure(e.getMessage());
            }
            ledger.markDone(workout.basename(), activityId);
        }

        String metadataWarning = null;
        try {
            throttle.await();
            stravaClient.updateActivity(activityId, workout.name(), workout.stravaSportType(), workout.description(),
                    gearIdFor(workout));
        } catch (StravaApiException e) {
            metadataWarning = workout.basename() + ": activity " + activityId
                    + " but the metadata correction call failed: " + e.getMessage();
        }

        return WorkoutMigrationOutcome.success(activityId, metadataWarning);

    }

    private long uploadAndPoll(ResolvedWorkout workout) {

        StravaUploadResult result = stravaClient.uploadTcx(
                workout.tcxFile(), workout.basename(), workout.name(), workout.description());

        for (int attempt = 0; attempt < MAX_UPLOAD_POLL_ATTEMPTS; attempt++) {
            if (result.hasActivityId()) {
                return result.activityId();
            }
            if (result.failed()) {
                throw new StravaApiException("Upload failed: " + result.error());
            }
            sleeper.accept(UPLOAD_POLL_INTERVAL_MILLIS);
            throttle.await();
            result = stravaClient.checkUploadStatus(result.id());
        }

        throw new StravaApiException("Upload did not finish processing after " + MAX_UPLOAD_POLL_ATTEMPTS + " checks");

    }

    private long createManual(ResolvedWorkout workout) {
        StravaActivity activity = stravaClient.createManualActivity(
                workout.name(),
                workout.stravaSportType(),
                toStartInstant(workout),
                workout.durationS() == null ? Duration.ZERO : Duration.ofSeconds(workout.durationS()),
                workout.distanceKm() == null ? null : workout.distanceKm() * 1000.0,
                workout.description());
        return activity.id();
    }

    /**
     * Strava auto-assigns whatever gear is currently marked default on the account to
     * every new activity, which is wrong for a Ride recorded before the rider owned
     * that gear (design.md backlog, added after the user's first real migrated
     * activity). Returns the configured old bike's gear id for a Ride on or before the
     * configured cutoff date; null otherwise (unconfigured, wrong sport, or too
     * recent), which {@link StravaClient#updateActivity} treats as "don't touch gear".
     */
    private String gearIdFor(ResolvedWorkout workout) {
        if (oldBikeGearId == null || oldBikeGearId.isBlank()
                || oldBikeCutoffDate == null || oldBikeCutoffDate.isBlank()) {
            return null;
        }
        if (!OLD_BIKE_SPORT_TYPE.equals(workout.stravaSportType())) {
            return null;
        }
        String workoutDate = dateOnly(workout.startTime());
        if (workoutDate == null) {
            return null;
        }
        return workoutDate.compareTo(oldBikeCutoffDate) <= 0 ? oldBikeGearId : null;
    }

    /** Endomondo's start_time looks like "2015-04-11 11:37:00.0"; the date is a fixed prefix. */
    private static String dateOnly(String startTime) {
        return (startTime == null || startTime.length() < 10) ? null : startTime.substring(0, 10);
    }

    private static Instant toStartInstant(ResolvedWorkout workout) {
        if (workout.startTime() == null) {
            throw new StravaApiException(
                    "Workout " + workout.basename() + " has no start time; cannot create a manual activity");
        }
        return LocalDateTime.parse(workout.startTime(), START_TIME_FORMAT).toInstant(ZoneOffset.UTC);
    }

}
