package com.mapin.common.domain;

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
                "정부 정책 지지 입장의 영상", "neutral", "low", true
        );

        assertThat(content.getCategory()).isEqualTo("politics");
        assertThat(content.getPerspectiveLevel()).isEqualTo("pro");
        assertThat(content.getPerspectiveStakeholder()).isEqualTo("government");
        assertThat(content.getKeywords()).containsExactly("금리", "한국은행", "통화정책");
        assertThat(content.getSummary()).isEqualTo("정부 정책 지지 입장의 영상");
        assertThat(content.getTone()).isEqualTo("neutral");
        assertThat(content.getBiasLevel()).isEqualTo("low");
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
                List.of("경제"), "중립 영상", "formal", "none", false);

        assertThat(content.getIsOpinionated()).isFalse();
    }
}
