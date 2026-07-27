package pl.kiraga.endomondoexportparser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.migration.DisplayTime;
import pl.kiraga.endomondoexportparser.migration.StravaApiException;
import pl.kiraga.endomondoexportparser.migration.StravaDictionary;
import pl.kiraga.endomondoexportparser.migration.StravaDictionarySnapshot;
import pl.kiraga.endomondoexportparser.migration.StravaTokenStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user (re)fetch Strava's rarely-changing "dictionary" data — athlete profile
 * and gear names — on demand, via one {@code GET /athlete} call, rather than each gear
 * name being looked up individually wherever it's needed (see {@code ConfirmedGearResolver}).
 * Explicit and manual, matching every other Strava call in this app: nothing refreshes
 * on its own.
 */
@Controller
@RequestMapping("/migration/strava-dictionary")
public class StravaDictionaryController extends BaseController {

    private final StravaDictionary stravaDictionary;
    private final StravaTokenStore stravaTokenStore;

    public StravaDictionaryController(StravaDictionary stravaDictionary, StravaTokenStore stravaTokenStore) {
        this.stravaDictionary = stravaDictionary;
        this.stravaTokenStore = stravaTokenStore;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView) {
        modelAndView.setViewName("migration/strava-dictionary");
        addStatus(modelAndView);
        return modelAndView;
    }

    @RequestMapping(value = "/refresh", method = RequestMethod.POST)
    public ModelAndView refresh(ModelAndView modelAndView) {

        List<String> errorMessages = new ArrayList<>();
        List<String> infoMessages = new ArrayList<>();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);
        modelAndView.setViewName("migration/strava-dictionary");

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
        StravaDictionarySnapshot snapshot = stravaDictionary.current().orElse(null);
        modelAndView.addObject("snapshot", snapshot);
        modelAndView.addObject("refreshedAtDisplay", snapshot == null ? null : DisplayTime.of(snapshot.refreshedAt()));
    }

}
