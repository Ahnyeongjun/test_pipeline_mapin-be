package com.mapin.ingest.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@Slf4j
public class IngestController {

    private final JobLauncher jobLauncher;

    @Qualifier("ingestJob")
    private final Job ingestJob;

    @PostMapping
    public ResponseEntity<Void> ingest(@RequestBody IngestRequest request) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("url", request.url())
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(ingestJob, params);
        log.info("[Ingest] Job launched. url={}", request.url());
        return ResponseEntity.accepted().build();
    }
}
