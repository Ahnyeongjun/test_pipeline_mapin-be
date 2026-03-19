package com.mapin.ingest.client;

import com.mapin.ingest.client.youtube.YoutubeContentDetails;
import com.mapin.ingest.client.youtube.YoutubeSnippet;
import com.mapin.ingest.client.youtube.YoutubeStatistics;
import com.mapin.ingest.client.youtube.YoutubeThumbnail;
import com.mapin.ingest.client.youtube.YoutubeThumbnails;
import com.mapin.ingest.client.youtube.YoutubeVideoItem;
import com.mapin.ingest.client.youtube.YoutubeVideosResponse;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class YoutubeMetadataClient {

    private final RestClient restClient;

    @Value("${youtube.api.key}")
    private String apiKey;

    public YoutubeMetadataClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }

    public YoutubeVideoMetadata fetch(String videoId) {
        YoutubeVideosResponse response = restClient.get()
                .uri(b -> b.path("/videos")
                        .queryParam("part", "snippet,contentDetails,statistics")
                        .queryParam("id", videoId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(YoutubeVideosResponse.class);

        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new IllegalArgumentException("해당 videoId의 영상을 찾을 수 없습니다: " + videoId);
        }

        YoutubeVideoItem item = response.items().get(0);
        return new YoutubeVideoMetadata(
                item.id(),
                item.snippet() != null ? item.snippet().title() : null,
                item.snippet() != null ? item.snippet().description() : null,
                extractThumbnailUrl(item),
                item.snippet() != null ? item.snippet().channelTitle() : null,
                item.snippet() != null && item.snippet().publishedAt() != null
                        ? OffsetDateTime.parse(item.snippet().publishedAt()) : null,
                item.snippet() != null ? item.snippet().categoryId() : null,
                item.contentDetails() != null ? item.contentDetails().duration() : null,
                extractViewCount(item)
        );
    }

    private String extractThumbnailUrl(YoutubeVideoItem item) {
        YoutubeSnippet snippet = item.snippet();
        if (snippet == null || snippet.thumbnails() == null) return null;
        YoutubeThumbnails t = snippet.thumbnails();
        if (t.high() != null) return t.high().url();
        if (t.medium() != null) return t.medium().url();
        if (t.defaultValue() != null) return t.defaultValue().url();
        return null;
    }

    private Long extractViewCount(YoutubeVideoItem item) {
        YoutubeStatistics stats = item.statistics();
        if (stats == null || stats.viewCount() == null) return null;
        try { return Long.parseLong(stats.viewCount()); } catch (NumberFormatException e) { return null; }
    }
}
