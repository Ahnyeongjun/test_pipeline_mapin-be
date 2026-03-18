package com.mapin.embedding.client;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
@Slf4j
public class QdrantVectorStoreClient implements VectorStoreClient {

    private final RestClient restClient;
    private final String collection;

    public QdrantVectorStoreClient(
            @Value("${qdrant.host:localhost}") String host,
            @Value("${qdrant.port:6333}") int port,
            @Value("${qdrant.collection-name:mapin_contents}") String collection) {
        this.restClient = RestClient.builder()
                .baseUrl("http://%s:%d".formatted(host, port))
                .build();
        this.collection = collection;
    }

    @Override
    public void upsert(long id, List<Float> vector) {
        Map<String, Object> body = Map.of(
                "points", List.of(Map.of(
                        "id", id,
                        "vector", vector
                ))
        );
        restClient.put()
                .uri("/collections/{collection}/points", collection)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.debug("[Qdrant] upsert id={} vectorDim={}", id, vector.size());
    }

    @Override
    public List<String> search(List<Float> vector, int topK) {
        Map<String, Object> body = Map.of(
                "vector", vector,
                "limit", topK,
                "with_payload", false
        );
        SearchResponse response = restClient.post()
                .uri("/collections/{collection}/points/search", collection)
                .body(body)
                .retrieve()
                .body(SearchResponse.class);

        if (response == null || response.result() == null) {
            return List.of();
        }
        return response.result().stream()
                .map(r -> String.valueOf(r.id()))
                .toList();
    }

    record SearchResponse(List<SearchResult> result) {}
    record SearchResult(long id, float score) {}
}
