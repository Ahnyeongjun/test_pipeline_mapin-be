package com.mapin.recommendation.client;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbRecommendationStrategyTest {

    @Mock private ContentRepository contentRepository;

    private DbRecommendationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DbRecommendationStrategy(contentRepository);
    }

    private Content buildContent(String category) {
        return Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc")
                .platform("YOUTUBE").externalContentId("abc")
                .title("영상").description("설명")
                .status("ACTIVE").source("USER").build();
    }

    @Test
    @DisplayName("같은 category의 다른 콘텐츠를 후보로 반환한다")
    void returnsCandidatesInSameCategory() {
        Content source = buildContent("politics");
        // category 설정 (updatePerspective 통해)
        source.updatePerspective("politics", null, null, null, null, null, null, false);

        Content candidate = buildContent("politics");
        candidate.updatePerspective("politics", null, null, null, null, null, null, false);

        when(contentRepository.findByCategoryAndIdNot("politics", source.getId()))
                .thenReturn(List.of(candidate));

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).containsExactly(candidate);
    }

    @Test
    @DisplayName("category가 null이면 빈 리스트를 반환한다")
    void returnsEmptyListWhenCategoryIsNull() {
        Content source = buildContent(null);

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).isEmpty();
        verify(contentRepository, never()).findByCategoryAndIdNot(any(), any());
    }

    @Test
    @DisplayName("같은 category의 다른 콘텐츠가 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoCandidates() {
        Content source = buildContent("economy");
        source.updatePerspective("economy", null, null, null, null, null, null, false);

        when(contentRepository.findByCategoryAndIdNot("economy", source.getId()))
                .thenReturn(List.of());

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).isEmpty();
    }
}
