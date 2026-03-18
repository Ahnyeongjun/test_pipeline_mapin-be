package com.mapin.content.port;

import java.util.List;
import java.util.Map;

public interface VectorStoreClient {

    void upsert(String id, List<Float> vector, Map<String, Object> metadata);

    List<VectorSearchResult> searchById(String vectorId, int topK);
}
