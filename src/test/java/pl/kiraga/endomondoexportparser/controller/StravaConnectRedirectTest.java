package pl.kiraga.endomondoexportparser.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A client id is configured here (unlike {@link StravaOAuthControllerTest}) to exercise the actual redirect. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "STRAVA_CLIENT_ID=test-client-id")
public class StravaConnectRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void connectRedirectsToStravaAuthorizeWithTheExpectedParameters() throws Exception {

        MvcResult result = mockMvc.perform(get("/strava/connect").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();

        assertTrue(location.startsWith("https://www.strava.com/oauth/authorize?"), location);
        assertTrue(location.contains("client_id=test-client-id"), location);
        assertTrue(location.contains("response_type=code"), location);
        assertTrue(location.contains("activity:write") || location.contains("activity%3Awrite"), location);
        assertTrue(location.contains("activity:read_all") || location.contains("activity%3Aread_all"), location);
        assertTrue(location.contains("profile:read_all") || location.contains("profile%3Aread_all"), location);
        assertTrue(location.contains("state="), location);
        assertTrue(location.contains("redirect_uri="), location);
    }

    @Test
    void connectWithAReturnParamRemembersItInACookie() throws Exception {

        MvcResult result = mockMvc.perform(get("/strava/connect")
                        .param("return", "/migration/strava-dictionary").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie returnCookie = result.getResponse().getCookie("strava_oauth_return");
        assertNotNull(returnCookie, "no strava_oauth_return cookie was set");
        assertEquals("/migration/strava-dictionary", returnCookie.getValue());
    }

    @Test
    void connectWithAProtocolRelativeReturnParamIsIgnored() throws Exception {

        MvcResult result = mockMvc.perform(get("/strava/connect")
                        .param("return", "//evil.example.com/phish").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertNull(result.getResponse().getCookie("strava_oauth_return"),
                "a scheme-relative return path must never be trusted into a cookie");
    }

}
