package pl.kiraga.endomondoexportparser.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
public class UploadFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validWorkoutUploadRendersProcessedMessage() throws Exception {
        byte[] workout = getClass().getResourceAsStream("/fixtures/workout-manual.json").readAllBytes();

        mockMvc.perform(multipart("/upload/process")
                        .file(new MockMultipartFile("file", "workout-manual.json", "application/json", workout))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Submitted file processed successfully: &quot;workout-manual.json&quot;")));
    }

    @Test
    void trackedWorkoutUploadRendersFullSummary() throws Exception {
        byte[] workout = getClass().getResourceAsStream("/fixtures/workout-tracked.json").readAllBytes();

        mockMvc.perform(multipart("/upload/process")
                        .file(new MockMultipartFile("file", "workout-tracked.json", "application/json", workout))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Workout summary")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sample tracked ride")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CYCLING_SPORT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TRACK_MOBILE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2:09:45")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("34.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("33.9")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("not present in file"))));
    }

    @Test
    void manualWorkoutUploadMarksMissingMetricsAsAbsent() throws Exception {
        byte[] workout = getClass().getResourceAsStream("/fixtures/workout-manual.json").readAllBytes();

        mockMvc.perform(multipart("/upload/process")
                        .file(new MockMultipartFile("file", "workout-manual.json", "application/json", workout))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Workout summary")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sample manual walk")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("6.6")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not present in file")));
    }

    @Test
    void wrongShapeJsonRendersInvalidFormatMessage() throws Exception {
        mockMvc.perform(multipart("/upload/process")
                        .file(new MockMultipartFile("file", "not-a-workout.json", "application/json", "{}".getBytes()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Invalid JSON content in uploaded file: &quot;not-a-workout.json&quot;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Workout summary"))));
    }

}
