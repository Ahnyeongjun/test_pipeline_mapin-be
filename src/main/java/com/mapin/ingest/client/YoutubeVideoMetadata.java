package com.mapin.ingest.client;

import java.time.OffsetDateTime;

public record YoutubeVideoMetadata(
        String videoId, String title, String description, String thumbnailUrl,
        String channelTitle, OffsetDateTime publishedAt, String categoryId,
        String duration, Long viewCount) {
}
