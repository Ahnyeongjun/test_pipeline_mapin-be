package com.mapin.recommendation.client;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.embedding.client.EmbeddingClient;
import com.mapin.embedding.client.VectorStoreClient;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 벡터 기반 추천: 벡터 유사도로 같은 주제 후보를 좁혀 반환.
 * score 계산 및 필터링은 RecommendationTasklet에서 수행.
 */
@Component("vectorRecommendation")
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@RequiredArgsConstructor
@Slf4j
public class VectorRecommendationStrategy implements RecommendationStrategy {

    private static final int VECTOR_SEARCH_LIMIT = 50;

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final ContentRepository contentRepository;

    @Override
    public List<Content> getCandidates(Content content) {
        String text = "[TITLE]\n%s\n\n[DESCRIPTION]\n%s".formatted(
                Objects.toString(content.getTitle(), ""), Objects.toString(content.getDescription(), ""));

        List<Float> queryVector = embeddingClient.embed(text);
        List<String> vectorIds = vectorStoreClient.search(queryVector, VECTOR_SEARCH_LIMIT);

        if (vectorIds.isEmpty()) {
            return List.of();
        }

        return contentRepository.findAllByVectorIdIn(vectorIds).stream()
                .filter(c -> !Objects.equals(c.getId(), content.getId()))
                .toList();
    }

}
