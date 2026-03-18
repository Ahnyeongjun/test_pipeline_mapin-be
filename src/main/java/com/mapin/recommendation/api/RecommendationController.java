package com.mapin.recommendation.api;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.recommendation.domain.ContentRecommendation;
import com.mapin.recommendation.domain.ContentRecommendationRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
@Slf4j
public class RecommendationController {

    private final ContentRepository contentRepository;
    private final ContentRecommendationRepository recommendationRepository;
    private final JobLauncher jobLauncher;
    private final Job recommendationJob;

    public RecommendationController(
            ContentRepository contentRepository,
            ContentRecommendationRepository recommendationRepository,
            JobLauncher jobLauncher,
            @Qualifier("recommendationJob") Job recommendationJob) {
        this.contentRepository = contentRepository;
        this.recommendationRepository = recommendationRepository;
        this.jobLauncher = jobLauncher;
        this.recommendationJob = recommendationJob;
    }

    @GetMapping("/{id}/recommendations")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @PathVariable Long id) throws Exception {

        contentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + id));

        List<ContentRecommendation> existing = recommendationRepository.findBySourceContentId(id);

        if (existing.isEmpty()) {
            log.info("[Recommendation] 캐시 없음, Job 실행 contentId={}", id);
            JobParameters params = new JobParametersBuilder()
                    .addLong("contentId", id)
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(recommendationJob, params);
            existing = recommendationRepository.findBySourceContentId(id);
        }

        List<Long> targetIds = existing.stream()
                .map(ContentRecommendation::getTargetContentId)
                .toList();

        List<RecommendationResponse> result = contentRepository.findAllById(targetIds).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    private RecommendationResponse toResponse(Content c) {
        return new RecommendationResponse(
                c.getId(),
                c.getTitle(),
                c.getThumbnailUrl(),
                c.getCategory(),
                c.getPerspectiveLevel(),
                c.getPerspectiveStakeholder(),
                c.getCanonicalUrl()
        );
    }
}
