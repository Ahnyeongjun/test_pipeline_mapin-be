package com.mapin.content.dto;

import com.mapin.content.domain.Content;
import java.time.OffsetDateTime;

public record ContentResponse(
        Long id,
        String canonicalUrl,
        String platform,
        String externalContentId,
        String title,
        String description,
        String thumbnailUrl,
        String channelTitle,
        OffsetDateTime publishedAt,
        String youtubeCategoryId,
        String duration,
        Long viewCount,
        String status
) {
    public static ContentResponse from(Content content) {
        return new ContentResponse(
                content.getId(),
                content.getCanonicalUrl(),
                content.getPlatform(),
                content.getExternalContentId(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getChannelTitle(),
                content.getPublishedAt(),
                content.getYoutubeCategoryId(),
                content.getDuration(),
                content.getViewCount(),
                content.getStatus()
        );
    }
}
