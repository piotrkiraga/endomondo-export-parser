package pl.kiraga.endomondoexportparser.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        auth
                .inMemoryAuthentication()
                .withUser("user")
                .password(encoder.encode("password"))
                .roles("USER")
                .and()
                .withUser("admin")
                .password(encoder.encode("admin"))
                .roles("USER", "ADMIN");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        String[] AUTHENTICATION_NOT_REQUIRED = {
                "/",
                "/error",
                "/home",
                "/upload",
                "/upload/process"
        };

        http

                .authorizeRequests()
                .antMatchers(AUTHENTICATION_NOT_REQUIRED).permitAll()

                .and()
                .authorizeRequests()
                .anyRequest().authenticated()

                .and()
                .httpBasic();

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

    }

}
