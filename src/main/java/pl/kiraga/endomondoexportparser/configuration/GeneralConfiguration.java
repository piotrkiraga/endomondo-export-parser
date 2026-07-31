package pl.kiraga.endomondoexportparser.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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

    @Value("${endomondo.photo-report.output-directory}")
    private String photoReportOutputDirectory;

    @Value("${endomondo.workout-report.output-directory}")
    private String workoutReportOutputDirectory;

    /**
     * Serves the photo and workout reports straight off disk, at the same relative
     * paths they were written with, so each stays equally openable by double-clicking
     * it later with the app not running.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/photo-report/**")
                .addResourceLocations("file:" + photoReportOutputDirectory + "/");
        registry.addResourceHandler("/workout-report/**")
                .addResourceLocations("file:" + workoutReportOutputDirectory + "/");
    }

    /**
     * Before any explicit choice is remembered in the locale cookie, the default is
     * derived from the browser's {@code Accept-Language} header (via
     * {@code HttpServletRequest.getLocale()}, the servlet container's own best-match
     * resolution) rather than always English: Polish if the browser prefers it,
     * English otherwise, since those are the only two languages this app supports.
     * Only consulted when the header is actually present — {@code getLocale()} silently
     * falls back to the server JVM's own default locale when a request sends no
     * {@code Accept-Language} at all, which would make the app's language depend on
     * whatever machine happens to be running it rather than the browser. Once a user
     * picks a language explicitly via the nav bar's English/Polski links
     * ({@code ?lang=en}/{@code ?lang=pl}), the cookie takes over and this function is
     * never consulted again for that browser.
     */
    @Bean
    public LocaleResolver localeResolver() {
        //SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        CookieLocaleResolver localeResolver = new CookieLocaleResolver();
        localeResolver.setDefaultLocaleFunction(request -> {
            String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
            if (acceptLanguage == null || acceptLanguage.isBlank()) {
                return Locale.US;
            }
            return "pl".equalsIgnoreCase(request.getLocale().getLanguage()) ? Locale.forLanguageTag("pl") : Locale.US;
        });
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
