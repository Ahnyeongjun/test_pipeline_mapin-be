package com.mapin.recommendation.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeSearchClient;
import com.mapin.recommendation.client.RecommendationStrategy;
import com.mapin.recommendation.domain.ContentRecommendation;
import com.mapin.recommendation.domain.ContentRecommendationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        List<Content> filtered = filterCandidates(content, candidates);

        if (filtered.size() < FALLBACK_THRESHOLD) {
            log.info("[Recommendation] 반대관점 후보 부족({}개), fallback 실행 contentId={}", filtered.size(), content.getId());
            tryFallbackSearch(content);
            // fallback ingest는 비동기로 돌기 때문에 이번 요청에서 재시도하지 않음
        }

        return filtered;
    }

    /**
     * 후보 필터링 및 채널 다양성 보장.
     * 1. 분석 완료된 콘텐츠만 통과 (perspectiveStakeholder 필수)
     * 2. 관점 점수 > 0 인 것만 통과
     * 3. keywords 겹침이 너무 낮으면 (다른 주제) 제외
     * 4. 같은 채널은 점수 가장 높은 1개만 유지
     */
    private List<Content> filterCandidates(Content source, List<Content> candidates) {
        Map<String, Content> bestByChannel = new LinkedHashMap<>();
        Map<String, Integer> bestScoreByChannel = new LinkedHashMap<>();

        for (Content target : candidates) {
            if (target.getPerspectiveStakeholder() == null) continue;

            int score = calculateScore(source, target);
            if (score == 0) continue;
            if (!hasTopicOverlap(source, target)) continue;

            // channelTitle이 null이면 content마다 고유 키를 사용해 채널 중복 처리에서 제외
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

    /**
     * keywords 겹침으로 같은 주제인지 확인.
     * 둘 중 하나라도 keywords가 없으면 필터링하지 않음.
     * 겹치는 비율이 20% 미만이면 다른 주제로 판단해 제외.
     */
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

    /**
     * FALLBACK 콘텐츠: 기존 USER 콘텐츠 → 신규 FALLBACK 단방향 관계 추가
     */
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

    /**
     * USER 콘텐츠 ↔ 후보 양방향 관계 저장.
     * filterCandidates()를 거친 후보만 전달받으므로 추가 필터링 없이 저장.
     */
    private void saveRelations(Content source, List<Content> candidates) {
        List<ContentRecommendation> toSave = new ArrayList<>();

        for (Content target : candidates) {
            int score = calculateScore(source, target);

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
     * 기본 점수 (perspectiveStakeholder/Level 기반):
     *   0: 같은 stakeholder → 추천 제외
     *   1: 다른 stakeholder
     *   2: 다른 stakeholder + 다른 level
     * 보너스 점수 (analysis 결과 활용):
     *   +1: tone이 다를 때 (논조 차이)
     *   +1: isOpinionated가 다를 때 (사실 보도 vs 의견 콘텐츠)
     * 최대 점수: 4
     */
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
            // 원본 영상 자신 제외
            Set<String> excluded = Set.of(content.getExternalContentId());
            List<String> filtered = videoIds.stream()
                    .filter(id -> !excluded.contains(id))
                    .toList();
            log.info("[Recommendation] fallback 검색 결과 query='{}' total={} filtered={}", query, videoIds.size(), filtered.size());
            for (String videoId : filtered) {
                triggerFallbackIngest(videoId);
                try {
                    Thread.sleep(1500); // GPT TPM 한도 초과 방지
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[Recommendation] fallback ingest 인터럽트 발생, 중단 contentId={}", content.getId());
                    return;
                }
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
