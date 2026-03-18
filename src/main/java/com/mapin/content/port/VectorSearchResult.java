package com.mapin.content.port;

import java.util.Map;

public record VectorSearchResult(
        String vectorId,
        double similarityScore,
        Map<String, Object> metadata
) {
}
