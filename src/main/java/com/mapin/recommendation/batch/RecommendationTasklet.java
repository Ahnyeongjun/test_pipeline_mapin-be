package com.mapin.recommendation.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeSearchClient;
import com.mapin.recommendation.client.RecommendationStrategy;
import com.mapin.recommendation.domain.ContentRecommendation;
import com.mapin.recommendation.domain.ContentRecommendationRepository;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RecommendationTasklet implements Tasklet {

    private static final int RECOMMEND_LIMIT = 5;
    private static final int FALLBACK_THRESHOLD = 1;

    private final ContentRepository contentRepository;
    private final ContentRecommendationRepository recommendationRepository;
    private final Map<String, RecommendationStrategy> strategies;
    private final YoutubeSearchClient youtubeSearchClient;
    private final String strategyName;

    public RecommendationTasklet(
            ContentRepository contentRepository,
            ContentRecommendationRepository recommendationRepository,
            Map<String, RecommendationStrategy> strategies,
            YoutubeSearchClient youtubeSearchClient,
            @Value("${pipeline.recommendation.strategy:db}") String strategyName) {
        this.contentRepository = contentRepository;
        this.recommendationRepository = recommendationRepository;
        this.strategies = strategies;
        this.youtubeSearchClient = youtubeSearchClient;
        this.strategyName = strategyName;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long contentId = contribution.getStepExecution().getJobParameters().getLong("contentId");

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        RecommendationStrategy strategy = resolveStrategy();
        List<Content> candidates = strategy.recommend(content, RECOMMEND_LIMIT);

        if (candidates.size() < FALLBACK_THRESHOLD) {
            log.info("[Recommendation] 후보 부족({}개), YouTube 검색 fallback contentId={}", candidates.size(), contentId);
            tryFallbackSearch(content);
            candidates = strategy.recommend(content, RECOMMEND_LIMIT);
        }

        saveRecommendations(contentId, candidates);

        log.info("[Recommendation] contentId={} strategy={} count={}",
                contentId, strategyName, candidates.size());

        return RepeatStatus.FINISHED;
    }

    private RecommendationStrategy resolveStrategy() {
        String beanName = strategyName + "Recommendation";
        RecommendationStrategy strategy = strategies.get(beanName);
        if (strategy == null) {
            log.warn("[Recommendation] 전략 빈 없음: {}, db 전략으로 fallback", beanName);
            strategy = strategies.get("dbRecommendation");
        }
        return strategy;
    }

    private void tryFallbackSearch(Content content) {
        if (content.getCategory() == null) {
            return;
        }
        try {
            List<String> videoIds = youtubeSearchClient.searchVideoIds(content.getCategory(), 10);
            log.info("[Recommendation] fallback 검색 결과 category={} videoIds={}", content.getCategory(), videoIds);
        } catch (Exception e) {
            log.warn("[Recommendation] fallback 검색 실패: {}", e.getMessage());
        }
    }

    private void saveRecommendations(Long sourceContentId, List<Content> candidates) {
        recommendationRepository.deleteBySourceContentId(sourceContentId);

        List<ContentRecommendation> recommendations = candidates.stream()
                .map(c -> ContentRecommendation.builder()
                        .sourceContentId(sourceContentId)
                        .targetContentId(c.getId())
                        .strategy(strategyName)
                        .build())
                .toList();

        recommendationRepository.saveAll(recommendations);
    }
}
