package pl.kiraga.endomondoexportparser.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Credentials are forced blank here (unlike {@link StravaConnectRedirectTest}), which is
 * deliberately what makes the "missing credentials" and "exchange fails" paths exercisable
 * without a real network call: {@code StravaClient} checks for credentials before ever
 * calling out. This must be forced rather than left to the ambient environment — a
 * developer machine with real STRAVA_CLIENT_ID/SECRET exported (e.g. for a manual OAuth
 * smoke test) would otherwise make this class attempt a real Strava call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"STRAVA_CLIENT_ID=", "STRAVA_CLIENT_SECRET="})
public class StravaOAuthControllerTest {

    // Must match StravaOAuthController.STATE_COOKIE_NAME/RETURN_COOKIE_NAME.
    private static final String STATE_COOKIE_NAME = "strava_oauth_state";
    private static final String RETURN_COOKIE_NAME = "strava_oauth_return";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void connectWithoutClientIdRedirectsWithAnErrorInstead() throws Exception {
        mockMvc.perform(get("/strava/connect").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/migration/photo-report"))
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "STRAVA_CLIENT_ID is not set; copy application-local.properties.example to application-local.properties and fill it in (or set STRAVA_CLIENT_ID/STRAVA_CLIENT_SECRET as environment variables instead), then restart the app")));
    }

    @Test
    void callbackWithAnErrorParamIsReportedWithoutTouchingTheState() throws Exception {
        mockMvc.perform(get("/strava/callback").param("error", "access_denied").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/migration/photo-report"))
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava did not authorize this app: \"access_denied\"")));
    }

    @Test
    void callbackWithNoReturnCookieRedirectsToTheDefaultPage() throws Exception {
        mockMvc.perform(get("/strava/callback").param("error", "access_denied").with(user("piotr")))
                .andExpect(redirectedUrl("/migration/photo-report"));
    }

    @Test
    void callbackWithAReturnCookieRedirectsBackToWhereTheUserStarted() throws Exception {
        mockMvc.perform(get("/strava/callback").param("error", "access_denied")
                        .cookie(new Cookie(RETURN_COOKIE_NAME, "/migration/strava-dictionary")).with(user("piotr")))
                .andExpect(redirectedUrl("/migration/strava-dictionary"));
    }

    @Test
    void callbackWithNoMatchingStateIsRejected() throws Exception {
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "unexpected").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava sign-in could not be verified (state mismatch); please try connecting again")));
    }

    @Test
    void callbackWithNoCodeIsRejected() throws Exception {
        mockMvc.perform(get("/strava/callback").param("state", "expected-state")
                        .cookie(new Cookie(STATE_COOKIE_NAME, "expected-state")).with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava did not send back an authorization code")));
    }

    @Test
    void callbackWithMatchingStateAttemptsExchangeAndReportsTheFailureWithoutCredentials() throws Exception {
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "expected-state")
                        .cookie(new Cookie(STATE_COOKIE_NAME, "expected-state")).with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Could not complete the Strava connection: \"STRAVA_CLIENT_ID and STRAVA_CLIENT_SECRET environment variables must be set\"")));
    }

    @Test
    void stateIsConsumedAfterOneCallbackAttempt() throws Exception {

        // First attempt consumes the cookie (fails on missing credentials, as above).
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE_NAME, "expected-state")).with(user("piotr")));

        // A second attempt with no cookie — the server cleared it in the first response, so a
        // real browser would no longer send it — must see a state mismatch.
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "expected-state")
                        .with(user("piotr")))
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava sign-in could not be verified (state mismatch); please try connecting again")));
    }

}
