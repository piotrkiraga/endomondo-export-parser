package pl.kiraga.endomondoexportparser.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.migration.LedgerEntry;
import pl.kiraga.endomondoexportparser.migration.LedgerStatus;
import pl.kiraga.endomondoexportparser.migration.MigrationExecutor;
import pl.kiraga.endomondoexportparser.migration.MigrationLedger;
import pl.kiraga.endomondoexportparser.migration.PlannedAction;
import pl.kiraga.endomondoexportparser.migration.ResolvedWorkout;
import pl.kiraga.endomondoexportparser.migration.StravaActivity;
import pl.kiraga.endomondoexportparser.migration.StravaApiException;
import pl.kiraga.endomondoexportparser.migration.StravaClient;
import pl.kiraga.endomondoexportparser.migration.StravaGear;
import pl.kiraga.endomondoexportparser.migration.StravaTokenStore;
import pl.kiraga.endomondoexportparser.migration.WorkoutPhotoResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The one-workout-at-a-time interactive migration flow (task 6.1), decided with the
 * user 2026-07-21: every migration is a deliberate, reviewed click, never a bulk
 * unattended run — {@link MigrationExecutor#run} exists but this page never calls it,
 * only {@link MigrationExecutor#migrateOne}. Workouts are addressed by their position
 * in the plan's actionable (non-planner-skipped) order, recomputed on every request
 * rather than held in server-side session state, so the page survives a restart and a
 * bookmarked/shared URL always means the same workout.
 */
@Controller
@RequestMapping("/migration/review")
public class MigrationReviewController extends BaseController {

    private final MigrationExecutor executor;
    private final MigrationLedger ledger;
    private final WorkoutPhotoResolver photoResolver;
    private final StravaTokenStore stravaTokenStore;
    private final StravaClient stravaClient;

    @Value("${endomondo.archive.root}")
    private String archiveRootProperty;

    @Value("${endomondo.photo-report.output-directory}")
    private String photoReportOutputDirectory;

    public MigrationReviewController(MigrationExecutor executor, MigrationLedger ledger,
                                      WorkoutPhotoResolver photoResolver, StravaTokenStore stravaTokenStore,
                                      StravaClient stravaClient) {
        this.executor = executor;
        this.ledger = ledger;
        this.photoResolver = photoResolver;
        this.stravaTokenStore = stravaTokenStore;
        this.stravaClient = stravaClient;
    }

    /** {@code @Value} fields aren't populated outside a Spring context; tests set them directly. */
    void setArchiveRootProperty(String archiveRootProperty) {
        this.archiveRootProperty = archiveRootProperty;
    }

    void setPhotoReportOutputDirectory(String photoReportOutputDirectory) {
        this.photoReportOutputDirectory = photoReportOutputDirectory;
    }

    /**
     * Jumps to the first workout with no durable decision yet. DONE, SKIPPED, and FAILED
     * all count as decided here, matching {@code hasDecision} on the per-workout view —
     * a FAILED workout already shows its flag and a Next button, so this must skip past it
     * too, or every fresh visit to this entry point would bounce back to the first-ever
     * failure forever instead of resuming where the review actually left off.
     */
    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView start(ModelAndView modelAndView) {

        Path archiveRoot = Path.of(archiveRootProperty);
        if (!Files.isDirectory(archiveRoot)) {
            return workoutView(modelAndView, null, 0, 0, archiveRoot);
        }

        List<ResolvedWorkout> actionable = actionable(archiveRoot);
        for (int i = 0; i < actionable.size(); i++) {
            LedgerStatus status = ledger.find(actionable.get(i).basename()).map(LedgerEntry::status).orElse(null);
            if (status == null || status == LedgerStatus.PENDING) {
                modelAndView.setViewName("redirect:/migration/review/" + i);
                return modelAndView;
            }
        }

        modelAndView.setViewName("redirect:/migration/review/summary");
        return modelAndView;

    }

    @RequestMapping(value = "/{index}", method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView, @PathVariable int index) {

        Path archiveRoot = Path.of(archiveRootProperty);
        if (!Files.isDirectory(archiveRoot)) {
            return workoutView(modelAndView, null, 0, 0, archiveRoot);
        }

        List<ResolvedWorkout> actionable = actionable(archiveRoot);
        if (index < 0 || index >= actionable.size()) {
            modelAndView.setViewName("redirect:/migration/review/summary");
            return modelAndView;
        }

        return workoutView(modelAndView, actionable.get(index), index, actionable.size(), archiveRoot);

    }

    @RequestMapping(value = "/{index}/migrate", method = RequestMethod.POST)
    public ModelAndView migrate(ModelAndView modelAndView, @PathVariable int index) {

        Path archiveRoot = Path.of(archiveRootProperty);
        List<ResolvedWorkout> actionable = actionable(archiveRoot);
        if (index >= 0 && index < actionable.size()) {
            // Deliberately no isDone()/isSkipped() check: a click here is always an
            // explicit request, including a re-migrate of an already-decided workout.
            executor.migrateOne(actionable.get(index));
        }

        modelAndView.setViewName("redirect:/migration/review/" + index);
        return modelAndView;

    }

    @RequestMapping(value = "/{index}/skip", method = RequestMethod.POST)
    public ModelAndView skip(ModelAndView modelAndView, @PathVariable int index) {

        Path archiveRoot = Path.of(archiveRootProperty);
        List<ResolvedWorkout> actionable = actionable(archiveRoot);
        if (index >= 0 && index < actionable.size()) {
            ResolvedWorkout workout = actionable.get(index);
            ledger.markSkipped(workout.basename(), workout.action());
        }

        modelAndView.setViewName("redirect:/migration/review/" + (index + 1));
        return modelAndView;

    }

    @RequestMapping(value = "/stop", method = RequestMethod.POST)
    public ModelAndView stop(ModelAndView modelAndView) {
        modelAndView.setViewName("redirect:/migration/review/summary");
        return modelAndView;
    }

    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    public ModelAndView summary(ModelAndView modelAndView) {

        modelAndView.setViewName("migration/review-summary");

        Path archiveRoot = Path.of(archiveRootProperty);
        if (!Files.isDirectory(archiveRoot)) {
            modelAndView.addObject("archivePresent", false);
            modelAndView.addObject("archiveRoot", archiveRootProperty);
            return modelAndView;
        }

        List<ResolvedWorkout> actionable = actionable(archiveRoot);
        int uploaded = 0;
        int manual = 0;
        int skippedByUser = 0;
        int failed = 0;
        int remaining = 0;
        List<String> failures = new ArrayList<>();

        for (ResolvedWorkout workout : actionable) {
            Optional<LedgerEntry> entry = ledger.find(workout.basename());
            if (entry.isEmpty() || entry.get().status() == LedgerStatus.PENDING) {
                remaining++;
                continue;
            }
            switch (entry.get().status()) {
                case DONE -> {
                    if (workout.action() == PlannedAction.UPLOAD_TCX) {
                        uploaded++;
                    } else {
                        manual++;
                    }
                }
                case SKIPPED -> skippedByUser++;
                case FAILED -> {
                    failed++;
                    failures.add(workout.basename() + ": " + entry.get().reason());
                }
                case PENDING -> remaining++;
            }
        }

        modelAndView.addObject("archivePresent", true);
        modelAndView.addObject("total", actionable.size());
        modelAndView.addObject("uploaded", uploaded);
        modelAndView.addObject("manualCreated", manual);
        modelAndView.addObject("skippedByUser", skippedByUser);
        modelAndView.addObject("failed", failed);
        modelAndView.addObject("remaining", remaining);
        modelAndView.addObject("failures", failures);

        return modelAndView;

    }

    private static String formatDuration(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private static String formatDistance(double km) {
        return String.format(Locale.ROOT, "%.1f", km);
    }

    /**
     * "{name} ({id})", e.g. "Trek Checkpoint (b18387038)" — falls back to the bare id if
     * the gear's name can't be looked up (a second, independent Strava call from the
     * activity read above; its failure doesn't invalidate the id we already have).
     */
    private String gearDisplay(String gearId) {
        try {
            StravaGear gear = stravaClient.getGear(gearId);
            return gear.name() == null || gear.name().isBlank() ? gearId : gear.name() + " (" + gearId + ")";
        } catch (StravaApiException e) {
            return gearId;
        }
    }

    private List<ResolvedWorkout> actionable(Path archiveRoot) {
        return executor.resolveAll(archiveRoot).stream()
                .filter(workout -> workout.action() != PlannedAction.SKIP)
                .toList();
    }

    private ModelAndView workoutView(ModelAndView modelAndView, ResolvedWorkout workout, int index, int total,
                                      Path archiveRoot) {

        modelAndView.setViewName("migration/review");
        modelAndView.addObject("archiveRoot", archiveRootProperty);
        modelAndView.addObject("archivePresent", Files.isDirectory(archiveRoot));
        modelAndView.addObject("stravaConnected", stravaTokenStore.load().isPresent());

        if (workout == null) {
            return modelAndView;
        }

        modelAndView.addObject("workout", workout);
        modelAndView.addObject("index", index);
        modelAndView.addObject("position", index + 1);
        modelAndView.addObject("total", total);
        modelAndView.addObject("hasNext", index + 1 < total);
        modelAndView.addObject("hasPrevious", index > 0);
        modelAndView.addObject("isUpload", workout.action() == PlannedAction.UPLOAD_TCX);
        modelAndView.addObject("formattedDistance", workout.distanceKm() == null ? null : formatDistance(workout.distanceKm()));
        modelAndView.addObject("formattedDuration", workout.durationS() == null ? null : formatDuration(workout.durationS()));

        Optional<LedgerEntry> entry = ledger.find(workout.basename());
        modelAndView.addObject("ledgerEntry", entry.orElse(null));
        boolean isDone = entry.map(e -> e.status() == LedgerStatus.DONE).orElse(false);
        modelAndView.addObject("isDone", isDone);
        modelAndView.addObject("isFailed", entry.map(e -> e.status() == LedgerStatus.FAILED).orElse(false));
        modelAndView.addObject("isSkipped", entry.map(e -> e.status() == LedgerStatus.SKIPPED).orElse(false));
        modelAndView.addObject("hasDecision", entry.map(e -> e.status() != LedgerStatus.PENDING).orElse(false));

        if (isDone && stravaTokenStore.load().isPresent()) {
            try {
                StravaActivity current = stravaClient.getActivity(entry.get().activityId());
                String gearId = current.gearId();
                modelAndView.addObject("currentGearDisplay", gearId == null || gearId.isBlank() ? null : gearDisplay(gearId));
                modelAndView.addObject("gearLookupFailed", false);
            } catch (StravaApiException e) {
                modelAndView.addObject("gearLookupFailed", true);
            }
        }

        Path photoReportRoot = Path.of(photoReportOutputDirectory).toAbsolutePath().normalize();
        Path photoCopiesDirectory = photoReportRoot.resolve("photos");
        List<PhotoPreview> photos = workout.jsonFile() == null ? List.of()
                : photoResolver.resolve(archiveRoot, workout.jsonFile(), photoCopiesDirectory).stream()
                        .map(path -> toPreview(path, photoReportRoot))
                        .toList();
        modelAndView.addObject("photos", photos);

        return modelAndView;

    }

    /**
     * {@code url} is servable because {@link pl.kiraga.endomondoexportparser.configuration.GeneralConfiguration}
     * maps {@code /photo-report/**} straight onto {@code endomondo.photo-report.output-directory} —
     * the same directory {@link #photoReportOutputDirectory} points at here, so a copy's
     * URL is just that prefix plus its path relative to that root.
     */
    private PhotoPreview toPreview(Path copy, Path photoReportRoot) {
        Path absolute = copy.toAbsolutePath().normalize();
        String relative = photoReportRoot.relativize(absolute).toString().replace('\\', '/');
        return new PhotoPreview(absolute.toString(), "/photo-report/" + relative);
    }

    /** A geotagged photo copy as shown on the review screen: a viewable thumbnail plus a copyable local path. */
    public record PhotoPreview(String localPath, String url) {
    }

}
