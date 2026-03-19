package com.mapin.recommendation.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeSearchClient;
import com.mapin.recommendation.client.RecommendationStrategy;
import com.mapin.recommendation.domain.ContentRecommendation;
import com.mapin.recommendation.domain.ContentRecommendationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RecommendationTasklet implements Tasklet {

    private static final int FALLBACK_THRESHOLD = 1;

    private static final String YOUTUBE_BASE_URL = "https://www.youtube.com/watch?v=";


    private final ContentRepository contentRepository;
    private final ContentRecommendationRepository recommendationRepository;
    private final Map<String, RecommendationStrategy> strategies;
    private final YoutubeSearchClient youtubeSearchClient;
    private final JobLauncher jobLauncher;
    private final Job ingestJob;
    private final String strategyName;

    public RecommendationTasklet(
            ContentRepository contentRepository,
            ContentRecommendationRepository recommendationRepository,
            Map<String, RecommendationStrategy> strategies,
            YoutubeSearchClient youtubeSearchClient,
            JobLauncher jobLauncher,
            @Qualifier("ingestJob") Job ingestJob,
            @Value("${pipeline.recommendation.strategy:db}") String strategyName) {
        this.contentRepository = contentRepository;
        this.recommendationRepository = recommendationRepository;
        this.strategies = strategies;
        this.youtubeSearchClient = youtubeSearchClient;
        this.jobLauncher = jobLauncher;
        this.ingestJob = ingestJob;
        this.strategyName = strategyName;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long contentId = contribution.getStepExecution().getJobParameters().getLong("contentId");
        String source = contribution.getStepExecution().getJobParameters().getString("source", "USER");

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        if ("FALLBACK".equals(source)) {
            // FALLBACK: 기존 USER 콘텐츠 → 신규 FALLBACK 단방향 관계만 추가
            linkFallbackToExistingUsers(content);
        } else {
            // USER: 신규 콘텐츠 ↔ 기존 콘텐츠 양방향 관계 추가
            List<Content> candidates = getCandidatesWithFallback(content);
            saveRelations(content, candidates);
        }

        return RepeatStatus.FINISHED;
    }

    /**
     * USER 콘텐츠: 후보 조회 → 후보 부족 시 fallback → 양방향 저장
     */
    private List<Content> getCandidatesWithFallback(Content content) {
        RecommendationStrategy strategy = resolveStrategy();
        List<Content> candidates = strategy.getCandidates(content);

        long oppositeCount = candidates.stream().filter(c -> calculateScore(content, c) > 0).count();

        if (oppositeCount < FALLBACK_THRESHOLD) {
            log.info("[Recommendation] 반대관점 후보 부족({}개), fallback 실행 contentId={}", oppositeCount, content.getId());
            tryFallbackSearch(content);
            // fallback ingest는 비동기로 돌기 때문에 이번 요청에서 재시도하지 않음
        }

        return candidates;
    }

    /**
     * FALLBACK 콘텐츠: 기존 USER 콘텐츠 → 신규 FALLBACK 단방향 관계 추가
     */
    private void linkFallbackToExistingUsers(Content fallback) {
        if (fallback.getCategory() == null) return;

        List<Content> userContents = contentRepository.findByCategoryAndSourceAndIdNot(
                fallback.getCategory(), "USER", fallback.getId());

        List<ContentRecommendation> toSave = new ArrayList<>();
        for (Content user : userContents) {
            int score = calculateScore(user, fallback);
            if (score > 0 && !recommendationRepository.existsBySourceContentIdAndTargetContentId(
                    user.getId(), fallback.getId())) {
                toSave.add(ContentRecommendation.builder()
                        .sourceContentId(user.getId())
                        .targetContentId(fallback.getId())
                        .score(score)
                        .strategy(strategyName)
                        .build());
            }
        }

        recommendationRepository.saveAll(toSave);
        log.info("[Recommendation] FALLBACK 관계 추가 fallbackId={} count={}", fallback.getId(), toSave.size());
    }

    /**
     * USER 콘텐츠 ↔ 후보 양방향 관계 저장 (score > 0인 것만)
     */
    private void saveRelations(Content source, List<Content> candidates) {
        List<ContentRecommendation> toSave = new ArrayList<>();

        for (Content target : candidates) {
            int score = calculateScore(source, target);
            if (score == 0) continue;

            // source → target
            if (!recommendationRepository.existsBySourceContentIdAndTargetContentId(
                    source.getId(), target.getId())) {
                toSave.add(ContentRecommendation.builder()
                        .sourceContentId(source.getId())
                        .targetContentId(target.getId())
                        .score(score)
                        .strategy(strategyName)
                        .build());
            }

            // target → source (양방향)
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

    /**
     * 반대 관점 점수 계산.
     * 2: 강한 반대관점 (perspectiveLevel + stakeholder 모두 다름)
     * 1: 약한 반대관점 (stakeholder만 다름)
     * 0: 같은 관점
     */
    private int calculateScore(Content a, Content b) {
        if (Objects.equals(a.getPerspectiveStakeholder(), b.getPerspectiveStakeholder())) {
            return 0;
        }
        int score = 1;
        if (!Objects.equals(a.getPerspectiveLevel(), b.getPerspectiveLevel())) {
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
        String query = buildFallbackQuery(content);
        if (query == null) return;
        try {
            List<String> videoIds = youtubeSearchClient.searchVideoIds(query, 50);
            // 원본 영상 자신 제외
            Set<String> excluded = Set.of(content.getExternalContentId());
            List<String> filtered = videoIds.stream()
                    .filter(id -> !excluded.contains(id))
                    .toList();
            log.info("[Recommendation] fallback 검색 결과 query='{}' total={} filtered={}", query, videoIds.size(), filtered.size());
            for (String videoId : filtered) {
                triggerFallbackIngest(videoId);
            }
        } catch (Exception e) {
            log.warn("[Recommendation] fallback 검색 실패: {}", e.getMessage());
        }
    }

    private void triggerFallbackIngest(String videoId) {
        String url = YOUTUBE_BASE_URL + videoId;
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("url", url)
                    .addString("source", "FALLBACK")
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(ingestJob, params);
            log.info("[Recommendation] FALLBACK ingest 실행 videoId={}", videoId);
        } catch (Exception e) {
            log.warn("[Recommendation] FALLBACK ingest 실패 videoId={}: {}", videoId, e.getMessage());
        }
    }

    /**
     * YouTube 검색 쿼리 생성.
     * 같은 주제 영상을 최대한 넓게 수집 → 반대관점 필터링은 calculateScore에서 수행
     * ex) keywords=["금리","한국은행","통화정책"] → "금리 한국은행 통화정책"
     */
    private String buildFallbackQuery(Content content) {
        List<String> keywords = content.getKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            return keywords.stream().limit(5).collect(Collectors.joining(" "));
        }
        return content.getCategory();
    }
}
