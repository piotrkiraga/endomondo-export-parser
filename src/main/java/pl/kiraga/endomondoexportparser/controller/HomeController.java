package pl.kiraga.endomondoexportparser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.service.StravaDictionaryService;

@Controller
@RequestMapping(value = {"/", "/home"})
public class HomeController extends BaseController {

    private final StravaDictionaryService stravaDictionary;

    public HomeController(StravaDictionaryService stravaDictionary) {
        this.stravaDictionary = stravaDictionary;
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ModelAndView view(ModelAndView modelAndView) {
        modelAndView.setViewName("home");
        modelAndView.addObject("athlete", stravaDictionary.current().orElse(null));
        return modelAndView;
    }

}
