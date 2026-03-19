package com.mapin.embedding.event;

import com.mapin.analysis.event.ContentAnalyzedEvent;
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

@Component("embeddingContentAnalyzedEventHandler")
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@Slf4j
public class ContentAnalyzedEventHandler {

    private final JobLauncher jobLauncher;
    private final Job embeddingJob;

    public ContentAnalyzedEventHandler(JobLauncher jobLauncher,
            @Qualifier("embeddingJob") Job embeddingJob) {
        this.jobLauncher = jobLauncher;
        this.embeddingJob = embeddingJob;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentAnalyzedEvent event) throws Exception {
        Long contentId = event.getContentId();
        log.info("[EmbeddingHandler] ContentAnalyzedEvent 수신 contentId={} source={}", contentId, event.getSource());

        JobParameters params = new JobParametersBuilder()
                .addLong("contentId", contentId)
                .addString("source", event.getSource())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(embeddingJob, params);
    }
}
