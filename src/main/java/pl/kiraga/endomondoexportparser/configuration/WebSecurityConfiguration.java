package pl.kiraga.endomondoexportparser.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Login is disabled for now (2026-07-21, at the user's request): the prior HTTP Basic
 * setup guarded every page with hardcoded placeholder credentials ("user"/"password",
 * "admin"/"admin") that were never real security, just a confusing browser popup on a
 * single-user localhost app. Revisit with a real scheme (or leave disabled, since this
 * app never leaves localhost) if that changes.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();

    }

}
