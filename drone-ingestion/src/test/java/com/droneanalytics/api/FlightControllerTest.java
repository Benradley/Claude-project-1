package com.droneanalytics.api;

import com.droneanalytics.ingestion.SampleDataGenerator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FlightController.
 *
 * @WithMockUser is applied at class level so every test runs as the mock
 * user "user@test.com".  Session saving will be silently skipped (that user
 * doesn't exist in the test DB), which is the expected behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(username = "user@test.com")
class FlightControllerTest {

    @Autowired
    private MockMvc mvc;

    private static Path sampleDir;

    @BeforeAll
    static void generateSamples() throws Exception {
        sampleDir = Files.createTempDirectory("drone-api-test-");
        SampleDataGenerator.generateAll(sampleDir);
    }

    // ── Health (public — no auth needed) ─────────────────────────────────────

    @Test
    @Order(1)
    @WithMockUser   // override: health is public but @WithMockUser doesn't break it
    void healthEndpointReturns200() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("running")));
    }

    // ── /api/flights/analyze ──────────────────────────────────────────────────

    @Test
    @Order(2)
    void analyzeEndpointReturnJsonForDjiTxt() throws Exception {
        byte[] fileBytes = Files.readAllBytes(sampleDir.resolve("sample_dji.txt"));
        MockMultipartFile upload = new MockMultipartFile("file", "sample_dji.txt",
            "application/octet-stream", fileBytes);

        MvcResult result = mvc.perform(multipart("/api/flights/analyze").file(upload))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"droneType\"");
        assertThat(body).contains("\"healthy\"");
        assertThat(body).contains("\"summary\"");
        assertThat(body).contains("\"durationSeconds\"");
    }

    @Test @Order(3)
    void analyzeEndpointReturnJsonForArduPilotBin() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_ardupilot.bin"));
        MvcResult r = mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "sample_ardupilot.bin", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("\"pathOptimization\"");
    }

    @Test @Order(4)
    void analyzeEndpointReturnJsonForPx4Ulog() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_px4.ulg"));
        mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "sample_px4.ulg", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.droneType").isNotEmpty());
    }

    @Test @Order(5)
    void analyzeEndpointReturnJsonForMavlinkTlog() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_mavlink.tlog"));
        mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "sample_mavlink.tlog", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.durationSeconds").isNumber());
    }

    @Test @Order(6)
    void analyzeEndpointReturnJsonForParrotJson() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_parrot.json"));
        mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "sample_parrot.json", "application/json", bytes)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.battery").isMap());
    }

    @Test @Order(7)
    void analyzeEndpointReturnsBadRequestForEmptyFile() throws Exception {
        mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "empty.bin", "application/octet-stream", new byte[0])))
            .andExpect(status().isBadRequest());
    }

    // ── /api/flights/report/csv ───────────────────────────────────────────────

    @Test @Order(8)
    void csvEndpointReturnsCsvFile() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_dji.txt"));
        MvcResult r = mvc.perform(multipart("/api/flights/report/csv")
                .file(new MockMultipartFile("file", "sample_dji.txt", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("attachment")))
            .andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("# Drone Analytics Report");
        assertThat(r.getResponse().getContentAsString()).contains("timestamp_ms");
    }

    @Test @Order(9)
    void csvEndpointContainsDroneTypeMetadata() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_ardupilot.bin"));
        MvcResult r = mvc.perform(multipart("/api/flights/report/csv")
                .file(new MockMultipartFile("file", "sample_ardupilot.bin", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("# DroneType,");
    }

    // ── /api/flights/report/pdf ───────────────────────────────────────────────

    @Test @Order(10)
    void pdfEndpointReturnsPdfBytes() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_dji.txt"));
        MvcResult r = mvc.perform(multipart("/api/flights/report/pdf")
                .file(new MockMultipartFile("file", "sample_dji.txt", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString(".pdf")))
            .andReturn();
        byte[] pdf = r.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test @Order(11)
    void pdfEndpointWorksForAllFormats() throws Exception {
        String[] fnames = {"sample_dji.txt","sample_ardupilot.bin",
                           "sample_px4.ulg","sample_mavlink.tlog","sample_parrot.json"};
        for (String fname : fnames) {
            byte[] bytes = Files.readAllBytes(sampleDir.resolve(fname));
            MvcResult r = mvc.perform(multipart("/api/flights/report/pdf")
                    .file(new MockMultipartFile("file", fname, "application/octet-stream", bytes)))
                .andExpect(status().isOk())
                .andReturn();
            assertThat(new String(r.getResponse().getContentAsByteArray(), 0, 4))
                .as("PDF magic for %s", fname).isEqualTo("%PDF");
        }
    }

    // ── Full JSON shape ───────────────────────────────────────────────────────

    @Test @Order(12)
    void analyzeResponseContainsAllTopLevelFields() throws Exception {
        byte[] bytes = Files.readAllBytes(sampleDir.resolve("sample_dji.txt"));
        mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "sample_dji.txt", "application/octet-stream", bytes)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.droneType").isNotEmpty())
            .andExpect(jsonPath("$.healthy").isBoolean())
            .andExpect(jsonPath("$.statusLine").isString())
            .andExpect(jsonPath("$.summary.durationSeconds").isNumber())
            .andExpect(jsonPath("$.summary.totalDistanceM").isNumber())
            .andExpect(jsonPath("$.pathOptimization.efficiencyScore").isNumber())
            .andExpect(jsonPath("$.battery.rSquared").isNumber())
            .andExpect(jsonPath("$.airspaceRisk.overallRiskScore").isNumber())
            .andExpect(jsonPath("$.anomalies").isArray());
    }

    // ── Unauthenticated requests are rejected ─────────────────────────────────

    @Test @Order(13)
    void unauthenticatedAnalyzeReturns401() throws Exception {
        // No @WithMockUser on this method — override class-level annotation
        // by using Spring Security test support to clear the context
        mvc.perform(multipart("/api/flights/analyze")
                .file(new MockMultipartFile("file", "x.bin", "application/octet-stream", new byte[]{1,2,3}))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
            .andExpect(status().isUnauthorized());
    }
}
