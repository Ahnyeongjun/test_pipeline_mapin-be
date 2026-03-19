package com.mapin.ingest.api;

import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeUrlParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
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

    private final YoutubeUrlParser youtubeUrlParser;
    private final ContentRepository contentRepository;

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) throws Exception {
        String canonicalUrl = youtubeUrlParser.canonicalize(youtubeUrlParser.extractVideoId(request.url()));

        JobParameters params = new JobParametersBuilder()
                .addString("url", canonicalUrl)
                .addString("source", "USER")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(ingestJob, params);
        log.info("[Ingest] Job launched. url={}", canonicalUrl);

        Long contentId = contentRepository.findByCanonicalUrl(canonicalUrl)
                .map(c -> c.getId())
                .orElse(null);

        return ResponseEntity.accepted().body(new IngestResponse(contentId, canonicalUrl));
    }
}
