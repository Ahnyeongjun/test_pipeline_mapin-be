package com.mapin.domain.content.service;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.youtube.YoutubeMetadataClient;
import com.mapin.infra.youtube.YoutubeUrlParser;
import com.mapin.infra.youtube.YoutubeVideoMetadata;
import com.mapin.domain.content.event.ContentIngestedEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestService {

    private final ContentRepository contentRepository;
    private final YoutubeUrlParser youtubeUrlParser;
    private final YoutubeMetadataClient youtubeMetadataClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long ingest(String url, String source) {
        String videoId = youtubeUrlParser.extractVideoId(url);
        String canonicalUrl = youtubeUrlParser.canonicalize(videoId);

        AtomicBoolean isNew = new AtomicBoolean(false);
        Content content = contentRepository.findByCanonicalUrl(canonicalUrl)
                .orElseGet(() -> { isNew.set(true); return saveNew(videoId, canonicalUrl, source); });

        content.updatePipelineStatus("INGESTED");
        contentRepository.save(content);

        log.info("[Ingest] {} contentId={} url={} source={}",
                isNew.get() ? "NEW" : "EXISTING", content.getId(), canonicalUrl, source);
        eventPublisher.publishEvent(new ContentIngestedEvent(this, content.getId(), source));
        return content.getId();
    }

    private Content saveNew(String videoId, String canonicalUrl, String source) {
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
                .source(source)
                .build());
    }
}
