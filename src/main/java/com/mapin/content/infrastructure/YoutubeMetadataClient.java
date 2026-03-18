package com.mapin.content.infrastructure;

import com.mapin.content.dto.YoutubeVideoMetadata;
import com.mapin.content.dto.youtube.YoutubeContentDetails;
import com.mapin.content.dto.youtube.YoutubeSnippet;
import com.mapin.content.dto.youtube.YoutubeStatistics;
import com.mapin.content.dto.youtube.YoutubeThumbnail;
import com.mapin.content.dto.youtube.YoutubeThumbnails;
import com.mapin.content.dto.youtube.YoutubeVideoItem;
import com.mapin.content.dto.youtube.YoutubeVideosResponse;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class YoutubeMetadataClient {

    private final RestClient restClient;

    @Value("${youtube.api.key}")
    private String apiKey;

    public YoutubeMetadataClient() {
        this.restClient = RestClient.create("https://www.googleapis.com/youtube/v3");
    }

    public YoutubeVideoMetadata fetchVideoMetadata(String videoId) {
        YoutubeVideosResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "snippet,contentDetails,statistics")
                        .queryParam("id", videoId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(YoutubeVideosResponse.class);

        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new IllegalArgumentException("해당 videoId의 유튜브 영상을 찾을 수 없습니다.");
        }

        YoutubeVideoItem item = response.items().get(0);

        return new YoutubeVideoMetadata(
                item.id(),
                item.snippet() != null ? item.snippet().title() : null,
                item.snippet() != null ? item.snippet().description() : null,
                extractThumbnailUrl(item),
                item.snippet() != null ? item.snippet().channelTitle() : null,
                item.snippet() != null && item.snippet().publishedAt() != null
                        ? OffsetDateTime.parse(item.snippet().publishedAt())
                        : null,
                item.snippet() != null ? item.snippet().categoryId() : null,
                item.contentDetails() != null ? item.contentDetails().duration() : null,
                extractViewCount(item)
        );
    }

    private String extractThumbnailUrl(YoutubeVideoItem item) {
        YoutubeSnippet snippet = item.snippet();
        if (snippet == null || snippet.thumbnails() == null) {
            return null;
        }
        YoutubeThumbnails thumbnails = snippet.thumbnails();
        if (thumbnails.high() != null) return thumbnails.high().url();
        if (thumbnails.medium() != null) return thumbnails.medium().url();
        if (thumbnails.defaultValue() != null) return thumbnails.defaultValue().url();
        return null;
    }

    private Long extractViewCount(YoutubeVideoItem item) {
        YoutubeStatistics statistics = item.statistics();
        if (statistics == null || statistics.viewCount() == null) {
            return null;
        }
        try {
            return Long.parseLong(statistics.viewCount());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
