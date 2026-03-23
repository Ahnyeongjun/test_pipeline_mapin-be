package com.mapin.domain.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTest {

    private Content buildContent(String source) {
        return Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc123")
                .platform("YOUTUBE")
                .externalContentId("abc123")
                .title("테스트 영상")
                .description("테스트 설명")
                .status("ACTIVE")
                .source(source)
                .build();
    }

    @Test
    @DisplayName("updatePerspective는 분석 결과 필드를 모두 업데이트한다")
    void updatePerspective() {
        Content content = buildContent("USER");

        content.updatePerspective(
                "politics", "pro", "government",
                List.of("금리", "한국은행", "통화정책"),
                List.of("금리", "한국은행"),
                "정부 정책 지지 입장의 영상", "neutral", true
        );

        assertThat(content.getCategory()).isEqualTo("politics");
        assertThat(content.getPerspectiveLevel()).isEqualTo("pro");
        assertThat(content.getPerspectiveStakeholder()).isEqualTo("government");
        assertThat(content.getKeywords()).containsExactly("금리", "한국은행", "통화정책");
        assertThat(content.getCoreKeywords()).containsExactly("금리", "한국은행");
        assertThat(content.getSummary()).isEqualTo("정부 정책 지지 입장의 영상");
        assertThat(content.getTone()).isEqualTo("neutral");
        assertThat(content.getIsOpinionated()).isTrue();
    }

    @Test
    @DisplayName("updateEmbedding은 임베딩 모델과 벡터ID를 업데이트한다")
    void updateEmbedding() {
        Content content = buildContent("USER");

        content.updateEmbedding("text-embedding-3-small", "vec-id-001");

        assertThat(content.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(content.getVectorId()).isEqualTo("vec-id-001");
    }

    @Test
    @DisplayName("source 필드는 USER/FALLBACK을 구분한다")
    void sourceField() {
        Content user = buildContent("USER");
        Content fallback = buildContent("FALLBACK");

        assertThat(user.getSource()).isEqualTo("USER");
        assertThat(fallback.getSource()).isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("isOpinionated가 false일 때도 정상 저장된다")
    void updatePerspectiveNotOpinionated() {
        Content content = buildContent("USER");

        content.updatePerspective("economy", "neutral", "public",
                List.of("경제"), List.of("경제"), "중립 영상", "formal", false);

        assertThat(content.getIsOpinionated()).isFalse();
    }

    @Test
    @DisplayName("prePersist는 createdAt과 updatedAt을 현재 시각으로 설정한다")
    void prePersistSetsTimestamps() {
        Content content = buildContent("USER");
        content.prePersist();

        assertThat(content.getCreatedAt()).isNotNull();
        assertThat(content.getUpdatedAt()).isNotNull();
        assertThat(content.getCreatedAt()).isEqualTo(content.getUpdatedAt());
    }

    @Test
    @DisplayName("preUpdate는 updatedAt을 갱신한다")
    void preUpdateRefreshesUpdatedAt() {
        Content content = buildContent("USER");
        content.prePersist();
        var before = content.getUpdatedAt();

        content.preUpdate();

        assertThat(content.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("updatePipelineStatus는 pipelineStatus를 업데이트한다")
    void updatePipelineStatus() {
        Content content = buildContent("USER");
        content.updatePipelineStatus("ANALYZED");

        assertThat(content.getPipelineStatus()).isEqualTo("ANALYZED");
    }
}
