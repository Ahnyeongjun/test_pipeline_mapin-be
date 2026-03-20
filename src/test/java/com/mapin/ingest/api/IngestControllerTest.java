package com.mapin.ingest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapin.common.api.GlobalExceptionHandler;
import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeUrlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class IngestControllerTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private Job ingestJob;
    @Mock private YoutubeUrlParser youtubeUrlParser;
    @Mock private ContentRepository contentRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        IngestController controller = new IngestController(jobLauncher, ingestJob, youtubeUrlParser, contentRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유효한 URL로 요청하면 202 Accepted와 IngestResponse를 반환한다")
    void validUrl_returns202() throws Exception {
        String canonicalUrl = "https://www.youtube.com/watch?v=abc123";
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("abc123");
        when(youtubeUrlParser.canonicalize("abc123")).thenReturn(canonicalUrl);
        when(jobLauncher.run(any(), any())).thenReturn(null);

        Content saved = Content.builder()
                .canonicalUrl(canonicalUrl)
                .platform("YOUTUBE").externalContentId("abc123")
                .title("영상").description("").status("ACTIVE").source("USER").build();
        when(contentRepository.findByCanonicalUrl(canonicalUrl)).thenReturn(Optional.of(saved));

        mockMvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IngestRequest(canonicalUrl))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.canonicalUrl").value(canonicalUrl));
    }

    @Test
    @DisplayName("url이 blank이면 400 Bad Request를 반환한다")
    void blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IngestRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("YoutubeUrlParser가 예외를 던지면 400 Bad Request를 반환한다")
    void invalidUrl_returns400() throws Exception {
        when(youtubeUrlParser.extractVideoId(anyString()))
                .thenThrow(new IllegalArgumentException("유효하지 않은 YouTube URL입니다."));

        mockMvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IngestRequest("https://not-youtube.com/video"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("유효하지 않은 YouTube URL입니다."));
    }
}
