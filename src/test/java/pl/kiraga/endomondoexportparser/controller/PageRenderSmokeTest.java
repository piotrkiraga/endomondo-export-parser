package pl.kiraga.endomondoexportparser.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actually renders every page through the real Thymeleaf engine — none of the other
 * controller tests do this (they either inspect a {@code ModelAndView}'s model
 * programmatically, or use a {@code MockRestServiceServer}-backed rig that never touches
 * a template at all), which is exactly how a Thymeleaf expression error in a shared
 * fragment (e.g. {@code fragments/strava-status}, used by every page below) went
 * undetected by 224 otherwise-green tests until caught live. Blank Strava credentials,
 * same safety reasoning as {@link StravaOAuthControllerTest}: no real network call is
 * possible regardless of what's saved in the real {@code data/} directory on the machine
 * running this test. {@code endomondo.archive.root} is pointed at a path that can't
 * exist, so every page renders its "archive missing" state deterministically regardless
 * of whether the real archive happens to be present on the machine running this test —
 * {@code /migration/review}'s bare GET specifically redirects instead of rendering when
 * an archive *is* present (it jumps to the first workout), which would make this same
 * assertion flaky depending on that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "STRAVA_CLIENT_ID=", "STRAVA_CLIENT_SECRET=",
        "endomondo.archive.root=data/this-path-does-not-exist-for-tests"
})
public class PageRenderSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/home", "/upload",
            "/migration/photo-report", "/migration/workout-report",
            "/migration/review"
    })
    void pageRendersWithoutError(String path) throws Exception {
        mockMvc.perform(get(path).with(user("piotr")))
                .andExpect(status().isOk());
    }

}
