package com.mapin.recommendation.event;

import com.mapin.embedding.event.ContentEmbeddedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * vector 전략: 임베딩 완료 → 즉시 recommendation 실행 (USER/FALLBACK 모두)
 * source는 JobParameters로 전달되어 RecommendationTasklet에서 분기 처리
 */
@Component
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@Slf4j
public class ContentEmbeddedEventHandler {

    private final JobLauncher jobLauncher;
    private final Job recommendationJob;

    public ContentEmbeddedEventHandler(JobLauncher jobLauncher,
            @Qualifier("recommendationJob") Job recommendationJob) {
        this.jobLauncher = jobLauncher;
        this.recommendationJob = recommendationJob;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentEmbeddedEvent event) {
        Long contentId = event.getContentId();
        log.info("[RecommendationHandler] ContentEmbeddedEvent 수신 contentId={} source={}", contentId, event.getSource());

        JobParameters params = new JobParametersBuilder()
                .addLong("contentId", contentId)
                .addString("source", event.getSource())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(recommendationJob, params);
        } catch (Exception e) {
            log.error("[Recommendation] Job 실행 실패 contentId={}", contentId, e);
        }
    }
}
