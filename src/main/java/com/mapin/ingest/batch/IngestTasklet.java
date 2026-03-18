package com.mapin.ingest.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeMetadataClient;
import com.mapin.ingest.client.YoutubeUrlParser;
import com.mapin.ingest.client.YoutubeVideoMetadata;
import com.mapin.ingest.event.ContentIngestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestTasklet implements Tasklet {

    private final ContentRepository contentRepository;
    private final YoutubeUrlParser youtubeUrlParser;
    private final YoutubeMetadataClient youtubeMetadataClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String url = (String) chunkContext.getStepContext()
                .getJobParameters().get("url");

        String videoId = youtubeUrlParser.extractVideoId(url);
        String canonicalUrl = youtubeUrlParser.canonicalize(videoId);

        Content content = contentRepository.findByCanonicalUrl(canonicalUrl)
                .orElseGet(() -> saveNew(videoId, canonicalUrl));

        log.info("[Ingest] contentId={} url={}", content.getId(), canonicalUrl);
        eventPublisher.publishEvent(new ContentIngestedEvent(this, content.getId()));
        return RepeatStatus.FINISHED;
    }

    private Content saveNew(String videoId, String canonicalUrl) {
        YoutubeVideoMetadata meta = youtubeMetadataClient.fetch(videoId);
        return contentRepository.save(Content.builder()
                .canonicalUrl(canonicalUrl)
                .platform("YOUTUBE")
                .externalContentId(meta.videoId())
                .title(meta.title())
                .description(meta.description())
                .thumbnailUrl(meta.thumbnailUrl())
                .channelTitle(meta.channelTitle())
                .publishedAt(meta.publishedAt())
                .youtubeCategoryId(meta.categoryId())
                .duration(meta.duration())
                .viewCount(meta.viewCount())
                .status("ACTIVE")
                .build());
    }
}
