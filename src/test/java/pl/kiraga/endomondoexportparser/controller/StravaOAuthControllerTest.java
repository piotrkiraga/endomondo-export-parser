package pl.kiraga.endomondoexportparser.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No `STRAVA_CLIENT_ID` is configured for this test class (the default, un-overridden
 * environment), which is deliberately what makes the "missing credentials" and
 * "exchange fails" paths exercisable without a real network call: {@code StravaClient}
 * checks for credentials before ever calling out. See {@link StravaConnectRedirectTest}
 * for the happy-path redirect, which needs a client id configured.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class StravaOAuthControllerTest {

    // Must match StravaOAuthController.STATE_SESSION_KEY.
    private static final String STATE_SESSION_KEY = "strava_oauth_state";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void connectWithoutClientIdRedirectsWithAnErrorInstead() throws Exception {
        mockMvc.perform(get("/strava/connect").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/migration/photo-report"))
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "STRAVA_CLIENT_ID is not set; export it as an environment variable and restart the app")));
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
    void callbackWithNoMatchingSessionStateIsRejected() throws Exception {
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "unexpected").with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava sign-in could not be verified (state mismatch); please try connecting again")));
    }

    @Test
    void callbackWithNoCodeIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(STATE_SESSION_KEY, "expected-state");

        mockMvc.perform(get("/strava/callback").param("state", "expected-state").session(session).with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava did not send back an authorization code")));
    }

    @Test
    void callbackWithMatchingStateAttemptsExchangeAndReportsTheFailureWithoutCredentials() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(STATE_SESSION_KEY, "expected-state");

        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "expected-state")
                        .session(session).with(user("piotr")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Could not complete the Strava connection: \"STRAVA_CLIENT_ID and STRAVA_CLIENT_SECRET environment variables must be set\"")));
    }

    @Test
    void stateIsConsumedAfterOneCallbackAttempt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(STATE_SESSION_KEY, "expected-state");

        // First attempt consumes the state (fails on missing credentials, as above).
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "expected-state")
                .session(session).with(user("piotr")));

        // A second attempt with the same session must now see a state mismatch (already removed).
        mockMvc.perform(get("/strava/callback").param("code", "abc").param("state", "expected-state")
                        .session(session).with(user("piotr")))
                .andExpect(flash().attribute("errorMessages", hasItem(
                        "Strava sign-in could not be verified (state mismatch); please try connecting again")));
    }

}
