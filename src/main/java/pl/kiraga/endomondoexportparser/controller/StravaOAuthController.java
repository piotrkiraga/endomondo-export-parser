package pl.kiraga.endomondoexportparser.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pl.kiraga.endomondoexportparser.exception.StravaApiException;
import pl.kiraga.endomondoexportparser.service.StravaClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Hosts the two legs of Strava's OAuth authorization-code flow: {@code /strava/connect}
 * redirects to Strava's own authorize page, and Strava redirects the user's browser back
 * to {@code /strava/callback} with a code this app exchanges for tokens. Nothing here
 * runs except from an explicit click — matching "migration only starts from explicit
 * user action" (see strava-migration spec).
 * <p>
 * The CSRF-style {@code state} value round-trips in a short-lived HttpOnly cookie rather
 * than the HttpSession: this app runs as a single local user, and a session-backed state
 * doesn't survive a Spring Boot DevTools restart (which recreates the embedded servlet
 * container's session store) — a restart triggered by an unrelated classpath change while
 * the browser is away on Strava's authorize page produced a spurious "state mismatch" in
 * practice. A cookie set directly on the browser has no such dependency on server process
 * lifetime.
 */
@Controller
@RequestMapping("/strava")
public class StravaOAuthController extends BaseController {

    private static final String STATE_COOKIE_NAME = "strava_oauth_state";
    private static final String RETURN_COOKIE_NAME = "strava_oauth_return";
    private static final String DEFAULT_RETURN_PATH = "/migration/photo-report";

    private final StravaClient stravaClient;

    @Value("${STRAVA_CLIENT_ID:}")
    private String clientId;

    @Value("${endomondo.strava.redirect-uri}")
    private String redirectUri;

    public StravaOAuthController(StravaClient stravaClient) {
        this.stravaClient = stravaClient;
    }

    @RequestMapping(value = "/connect", method = RequestMethod.GET)
    public String connect(@RequestParam(name = "return", required = false) String returnTo,
                           HttpServletResponse response, RedirectAttributes redirectAttributes) {

        if (clientId == null || clientId.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessages",
                    List.of(message("errorMessage.strava.clientIdMissing")));
            return "redirect:" + DEFAULT_RETURN_PATH;
        }

        String state = UUID.randomUUID().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(STATE_COOKIE_NAME, state, Duration.ofMinutes(10)).toString());
        if (isSafeInternalPath(returnTo)) {
            response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(RETURN_COOKIE_NAME, returnTo, Duration.ofMinutes(10)).toString());
        }

        String authorizeUrl = UriComponentsBuilder.fromUriString("https://www.strava.com/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("approval_prompt", "auto")
                .queryParam("scope", "activity:write,activity:read_all,profile:read_all")
                .queryParam("state", state)
                .build()
                .toUriString();

        return "redirect:" + authorizeUrl;

    }

    @RequestMapping(value = "/callback", method = RequestMethod.GET)
    public String callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = STATE_COOKIE_NAME, required = false) String expectedState,
            @CookieValue(name = RETURN_COOKIE_NAME, required = false) String returnTo,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        List<String> errorMessages = new ArrayList<>();
        List<String> infoMessages = new ArrayList<>();

        // Both consumed on first use regardless of outcome, same as the session attribute this replaced.
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(STATE_COOKIE_NAME, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(RETURN_COOKIE_NAME, "", Duration.ZERO).toString());

        if (error != null) {
            errorMessages.add(message("errorMessage.strava.authorizationDenied", error));
        } else if (expectedState == null || !expectedState.equals(state)) {
            errorMessages.add(message("errorMessage.strava.stateMismatch"));
        } else if (code == null || code.isBlank()) {
            errorMessages.add(message("errorMessage.strava.codeMissing"));
        } else {
            try {
                stravaClient.exchangeAuthorizationCode(code);
                infoMessages.add(message("infoMessage.strava.connected"));
            } catch (StravaApiException e) {
                logger.debug("Strava authorization code exchange failed: ", e);
                errorMessages.add(message("errorMessage.strava.exchangeFailed", Objects.toString(e.getMessage(), "")));
            }
        }

        redirectAttributes.addFlashAttribute("errorMessages", errorMessages);
        redirectAttributes.addFlashAttribute("infoMessages", infoMessages);

        return "redirect:" + (isSafeInternalPath(returnTo) ? returnTo : DEFAULT_RETURN_PATH);

    }

    /**
     * {@code returnTo} round-trips through a cookie set by our own {@link #connect} on
     * our own domain — not through Strava — so this is a sanity check against a
     * malformed/tampered cookie, not a defense against Strava itself. Requiring a
     * same-app-relative path (starts with {@code /}, not protocol-relative {@code //})
     * is enough to rule out redirecting off this app.
     */
    private static boolean isSafeInternalPath(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//");
    }

    /**
     * Not marked {@code secure}: {@code endomondo.strava.redirect-uri} is a plain
     * {@code http://localhost} URL, and a Secure cookie would silently never be sent back
     * to it.
     */
    private static ResponseCookie stateCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/strava")
                .maxAge(maxAge)
                .build();
    }

}
