package pl.kiraga.endomondoexportparser.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import pl.kiraga.endomondoexportparser.controller.BaseControllerPrePostInterceptor;

import java.util.Locale;

// @EnableWebMvc removed at the Boot 2.7 hop: it disabled Boot's MVC autoconfiguration and,
// since Spring Framework 5.3, DelegatingWebMvcConfiguration registers its own localeResolver
// bean, colliding with the one below. Boot's autoconfiguration honors this WebMvcConfigurer.
@Configuration
public class GeneralConfiguration implements WebMvcConfigurer {

    // Message source: Spring Boot's autoconfigured MessageSource (UTF-8 by default),
    // basenames configured via spring.messages.basename. The previous hand-rolled
    // ResourceBundleMessageSource read the UTF-8 bundles as ISO-8859-1, garbling
    // Polish diacritics.

    @Bean
    public LocaleResolver localeResolver() {
        //SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        CookieLocaleResolver localeResolver = new CookieLocaleResolver();
        localeResolver.setDefaultLocale(Locale.US);
        return localeResolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("lang");
        return localeChangeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        registry.addInterceptor(new BaseControllerPrePostInterceptor());
    }

}
