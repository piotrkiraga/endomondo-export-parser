package pl.kiraga.endomondoexportparser.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

    private static final String[] AUTHENTICATION_NOT_REQUIRED = {
            "/",
            "/error",
            "/home",
            "/upload",
            "/upload/process",
            "/webjars/**",
            "/js/**"
    };

    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserDetails user = User.withUsername("user")
                .password(encoder.encode("password"))
                .roles("USER")
                .build();
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("admin"))
                .roles("USER", "ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(AUTHENTICATION_NOT_REQUIRED).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        /*
        http
                .csrf()
                .disable()
                .cors()
                .disable()
                .authorizeRequests()
                .antMatchers(AUTHENTICATION_NOT_REQUIRED).permitAll()
                .and()
                .authorizeRequests()
                .antMatchers(AUTHENTICATION_REQUIRED).authenticated()
                .antMatchers(AUTHENTICATION_REQUIRED).hasAnyAuthority(authorities())
                .and()
                .formLogin()
                .loginPage(LOGIN_URL)
                .loginProcessingUrl(LOGIN_PROCESSING_URL).permitAll()
                .failureUrl(LOGIN_URL)
                .and()
                .logout()
                .logoutUrl(LOGOUT_URL).permitAll();
        */

        return http.build();

    }

}
