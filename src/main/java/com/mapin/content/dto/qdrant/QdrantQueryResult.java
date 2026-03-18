package com.mapin.content.dto.qdrant;

import java.util.List;

public record QdrantQueryResult(List<QdrantScoredPoint> points) {
}
