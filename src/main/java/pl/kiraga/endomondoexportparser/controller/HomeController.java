package pl.kiraga.endomondoexportparser.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.dto.strava.StravaDictionarySnapshotDto;
import pl.kiraga.endomondoexportparser.exception.StravaApiException;
import pl.kiraga.endomondoexportparser.service.AppStatusResolver;
import pl.kiraga.endomondoexportparser.service.StravaDictionaryService;
import pl.kiraga.endomondoexportparser.service.StravaTokenStore;
import pl.kiraga.endomondoexportparser.util.DisplayTimeUtil;

/**
 * The app's one landing screen: it shows where things stand and carries the Strava
 * "dictionary" — athlete profile and gear names — that used to live on its own page.
 * The dictionary is (re)fetched on demand via one {@code GET /athlete} call, rather than
 * each gear name being looked up individually wherever it's needed (see
 * {@code ConfirmedGearResolver}). Explicit and manual, matching every other Strava call
 * in this app: nothing refreshes on its own.
 */
@Controller
@RequestMapping(value = {"/", "/home"})
public class HomeController extends BaseController {

    private final StravaDictionaryService stravaDictionary;
    private final StravaTokenStore stravaTokenStore;
    private final AppStatusResolver appStatusResolver;

    @Value("${endomondo.archive.root}")
    private String archiveRootProperty;

    public HomeController(StravaDictionaryService stravaDictionary, StravaTokenStore stravaTokenStore,
                           AppStatusResolver appStatusResolver) {
        this.stravaDictionary = stravaDictionary;
        this.stravaTokenStore = stravaTokenStore;
        this.appStatusResolver = appStatusResolver;
    }

    /** {@code @Value} fields aren't populated outside a Spring context; tests set them directly. */
    void setArchiveRootProperty(String archiveRootProperty) {
        this.archiveRootProperty = archiveRootProperty;
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView) {
        modelAndView.setViewName("home");
        addStatus(modelAndView);
        return modelAndView;
    }

    @RequestMapping(value = "/refresh", method = RequestMethod.POST)
    public ModelAndView refresh(ModelAndView modelAndView) {

        List<String> errorMessages = new ArrayList<>();
        List<String> infoMessages = new ArrayList<>();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);
        modelAndView.setViewName("home");

        try {
            stravaDictionary.refresh();
            infoMessages.add(message("infoMessage.stravaDictionary.refreshed"));
        } catch (StravaApiException e) {
            errorMessages.add(message("errorMessage.stravaDictionary.refreshFailed", e.getMessage()));
        }

        addStatus(modelAndView);
        return modelAndView;

    }

    private void addStatus(ModelAndView modelAndView) {
        modelAndView.addObject("stravaConnected", stravaTokenStore.load().isPresent());
        StravaDictionarySnapshotDto snapshot = stravaDictionary.current().orElse(null);
        modelAndView.addObject("snapshot", snapshot);
        modelAndView.addObject("refreshedAtDisplay", snapshot == null ? null : DisplayTimeUtil.of(snapshot.refreshedAt()));
        modelAndView.addObject("appStatus", appStatusResolver.resolve(Path.of(archiveRootProperty)));
    }

}
