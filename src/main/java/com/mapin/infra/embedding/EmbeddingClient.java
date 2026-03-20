package com.mapin.infra.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);

    String modelName();
}
