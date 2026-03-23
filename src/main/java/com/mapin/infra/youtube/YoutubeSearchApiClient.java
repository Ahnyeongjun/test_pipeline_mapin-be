package com.mapin.infra.youtube;

import com.mapin.infra.youtube.dto.YoutubeSearchResponse;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }

    @Override
    public List<String> searchVideoIds(String query, int maxResults) {
        try {
            YoutubeSearchResponse response = restClient.get()
                    .uri(b -> b.path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("q", query)
                            .queryParam("type", "video")
                            .queryParam("maxResults", maxResults)
                            .queryParam("regionCode", "KR")
                            .queryParam("relevanceLanguage", "ko")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(YoutubeSearchResponse.class);

            if (response == null || response.items() == null) return List.of();

            return response.items().stream()
                    .map(item -> item.id() != null ? item.id().videoId() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (RestClientResponseException e) {
            log.error("YouTube search API error. query='{}' status={}", query, e.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.error("YouTube search unexpected error. query='{}'", query, e);
            return List.of();
        }
    }
}
