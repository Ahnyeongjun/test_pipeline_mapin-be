package com.mapin.content.dto.openai;

import java.util.List;

public record OpenAiEmbeddingResponse(List<OpenAiEmbeddingData> data) {
}
