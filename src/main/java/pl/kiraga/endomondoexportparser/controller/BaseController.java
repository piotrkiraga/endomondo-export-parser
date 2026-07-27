package pl.kiraga.endomondoexportparser.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ModelAttribute;

@Component
public class BaseController {

    protected final static Logger logger = LoggerFactory.getLogger(BaseController.class);

    @Autowired
    private MessageSource messageSource;

    /** {@code @Autowired} fields aren't populated outside a Spring context; tests set this directly. */
    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * The current request's path, for the "return to where you were" link on the Strava
     * connect/reconnect button ({@code fragments/strava-status.html}) — Thymeleaf's
     * {@code #request} expression utility object isn't available by default in this
     * Spring version, so this is the supported way to get it into a template.
     */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    protected String message(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    protected String message(String key, Object parameter) {
        return messageSource.getMessage(key, new Object[]{parameter}, LocaleContextHolder.getLocale());
    }

    protected String message(String key, Object[] parameters) {
        return messageSource.getMessage(key, parameters, LocaleContextHolder.getLocale());
    }

}
