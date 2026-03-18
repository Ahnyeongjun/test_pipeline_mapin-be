package com.mapin.recommendation.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * recommendationJob 실행을 공통으로 처리하는 헬퍼.
 * ContentAnalyzedEventHandler / ContentEmbeddedEventHandler 가 함께 사용한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationTrigger {

    private final JobLauncher jobLauncher;

    @Qualifier("recommendationJob")
    private final Job recommendationJob;

    public void trigger(Long contentId) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("contentId", contentId)
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(recommendationJob, params);
        } catch (Exception e) {
            log.error("[RecommendationTrigger] recommendationJob 실행 실패 contentId={}", contentId, e);
        }
    }
}
