package pl.kiraga.endomondoexportparser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(value = {"/", "/home"})
public class HomeController extends BaseController {

    @RequestMapping(value = "", method = RequestMethod.GET)
    public String view() {
        return "home";
    }

}
