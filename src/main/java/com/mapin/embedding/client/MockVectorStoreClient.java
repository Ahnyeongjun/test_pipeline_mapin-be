package com.mapin.embedding.client;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockVectorStoreClient implements VectorStoreClient {

    @Override
    public void upsert(long id, List<Float> vector) {
        // no-op
    }

    @Override
    public List<String> search(List<Float> vector, int topK) {
        return List.of();
    }
}
