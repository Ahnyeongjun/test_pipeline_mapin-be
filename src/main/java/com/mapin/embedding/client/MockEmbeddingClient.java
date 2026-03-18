package com.mapin.embedding.client;

import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSION = 1536;

    @Override
    public List<Float> embed(String text) {
        return Collections.nCopies(DIMENSION, 0.1f);
    }

    @Override
    public String modelName() {
        return "mock-embedding";
    }
}
