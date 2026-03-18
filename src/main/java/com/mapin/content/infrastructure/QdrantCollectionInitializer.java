package com.mapin.content.infrastructure;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class QdrantCollectionInitializer implements ApplicationRunner {

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.port}")
    private int port;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    @Value("${qdrant.vector-size}")
    private int vectorSize;

    @Value("${qdrant.distance}")
    private String distance;

    @Override
    public void run(ApplicationArguments args) {
        RestClient restClient = RestClient.create("http://" + host + ":" + port);
        try {
            restClient.get()
                    .uri("/collections/{collectionName}", collectionName)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                createCollection(restClient);
                return;
            }
            throw e;
        }
    }

    private void createCollection(RestClient restClient) {
        Map<String, Object> body = Map.of(
                "vectors", Map.of(
                        "size", vectorSize,
                        "distance", distance
                )
        );

        restClient.put()
                .uri("/collections/{collectionName}", collectionName)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
