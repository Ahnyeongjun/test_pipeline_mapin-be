package com.mapin.content.port;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);

    String modelName();
}
