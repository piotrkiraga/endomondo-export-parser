package pl.kiraga.endomondoexportparser.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.migration.PhotoReport;
import pl.kiraga.endomondoexportparser.migration.PhotoReportGenerator;
import pl.kiraga.endomondoexportparser.migration.PlannedAction;
import pl.kiraga.endomondoexportparser.migration.StravaTokenStore;
import pl.kiraga.endomondoexportparser.migration.WorkoutReport;
import pl.kiraga.endomondoexportparser.migration.WorkoutReportGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets the user (re)generate the photo handout and workout preview reports on demand,
 * independently of any migration run — the photo report so photos can be browsed and
 * geotagged copies refreshed at any time, and the workout report so exactly what a
 * migration would send to Strava can be reviewed before any account is touched.
 */
@Controller
@RequestMapping("/migration")
public class MigrationController extends BaseController {

    private final PhotoReportGenerator photoReportGenerator;
    private final WorkoutReportGenerator workoutReportGenerator;
    private final StravaTokenStore stravaTokenStore;

    @Value("${endomondo.archive.root}")
    private String archiveRootProperty;

    @Value("${endomondo.photo-report.output-directory}")
    private String photoReportOutputDirectory;

    @Value("${endomondo.workout-report.output-directory}")
    private String workoutReportOutputDirectory;

    public MigrationController(PhotoReportGenerator photoReportGenerator, WorkoutReportGenerator workoutReportGenerator,
                                StravaTokenStore stravaTokenStore) {
        this.photoReportGenerator = photoReportGenerator;
        this.workoutReportGenerator = workoutReportGenerator;
        this.stravaTokenStore = stravaTokenStore;
    }

    @RequestMapping(value = "/photo-report", method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView) {
        modelAndView.setViewName("migration/photo-report");
        addStatus(modelAndView);
        return modelAndView;
    }

    @RequestMapping(value = "/photo-report/generate", method = RequestMethod.POST)
    public ModelAndView generate(ModelAndView modelAndView) {

        List<String> errorMessages = new ArrayList<>();
        List<String> infoMessages = new ArrayList<>();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);
        modelAndView.setViewName("migration/photo-report");

        Path archiveRoot = Path.of(archiveRootProperty);
        if (!Files.isDirectory(archiveRoot)) {
            errorMessages.add(message("errorMessage.migration.archiveMissing", archiveRootProperty));
            addStatus(modelAndView);
            return modelAndView;
        }

        // No activity ids yet: the ledger (task 4.2) does not exist. Every link reads
        // "pending migration" until the executor is built to supply real ones.
        PhotoReport report = photoReportGenerator.generate(
                archiveRoot, Path.of(photoReportOutputDirectory, "index.html"), Map.of());

        infoMessages.add(message("infoMessage.migration.reportGenerated", new Object[]{
                report.groups().size(), report.photoCount(), report.unmatchedPhotos().size()}));
        addStatus(modelAndView);

        return modelAndView;

    }

    @RequestMapping(value = "/workout-report", method = RequestMethod.GET)
    public ModelAndView workoutReportView(ModelAndView modelAndView) {
        modelAndView.setViewName("migration/workout-report");
        addStatus(modelAndView);
        return modelAndView;
    }

    @RequestMapping(value = "/workout-report/generate", method = RequestMethod.POST)
    public ModelAndView generateWorkoutReport(ModelAndView modelAndView) {

        List<String> errorMessages = new ArrayList<>();
        List<String> infoMessages = new ArrayList<>();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);
        modelAndView.setViewName("migration/workout-report");

        Path archiveRoot = Path.of(archiveRootProperty);
        if (!Files.isDirectory(archiveRoot)) {
            errorMessages.add(message("errorMessage.migration.archiveMissing", archiveRootProperty));
            addStatus(modelAndView);
            return modelAndView;
        }

        WorkoutReport report = workoutReportGenerator.generate(
                archiveRoot, Path.of(workoutReportOutputDirectory, "index.html"));

        infoMessages.add(message("infoMessage.migration.workoutReportGenerated", new Object[]{
                report.entries().size(), report.count(PlannedAction.SKIP)}));
        addStatus(modelAndView);

        return modelAndView;

    }

    private void addStatus(ModelAndView modelAndView) {
        modelAndView.addObject("archiveRoot", archiveRootProperty);
        modelAndView.addObject("archivePresent", Files.isDirectory(Path.of(archiveRootProperty)));
        modelAndView.addObject("reportPresent",
                Files.isRegularFile(Path.of(photoReportOutputDirectory, "index.html")));
        modelAndView.addObject("workoutReportPresent",
                Files.isRegularFile(Path.of(workoutReportOutputDirectory, "index.html")));
        modelAndView.addObject("stravaConnected", stravaTokenStore.load().isPresent());
    }

}
