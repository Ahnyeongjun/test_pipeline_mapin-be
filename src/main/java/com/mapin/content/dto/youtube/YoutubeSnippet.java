package com.mapin.content.dto.youtube;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YoutubeSnippet(
        String title,
        String description,
        String channelTitle,
        String publishedAt,
        @JsonProperty("categoryId") String categoryId,
        YoutubeThumbnails thumbnails
) {
}
