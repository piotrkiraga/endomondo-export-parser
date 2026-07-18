package pl.kiraga.endomondoexportparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// The former SecurityAutoConfiguration/ManagementWebSecurityAutoConfiguration excludes
// were dropped at the Boot 4 hop: both back off automatically because the app defines
// its own SecurityFilterChain and UserDetailsService in WebSecurityConfiguration.
@SpringBootApplication
public class EndomondoExportParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(EndomondoExportParserApplication.class, args);
    }

}
