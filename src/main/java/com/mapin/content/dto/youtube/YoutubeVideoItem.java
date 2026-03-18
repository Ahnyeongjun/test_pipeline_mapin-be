package com.mapin.content.dto.youtube;

public record YoutubeVideoItem(
        String id,
        YoutubeSnippet snippet,
        YoutubeContentDetails contentDetails,
        YoutubeStatistics statistics
) {
}
