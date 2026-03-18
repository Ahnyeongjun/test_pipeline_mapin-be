package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentRecommendationResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContentRecommendationOrchestrator {

    private final ContentRepository contentRepository;
    private final DbRecommendationService dbRecommendationService;
    private final VectorRecommendationService vectorRecommendationService;
    private final FallbackCandidateExpansionService fallbackCandidateExpansionService;

    @Value("${pipeline.recommendation.strategy:db}")
    private String strategy;

    public List<ContentRecommendationResponse> recommend(Long sourceContentId, int topK) {
        Content source = contentRepository.findById(sourceContentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + sourceContentId));

        RecommendationStrategy recommendationStrategy = resolveStrategy();
        log.info("Recommendation strategy={} for contentId={}", strategy, sourceContentId);

        List<ContentRecommendationResponse> initialResults = recommendationStrategy.recommend(source, topK);

        if (initialResults.size() >= topK) {
            return initialResults;
        }

        log.info("Fallback triggered for contentId={}, initialResults={}, topK={}", sourceContentId, initialResults.size(), topK);
        fallbackCandidateExpansionService.expand(source);

        List<ContentRecommendationResponse> fallbackResults = recommendationStrategy.recommend(source, topK);
        log.info("Fallback complete for contentId={} -> {} results", sourceContentId, fallbackResults.size());
        return fallbackResults;
    }

    private RecommendationStrategy resolveStrategy() {
        return switch (strategy) {
            case "vector" -> vectorRecommendationService;
            default -> dbRecommendationService;
        };
    }
}
