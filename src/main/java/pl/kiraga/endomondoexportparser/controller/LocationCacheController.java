package pl.kiraga.endomondoexportparser.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.model.CachedCoordinate;
import pl.kiraga.endomondoexportparser.model.LocationGroup;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.service.LocationCache;
import pl.kiraga.endomondoexportparser.service.LocationCacheCountryBackfillService;
import pl.kiraga.endomondoexportparser.service.StravaTokenStore;

/**
 * Shows what the location cache holds — every distinct place it has resolved, as a
 * suburb/locality/country summary, with the coordinates that map to it — so place-lookup
 * quality can be spot-checked without opening the cache file by hand. Opening the page
 * triggers no reverse-geocoding lookup; only the explicit backfill action does. Summaries
 * are rendered here rather than in the template because {@code PlaceDescription} is not
 * itself locale-aware.
 */
@Controller
@RequestMapping("/location-cache")
public class LocationCacheController extends BaseController {

    private final LocationCache locationCache;
    private final LocationCacheCountryBackfillService countryBackfill;
    private final StravaTokenStore stravaTokenStore;

    public LocationCacheController(LocationCache locationCache,
                                    LocationCacheCountryBackfillService countryBackfill,
                                    StravaTokenStore stravaTokenStore) {
        this.locationCache = locationCache;
        this.countryBackfill = countryBackfill;
        this.stravaTokenStore = stravaTokenStore;
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView) {
        modelAndView.setViewName("location-cache");
        modelAndView.addObject("groups", groupsByPlace());
        modelAndView.addObject("stravaConnected", stravaTokenStore.load().isPresent());
        return modelAndView;
    }

    @RequestMapping(value = "/backfill-countries", method = RequestMethod.POST)
    public ModelAndView backfillCountries(ModelAndView modelAndView) {

        List<String> errorMessages = new ArrayList<>();
        List<String> infoMessages = new ArrayList<>();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);

        int updated = countryBackfill.backfillMissingCountries();
        infoMessages.add(updated == 0
                ? message("infoMessage.locationCache.nothingToBackfill")
                : message("infoMessage.locationCache.countriesBackfilled", updated));

        return view(modelAndView);

    }

    private List<LocationGroup> groupsByPlace() {

        Locale displayLocale = LocaleContextHolder.getLocale();

        Map<String, List<CachedCoordinate>> byPlace = new TreeMap<>();
        for (Map.Entry<String, PlaceDescription> entry : locationCache.entries().entrySet()) {
            String[] parts = entry.getKey().split(",");
            byPlace.computeIfAbsent(entry.getValue().summary(displayLocale), place -> new ArrayList<>())
                    .add(new CachedCoordinate(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
        }

        List<LocationGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<CachedCoordinate>> group : byPlace.entrySet()) {
            group.getValue().sort(Comparator.<CachedCoordinate>comparingDouble(CachedCoordinate::latitude)
                    .thenComparingDouble(CachedCoordinate::longitude));
            groups.add(new LocationGroup(group.getKey(), List.copyOf(group.getValue())));
        }

        return groups;

    }

}
