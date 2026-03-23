package com.mapin.domain.recommendation;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQL 기반 추천: coreKeywords 겹치는 콘텐츠를 반환.
 * score 계산 및 필터링은 RecommendationService에서 수행.
 */
@Component("dbRecommendation")
@RequiredArgsConstructor
@Slf4j
public class DbRecommendationStrategy implements RecommendationStrategy {

    private final ContentRepository contentRepository;

    @Override
    public List<Content> getCandidates(Content content) {
        List<String> coreKeywords = content.getCoreKeywords();
        if (coreKeywords == null || coreKeywords.isEmpty()) {
            log.warn("[DbRecommend] coreKeywords 없음 contentId={}", content.getId());
            return List.of();
        }
        return contentRepository.findByCoreKeywordsOverlap(toJsonArray(coreKeywords), content.getId());
    }

    private static String toJsonArray(List<String> list) {
        return "[" + list.stream()
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
    }
}
