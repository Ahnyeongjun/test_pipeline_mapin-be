package com.mapin.infra.youtube.dto;

public record YoutubeVideoItem(
        String id, YoutubeSnippet snippet,
        YoutubeContentDetails contentDetails, YoutubeStatistics statistics) {
}
