package pl.kiraga.endomondoexportparser.migration;

import org.springframework.beans.factory.annotation.Autowired;
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
 * whether the cosmetic correction succeeded.
 */
@Service
public class MigrationExecutor {

    private static final String WORKOUTS_DIR = "Workouts";
    private static final DateTimeFormatter START_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S", Locale.ROOT);
    private static final int MAX_UPLOAD_POLL_ATTEMPTS = 30;
    private static final long UPLOAD_POLL_INTERVAL_MILLIS = 2000;

    private final MigrationPlanner planner;
    private final WorkoutResolver resolver;
    private final MigrationLedger ledger;
    private final StravaClient stravaClient;
    private final RequestThrottle throttle;
    private final LongConsumer sleeper;

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

    private static void realSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** {@code archiveRoot} is the archive root (containing "Workouts"), matching the reports. */
    public MigrationRunSummary run(Path archiveRoot) {

        Path workoutsDirectory = archiveRoot.resolve(WORKOUTS_DIR);
        MigrationPlan plan = planner.plan(workoutsDirectory);
        List<ResolvedWorkout> resolved = resolver.resolve(workoutsDirectory, plan);

        int uploaded = 0;
        int manual = 0;
        int alreadyDone = 0;
        int skippedByPlanner = 0;
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

            ledger.markPending(workout.basename(), workout.action());

            if (workout.name() == null) {
                String reason = "could not resolve name/description for this workout";
                ledger.markFailed(workout.basename(), reason);
                failed++;
                failures.add(workout.basename() + ": " + reason);
                continue;
            }

            long activityId;
            try {
                throttle.await();
                activityId = workout.action() == PlannedAction.UPLOAD_TCX ? uploadAndPoll(workout) : createManual(workout);
            } catch (StravaApiException e) {
                ledger.markFailed(workout.basename(), e.getMessage());
                failed++;
                failures.add(workout.basename() + ": " + e.getMessage());
                continue;
            }

            ledger.markDone(workout.basename(), activityId);
            if (workout.action() == PlannedAction.UPLOAD_TCX) {
                uploaded++;
            } else {
                manual++;
            }

            try {
                throttle.await();
                stravaClient.updateActivity(activityId, workout.name(), workout.stravaSportType(), workout.description());
            } catch (StravaApiException e) {
                metadataWarnings.add(workout.basename() + ": created (activity " + activityId
                        + ") but the metadata correction call failed: " + e.getMessage());
            }

        }

        return new MigrationRunSummary(uploaded, manual, alreadyDone, skippedByPlanner, failed,
                List.copyOf(failures), List.copyOf(metadataWarnings));

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

    private static Instant toStartInstant(ResolvedWorkout workout) {
        if (workout.startTime() == null) {
            throw new StravaApiException(
                    "Workout " + workout.basename() + " has no start time; cannot create a manual activity");
        }
        return LocalDateTime.parse(workout.startTime(), START_TIME_FORMAT).toInstant(ZoneOffset.UTC);
    }

}
