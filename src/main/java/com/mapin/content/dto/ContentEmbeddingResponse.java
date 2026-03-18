package com.mapin.content.dto;

public record ContentEmbeddingResponse(
        Long id,
        String embeddingModel,
        String vectorId,
        String embeddingText
) {
}
