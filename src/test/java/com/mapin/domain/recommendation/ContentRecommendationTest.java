package com.mapin.domain.recommendation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentRecommendationTest {

    @Test
    @DisplayName("prePersist는 createdAt을 현재 시각으로 설정한다")
    void prePersistSetsCreatedAt() {
        ContentRecommendation rec = ContentRecommendation.builder()
                .sourceContentId(1L)
                .targetContentId(2L)
                .score(2)
                .strategy("db")
                .build();

        rec.prePersist();

        assertThat(rec.getCreatedAt()).isNotNull();
    }
}
