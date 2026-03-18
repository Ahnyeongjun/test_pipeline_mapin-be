package com.mapin.embedding.client;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final OpenAIClient openAIClient;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @Override
    public List<Float> embed(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(toEmbeddingModel(embeddingModel))
                .input(EmbeddingCreateParams.Input.ofString(text))
                .build();

        CreateEmbeddingResponse response = openAIClient.embeddings().create(params);

        return response.data().stream()
                .findFirst()
                .map(e -> e.embedding().stream()
                        .map(d -> d.floatValue())
                        .toList())
                .orElseThrow(() -> new IllegalStateException("임베딩 결과가 비어 있습니다."));
    }

    @Override
    public String modelName() {
        return embeddingModel;
    }

    private EmbeddingModel toEmbeddingModel(String name) {
        return switch (name) {
            case "text-embedding-3-small" -> EmbeddingModel.TEXT_EMBEDDING_3_SMALL;
            case "text-embedding-3-large" -> EmbeddingModel.TEXT_EMBEDDING_3_LARGE;
            case "text-embedding-ada-002" -> EmbeddingModel.TEXT_EMBEDDING_ADA_002;
            default -> throw new IllegalArgumentException("지원하지 않는 임베딩 모델: " + name);
        };
    }
}
