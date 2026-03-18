package com.mapin.embedding.client;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);

    String modelName();
}
