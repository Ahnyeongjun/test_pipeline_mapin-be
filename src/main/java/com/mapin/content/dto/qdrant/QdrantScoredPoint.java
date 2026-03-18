package com.mapin.content.dto.qdrant;

import java.util.Map;

public record QdrantScoredPoint(
        Object id,
        double score,
        Map<String, Object> payload
) {
}
