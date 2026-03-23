package com.mapin.domain.recommendation;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
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
    @DisplayName("coreKeywords 겹치는 콘텐츠를 후보로 반환한다")
    void returnsCandidatesWithSharedCoreKeywords() {
        Content source = buildContent("politics");
        source.updatePerspective("politics", null, null, null, List.of("정치", "정책"), null, null, false);

        Content candidate = buildContent("politics");
        candidate.updatePerspective("politics", null, null, null, List.of("정치"), null, null, false);

        when(contentRepository.findByCoreKeywordsOverlap(anyString(), any()))
                .thenReturn(List.of(candidate));

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).containsExactly(candidate);
    }

    @Test
    @DisplayName("coreKeywords가 null이면 빈 리스트를 반환한다")
    void returnsEmptyListWhenCoreKeywordsIsNull() {
        Content source = buildContent(null);

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).isEmpty();
        verify(contentRepository, never()).findByCoreKeywordsOverlap(any(), any());
    }

    @Test
    @DisplayName("coreKeywords 겹치는 콘텐츠가 없으면 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoCandidates() {
        Content source = buildContent("economy");
        source.updatePerspective("economy", null, null, null, List.of("경제"), null, null, false);

        when(contentRepository.findByCoreKeywordsOverlap(anyString(), any()))
                .thenReturn(List.of());

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).isEmpty();
    }
}
