package com.mapin.content.infrastructure;

import com.mapin.content.dto.qdrant.QdrantQueryResponse;
import com.mapin.content.dto.qdrant.QdrantScoredPoint;
import com.mapin.content.port.VectorSearchResult;
import com.mapin.content.port.VectorStoreClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class QdrantVectorStoreClient implements VectorStoreClient {

    private final RestClient restClient;
    private final String collectionName;

    public QdrantVectorStoreClient(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            @Value("${qdrant.collection-name}") String collectionName
    ) {
        this.restClient = RestClient.create("http://" + host + ":" + port);
        this.collectionName = collectionName;
    }

    @Override
    public void upsert(String id, List<Float> vector, Map<String, Object> metadata) {
        Map<String, Object> body = Map.of(
                "points", List.of(
                        Map.of(
                                "id", id,
                                "vector", vector,
                                "payload", metadata == null ? Map.of() : metadata
                        )
                )
        );

        restClient.put()
                .uri("/collections/{collectionName}/points", collectionName)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<VectorSearchResult> searchById(String vectorId, int topK) {
        Map<String, Object> body = Map.of(
                "query", vectorId,
                "limit", topK,
                "with_payload", true
        );

        QdrantQueryResponse response = restClient.post()
                .uri("/collections/{collectionName}/points/query", collectionName)
                .body(body)
                .retrieve()
                .body(QdrantQueryResponse.class);

        if (response == null || response.result() == null || response.result().points() == null) {
            return List.of();
        }

        return response.result().points().stream()
                .map(this::toVectorSearchResult)
                .filter(Objects::nonNull)
                .toList();
    }

    private VectorSearchResult toVectorSearchResult(QdrantScoredPoint point) {
        if (point.id() == null) {
            return null;
        }
        return new VectorSearchResult(
                String.valueOf(point.id()),
                point.score(),
                point.payload() == null ? Map.of() : point.payload()
        );
    }
}
