package com.mapin.analysis.event;

import com.mapin.ingest.event.ContentIngestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentIngestedEventHandler {

    private final JobLauncher jobLauncher;

    @Qualifier("analysisJob")
    private final Job analysisJob;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentIngestedEvent event) {
        Long contentId = event.getContentId();
        log.info("[Analysis] ContentIngestedEvent received. contentId={} source={}", contentId, event.getSource());

        JobParameters params = new JobParametersBuilder()
                .addLong("contentId", contentId)
                .addString("source", event.getSource())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(analysisJob, params);
        } catch (Exception e) {
            log.error("[Analysis] Job 실행 실패 contentId={}", contentId, e);
        }
    }
}
