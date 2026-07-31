package pl.kiraga.endomondoexportparser.controller;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.model.CachedCoordinate;
import pl.kiraga.endomondoexportparser.model.PlaceDescription;
import pl.kiraga.endomondoexportparser.service.LocationCache;
import pl.kiraga.endomondoexportparser.service.StravaTokenStore;

/**
 * Shows what the location cache holds — every coordinate it has resolved and a
 * suburb/locality/country summary of the place it resolved to — so place-lookup quality
 * can be spot-checked without opening the cache file by hand. Read-only: nothing here
 * triggers a reverse-geocoding lookup. Summaries are rendered here rather than in the
 * template because {@code PlaceDescription} is not itself locale-aware.
 */
@Controller
@RequestMapping("/location-cache")
public class LocationCacheController extends BaseController {

    private final LocationCache locationCache;
    private final StravaTokenStore stravaTokenStore;

    public LocationCacheController(LocationCache locationCache, StravaTokenStore stravaTokenStore) {
        this.locationCache = locationCache;
        this.stravaTokenStore = stravaTokenStore;
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView) {
        modelAndView.setViewName("location-cache");
        modelAndView.addObject("entries", summariesByCoordinate());
        modelAndView.addObject("stravaConnected", stravaTokenStore.load().isPresent());
        return modelAndView;
    }

    private Map<CachedCoordinate, String> summariesByCoordinate() {

        Locale displayLocale = LocaleContextHolder.getLocale();

        Map<CachedCoordinate, String> byCoordinate = new LinkedHashMap<>();
        for (Map.Entry<String, PlaceDescription> entry : new TreeMap<>(locationCache.entries()).entrySet()) {
            String[] parts = entry.getKey().split(",");
            byCoordinate.put(
                    new CachedCoordinate(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])),
                    entry.getValue().summary(displayLocale));
        }

        return byCoordinate;

    }

}
