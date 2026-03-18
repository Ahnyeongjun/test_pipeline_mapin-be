package com.mapin.content.infrastructure;

import com.mapin.content.dto.youtube.YoutubeSearchResponse;
import com.mapin.content.port.YoutubeSearchClient;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("!test")
@Slf4j
public class YoutubeSearchApiClient implements YoutubeSearchClient {

    private final RestClient restClient;

    @Value("${youtube.api.key}")
    private String apiKey;

    public YoutubeSearchApiClient() {
        this(RestClient.create("https://www.googleapis.com/youtube/v3"));
    }

    YoutubeSearchApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<String> searchVideoIds(String query, int maxResults) {
        try {
            log.debug("Calling YouTube search. query='{}', maxResults={}", query, maxResults);
            YoutubeSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("q", query)
                            .queryParam("type", "video")
                            .queryParam("maxResults", maxResults)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(YoutubeSearchResponse.class);

            if (response == null || response.items() == null) {
                log.warn("YouTube search response empty for query='{}'", query);
                return List.of();
            }

            List<String> videoIds = response.items().stream()
                    .map(item -> item.id() != null ? item.id().videoId() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            log.debug("YouTube search query='{}' returned {} ids: {}", query, videoIds.size(), videoIds);
            return videoIds;
        } catch (RestClientResponseException e) {
            log.error("YouTube search API error for query='{}' status={} body={}",
                    query, e.getStatusCode(), e.getResponseBodyAsString(), e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error while calling YouTube search for query='{}'", query, e);
            return List.of();
        }
    }
}
