package com.mapin.ingest.client.youtube;

public record YoutubeVideoItem(
        String id, YoutubeSnippet snippet,
        YoutubeContentDetails contentDetails, YoutubeStatistics statistics) {
}
