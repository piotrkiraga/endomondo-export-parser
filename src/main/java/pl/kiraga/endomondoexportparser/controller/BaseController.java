package pl.kiraga.endomondoexportparser.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BaseController {

    protected final static Logger logger = LoggerFactory.getLogger(BaseController.class);

    @Autowired
    private MessageSource messageSource;

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
