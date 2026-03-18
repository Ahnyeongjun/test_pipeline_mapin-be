package com.mapin.content.infrastructure;

import com.mapin.content.dto.openai.OpenAiEmbeddingResponse;
import com.mapin.content.port.EmbeddingClient;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient openAiRestClient;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${openai.api-key:}")
    private String apiKey;

    public OpenAiEmbeddingClient() {
        this.openAiRestClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        validateText(text);

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", text
        );

        OpenAiEmbeddingResponse response = openAiRestClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(OpenAiEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI 임베딩 응답이 비어 있습니다.");
        }

        List<Double> embedding = response.data().get(0).embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("OpenAI 임베딩 벡터가 비어 있습니다.");
        }

        return embedding.stream()
                .map(Double::floatValue)
                .toList();
    }

    @Override
    public String modelName() {
        return embeddingModel;
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩할 텍스트가 비어 있습니다.");
        }
    }
}
