package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentResponse;
import com.mapin.content.dto.YoutubeVideoMetadata;
import com.mapin.content.infrastructure.YoutubeMetadataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentIngestService {

    private final ContentRepository contentRepository;
    private final YoutubeUrlParser youtubeUrlParser;
    private final YoutubeMetadataClient youtubeMetadataClient;

    public ContentResponse ingestYoutubeUrl(String rawUrl) {
        String videoId = youtubeUrlParser.extractVideoId(rawUrl);
        String canonicalUrl = youtubeUrlParser.canonicalize(videoId);

        return contentRepository.findByCanonicalUrl(canonicalUrl)
                .map(ContentResponse::from)
                .orElseGet(() -> saveNewYoutubeContent(videoId, canonicalUrl));
    }

    private ContentResponse saveNewYoutubeContent(String videoId, String canonicalUrl) {
        YoutubeVideoMetadata metadata = youtubeMetadataClient.fetchVideoMetadata(videoId);

        Content content = Content.builder()
                .canonicalUrl(canonicalUrl)
                .platform("YOUTUBE")
                .externalContentId(metadata.videoId())
                .title(metadata.title())
                .description(metadata.description())
                .thumbnailUrl(metadata.thumbnailUrl())
                .channelTitle(metadata.channelTitle())
                .publishedAt(metadata.publishedAt())
                .youtubeCategoryId(metadata.categoryId())
                .duration(metadata.duration())
                .viewCount(metadata.viewCount())
                .status("ACTIVE")
                .build();

        Content saved = contentRepository.save(content);
        return ContentResponse.from(saved);
    }
}
