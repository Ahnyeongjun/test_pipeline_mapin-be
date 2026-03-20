package com.mapin.domain.recommendation.service;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.youtube.YoutubeSearchClient;
import com.mapin.domain.content.event.FallbackIngestRequestedEvent;
import com.mapin.domain.recommendation.RecommendationStrategy;
import com.mapin.domain.recommendation.ContentRecommendation;
import com.mapin.domain.recommendation.ContentRecommendationRepository;
import com.mapin.domain.recommendation.event.ContentRecommendationFailedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RecommendationService {

    private static final int FALLBACK_THRESHOLD = 1;
    private static final String YOUTUBE_BASE_URL = "https://www.youtube.com/watch?v=";

    private final ContentRepository contentRepository;
    private final ContentRecommendationRepository recommendationRepository;
    private final Map<String, RecommendationStrategy> strategies;
    private final YoutubeSearchClient youtubeSearchClient;
    private final String strategyName;
    private final ApplicationEventPublisher eventPublisher;

    public RecommendationService(
            ContentRepository contentRepository,
            ContentRecommendationRepository recommendationRepository,
            Map<String, RecommendationStrategy> strategies,
            YoutubeSearchClient youtubeSearchClient,
            @Value("${pipeline.recommendation.strategy:db}") String strategyName,
            ApplicationEventPublisher eventPublisher) {
        this.contentRepository = contentRepository;
        this.recommendationRepository = recommendationRepository;
        this.strategies = strategies;
        this.youtubeSearchClient = youtubeSearchClient;
        this.strategyName = strategyName;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void recommend(Long contentId, String source) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        try {
            if ("FALLBACK".equals(source)) {
                linkFallbackToExistingUsers(content);
            } else {
                List<Content> candidates = getCandidatesWithFallback(content);
                saveRelations(content, candidates);
            }

            content.updatePipelineStatus("COMPLETED");
            contentRepository.save(content);
        } catch (Exception e) {
            log.error("[Recommendation][Saga] 추천 실패 contentId={}: {}", contentId, e.getMessage(), e);
            eventPublisher.publishEvent(
                    new ContentRecommendationFailedEvent(this, contentId, source, e.getMessage()));
            throw e;
        }
    }

    private List<Content> getCandidatesWithFallback(Content content) {
        RecommendationStrategy strategy = resolveStrategy();
        List<Content> candidates = strategy.getCandidates(content);
        List<Content> filtered = filterCandidates(content, candidates);

        if (filtered.size() < FALLBACK_THRESHOLD) {
            log.info("[Recommendation] 반대관점 후보 부족({}개), fallback 실행 contentId={}", filtered.size(), content.getId());
            tryFallbackSearch(content);
        }

        return filtered;
    }

    private List<Content> filterCandidates(Content source, List<Content> candidates) {
        Map<String, Content> bestByChannel = new LinkedHashMap<>();
        Map<String, Integer> bestScoreByChannel = new LinkedHashMap<>();

        for (Content target : candidates) {
            if (target.getPerspectiveStakeholder() == null) continue;

            int score = calculateScore(source, target);
            if (score == 0) continue;
            if (!hasTopicOverlap(source, target)) continue;

            String channel = target.getChannelTitle() != null
                    ? target.getChannelTitle()
                    : "unknown_" + target.getExternalContentId();

            if (!bestByChannel.containsKey(channel) || score > bestScoreByChannel.get(channel)) {
                bestByChannel.put(channel, target);
                bestScoreByChannel.put(channel, score);
            }
        }

        return new ArrayList<>(bestByChannel.values());
    }

    private boolean hasTopicOverlap(Content a, Content b) {
        List<String> kA = a.getKeywords();
        List<String> kB = b.getKeywords();
        if (kA == null || kA.isEmpty() || kB == null || kB.isEmpty()) {
            return true;
        }
        long overlap = kA.stream().filter(kB::contains).count();
        double ratio = (double) overlap / Math.min(kA.size(), kB.size());
        return ratio >= 0.2;
    }

    private void linkFallbackToExistingUsers(Content fallback) {
        if (fallback.getCategory() == null) return;

        List<Content> userContents = contentRepository.findByCategoryAndSourceAndIdNot(
                fallback.getCategory(), "USER", fallback.getId());

        List<ContentRecommendation> toSave = new ArrayList<>();
        for (Content user : userContents) {
            if (user.getPerspectiveStakeholder() == null) continue;
            int score = calculateScore(user, fallback);
            if (score == 0) continue;
            if (!hasTopicOverlap(user, fallback)) continue;
            if (recommendationRepository.existsBySourceContentIdAndTargetContentId(user.getId(), fallback.getId())) continue;

            toSave.add(ContentRecommendation.builder()
                    .sourceContentId(user.getId())
                    .targetContentId(fallback.getId())
                    .score(score)
                    .strategy(strategyName)
                    .build());
        }

        recommendationRepository.saveAll(toSave);
        log.info("[Recommendation] FALLBACK 관계 추가 fallbackId={} count={}", fallback.getId(), toSave.size());
    }

    private void saveRelations(Content source, List<Content> candidates) {
        List<ContentRecommendation> toSave = new ArrayList<>();

        for (Content target : candidates) {
            int score = calculateScore(source, target);

            if (!recommendationRepository.existsBySourceContentIdAndTargetContentId(
                    source.getId(), target.getId())) {
                toSave.add(ContentRecommendation.builder()
                        .sourceContentId(source.getId())
                        .targetContentId(target.getId())
                        .score(score)
                        .strategy(strategyName)
                        .build());
            }

            if (!recommendationRepository.existsBySourceContentIdAndTargetContentId(
                    target.getId(), source.getId())) {
                toSave.add(ContentRecommendation.builder()
                        .sourceContentId(target.getId())
                        .targetContentId(source.getId())
                        .score(score)
                        .strategy(strategyName)
                        .build());
            }
        }

        recommendationRepository.saveAll(toSave);
        log.info("[Recommendation] contentId={} strategy={} 관계 추가={}",
                source.getId(), strategyName, toSave.size());
    }

    private int calculateScore(Content a, Content b) {
        if (Objects.equals(a.getPerspectiveStakeholder(), b.getPerspectiveStakeholder())) {
            return 0;
        }
        int score = 1;
        if (!Objects.equals(a.getPerspectiveLevel(), b.getPerspectiveLevel())) {
            score += 1;
        }
        if (a.getTone() != null && b.getTone() != null
                && !Objects.equals(a.getTone(), b.getTone())) {
            score += 1;
        }
        if (a.getIsOpinionated() != null && b.getIsOpinionated() != null
                && !Objects.equals(a.getIsOpinionated(), b.getIsOpinionated())) {
            score += 1;
        }
        return score;
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
        if (content.getCategory() != null
                && contentRepository.existsByCategoryAndSource(content.getCategory(), "FALLBACK")) {
            log.info("[Recommendation] 동일 카테고리 FALLBACK 이미 존재, 검색 스킵 category={} contentId={}",
                    content.getCategory(), content.getId());
            return;
        }

        String query = buildFallbackQuery(content);
        if (query == null) return;
        try {
            List<String> videoIds = youtubeSearchClient.searchVideoIds(query, 50);
            Set<String> excluded = Set.of(content.getExternalContentId());
            List<String> filtered = videoIds.stream()
                    .filter(id -> !excluded.contains(id))
                    .toList();
            log.info("[Recommendation] fallback 검색 결과 query='{}' total={} filtered={}",
                    query, videoIds.size(), filtered.size());
            for (String videoId : filtered) {
                eventPublisher.publishEvent(
                        new FallbackIngestRequestedEvent(this, YOUTUBE_BASE_URL + videoId));
            }
        } catch (Exception e) {
            log.warn("[Recommendation] fallback 검색 실패: {}", e.getMessage());
        }
    }

    private String buildFallbackQuery(Content content) {
        List<String> keywords = content.getKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            return keywords.stream().limit(5).collect(Collectors.joining(" "));
        }
        return content.getCategory();
    }
}
