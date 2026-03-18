package com.mapin.recommendation.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RecommendationTrigger {

    private final JobLauncher jobLauncher;
    private final Job recommendationJob;

    public RecommendationTrigger(JobLauncher jobLauncher,
            @Qualifier("recommendationJob") Job recommendationJob) {
        this.jobLauncher = jobLauncher;
        this.recommendationJob = recommendationJob;
    }

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
