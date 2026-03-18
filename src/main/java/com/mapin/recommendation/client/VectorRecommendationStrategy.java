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
 * 벡터 기반 추천: 같은 주제 후보를 벡터 유사도로 좁힌 뒤
 * perspectiveStakeholder 가 다른 콘텐츠를 반환.
 */
@Component("vectorRecommendation")
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@RequiredArgsConstructor
@Slf4j
public class VectorRecommendationStrategy implements RecommendationStrategy {

    private static final int VECTOR_SEARCH_LIMIT = 30;

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final ContentRepository contentRepository;

    @Override
    public List<Content> recommend(Content content, int limit) {
        if (content.getPerspectiveStakeholder() == null) {
            log.warn("[VectorRecommend] stakeholder 미분류 contentId={}", content.getId());
            return List.of();
        }

        String text = "[TITLE]\n%s\n\n[DESCRIPTION]\n%s".formatted(
                nullSafe(content.getTitle()), nullSafe(content.getDescription()));

        List<Float> queryVector = embeddingClient.embed(text);
        List<String> vectorIds = vectorStoreClient.search(queryVector, VECTOR_SEARCH_LIMIT);

        if (vectorIds.isEmpty()) {
            return List.of();
        }

        List<Content> candidates = contentRepository.findAllByVectorIdIn(vectorIds);

        return candidates.stream()
                .filter(c -> !Objects.equals(c.getId(), content.getId()))
                .filter(c -> !Objects.equals(c.getPerspectiveStakeholder(), content.getPerspectiveStakeholder()))
                .limit(limit)
                .toList();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
