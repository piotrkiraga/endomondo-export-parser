package pl.kiraga.endomondoexportparser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import pl.kiraga.endomondoexportparser.format.json.EndomondoJson;
import pl.kiraga.endomondoexportparser.service.EndomondoJsonParser;
import pl.kiraga.endomondoexportparser.service.InvalidWorkoutJsonException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping(value = "/upload")
public class UploadController extends BaseController {

    @Autowired
    private EndomondoJsonParser endomondoJsonParser;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public String view() {
        return "upload";
    }

    @RequestMapping(value = "/process", method = RequestMethod.POST)
    public ModelAndView process(ModelAndView modelAndView, @RequestParam("file") MultipartFile file) throws IOException {

        List errorMessages = new ArrayList();
        List infoMessages = new ArrayList();
        modelAndView.addObject("errorMessages", errorMessages);
        modelAndView.addObject("infoMessages", infoMessages);

        if (file != null && file.isEmpty()) {
            errorMessages.add(message("errorMessage.form.field.required.file"));
        }

        if (errorMessages.isEmpty()) {

            try {
                EndomondoJson endomondoJson = endomondoJsonParser.parse(file.getBytes());
                logger.debug("Processed file " + file + ": " + endomondoJson);
                infoMessages.add(message("infoMessage.form.processed", file.getOriginalFilename()));
            } catch (InvalidWorkoutJsonException e) {
                logger.debug("Exception occurred (handled, probably incorrect file format recognized): ", e);
                errorMessages.add(message("errorMessage.form.invalidJsonFormat", file.getOriginalFilename()));
            }

        }

        modelAndView.setViewName("upload");

        return modelAndView;

    }

}
