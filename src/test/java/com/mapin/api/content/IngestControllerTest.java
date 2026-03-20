package com.mapin.api.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapin.global.exception.GlobalExceptionHandler;
import com.mapin.infra.youtube.YoutubeUrlParser;
import com.mapin.api.content.dto.IngestRequest;
import com.mapin.domain.content.service.IngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class IngestControllerTest {

    @Mock private IngestService ingestService;
    @Mock private YoutubeUrlParser youtubeUrlParser;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        IngestController controller = new IngestController(ingestService, youtubeUrlParser);
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
        when(ingestService.ingest(eq(canonicalUrl), eq("USER"))).thenReturn(1L);

        mockMvc.perform(post("/api/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IngestRequest(canonicalUrl))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.canonicalUrl").value(canonicalUrl))
                .andExpect(jsonPath("$.contentId").value(1));
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
