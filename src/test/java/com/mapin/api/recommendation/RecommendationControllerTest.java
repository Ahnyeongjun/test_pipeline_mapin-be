package com.mapin.api.recommendation;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.domain.recommendation.ContentRecommendation;
import com.mapin.domain.recommendation.ContentRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock private ContentRepository contentRepository;
    @Mock private ContentRecommendationRepository recommendationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RecommendationController controller = new RecommendationController(
                contentRepository, recommendationRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.mapin.global.exception.GlobalExceptionHandler())
                .build();
    }

    private Content buildContent(Long id, String title, String category, String stakeholder) {
        Content content = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=vid" + id)
                .platform("YOUTUBE").externalContentId("vid" + id)
                .title(title).description("").status("ACTIVE").source("USER").build();
        content.updatePerspective(category, "pro", stakeholder,
                List.of(), List.of(), "요약", "neutral", false);
        try {
            var field = Content.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(content, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return content;
    }

    @Test
    @DisplayName("추천 목록 조회 성공 - 200 반환")
    void getRecommendationsSuccess() throws Exception {
        Content source = buildContent(1L, "정부 정책 지지 영상", "politics", "government");
        Content target = buildContent(2L, "노동자 입장 영상", "politics", "labor");

        when(contentRepository.findById(1L)).thenReturn(Optional.of(source));

        ContentRecommendation relation = ContentRecommendation.builder()
                .sourceContentId(1L).targetContentId(2L).score(2).strategy("db").build();
        when(recommendationRepository.findBySourceContentIdOrderByScoreDesc(1L))
                .thenReturn(List.of(relation));
        when(contentRepository.findAllById(anyList())).thenReturn(List.of(target));

        mockMvc.perform(get("/api/contents/1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("노동자 입장 영상"))
                .andExpect(jsonPath("$[0].category").value("politics"))
                .andExpect(jsonPath("$[0].perspectiveStakeholder").value("labor"))
                .andExpect(jsonPath("$[0].score").value(2));
    }

    @Test
    @DisplayName("추천 관계가 없으면 빈 배열 반환")
    void getRecommendationsEmpty() throws Exception {
        Content source = buildContent(1L, "영상", "politics", "government");
        when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(recommendationRepository.findBySourceContentIdOrderByScoreDesc(1L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/contents/1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠 ID면 404 반환")
    void notFoundWhenContentMissing() throws Exception {
        when(contentRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/contents/999/recommendations"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("여러 추천 결과를 정상적으로 직렬화한다")
    void multipleRecommendations() throws Exception {
        Content source = buildContent(1L, "원본", "politics", "government");
        Content target1 = buildContent(2L, "반대 영상1", "politics", "labor");
        Content target2 = buildContent(3L, "반대 영상2", "politics", "citizen");

        when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(recommendationRepository.findBySourceContentIdOrderByScoreDesc(1L)).thenReturn(
                List.of(
                        ContentRecommendation.builder().sourceContentId(1L).targetContentId(2L).score(2).strategy("db").build(),
                        ContentRecommendation.builder().sourceContentId(1L).targetContentId(3L).score(1).strategy("db").build()
                )
        );
        when(contentRepository.findAllById(anyList())).thenReturn(List.of(target1, target2));

        mockMvc.perform(get("/api/contents/1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].score").value(2))
                .andExpect(jsonPath("$[1].score").value(1));
    }
}
