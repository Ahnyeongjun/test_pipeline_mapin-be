package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentRecommendationResponse;
import com.mapin.content.dto.RecommendationCandidate;
import com.mapin.content.port.VectorSearchResult;
import com.mapin.content.port.VectorStoreClient;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 파이프라인 B: Qdrant 벡터 유사도 검색 + 관점 라벨 필터링으로 추천.
 * DB에 콘텐츠가 충분히 쌓인 후 사용.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorRecommendationService implements RecommendationStrategy {

    private final ContentRepository contentRepository;
    private final VectorStoreClient vectorStoreClient;
    private final CandidateValidationService candidateValidationService;

    @Override
    public List<ContentRecommendationResponse> recommend(Content source, int topK) {
        if (source.getVectorId() == null || source.getVectorId().isBlank()) {
            throw new IllegalStateException("임베딩되지 않은 콘텐츠입니다. id=" + source.getId());
        }

        List<VectorSearchResult> searchResults = vectorStoreClient.searchById(source.getVectorId(), topK + 5);

        List<String> candidateVectorIds = searchResults.stream()
                .map(VectorSearchResult::vectorId)
                .filter(vectorId -> !vectorId.equals(source.getVectorId()))
                .toList();

        if (candidateVectorIds.isEmpty()) {
            return List.of();
        }

        Map<String, Content> contentByVectorId = contentRepository.findAllByVectorIdIn(candidateVectorIds).stream()
                .collect(Collectors.toMap(Content::getVectorId, Function.identity()));

        log.debug("Vector recommendation: found {} vector candidates for contentId={}",
                candidateVectorIds.size(), source.getId());

        return searchResults.stream()
                .filter(result -> !result.vectorId().equals(source.getVectorId()))
                .map(result -> {
                    Content candidate = contentByVectorId.get(result.vectorId());
                    if (candidate == null) return null;
                    return candidateValidationService.validate(source, candidate, result.similarityScore());
                })
                .filter(Objects::nonNull)
                .filter(RecommendationCandidate::qualified)
                .sorted(Comparator.comparing(RecommendationCandidate::finalScore).reversed())
                .limit(topK)
                .map(this::toResponse)
                .toList();
    }

    private ContentRecommendationResponse toResponse(RecommendationCandidate candidate) {
        Content c = candidate.content();
        return new ContentRecommendationResponse(
                c.getId(),
                c.getCanonicalUrl(),
                c.getTitle(),
                c.getThumbnailUrl(),
                c.getChannelTitle(),
                c.getPublishedAt(),
                c.getCategory(),
                c.getPerspectiveLevel(),
                c.getPerspectiveStakeholder(),
                candidate.topicSimilarity(),
                candidate.perspectiveDistance(),
                candidate.finalScore()
        );
    }
}
