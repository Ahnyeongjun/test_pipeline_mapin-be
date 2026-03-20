package com.mapin.domain.recommendation;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQL 기반 추천: 같은 category의 콘텐츠 전체를 반환.
 * score 계산 및 필터링은 RecommendationTasklet에서 수행.
 */
@Component("dbRecommendation")
@RequiredArgsConstructor
@Slf4j
public class DbRecommendationStrategy implements RecommendationStrategy {

    private final ContentRepository contentRepository;

    @Override
    public List<Content> getCandidates(Content content) {
        if (content.getCategory() == null) {
            log.warn("[DbRecommend] category 미분류 contentId={}", content.getId());
            return List.of();
        }
        return contentRepository.findByCategoryAndIdNot(content.getCategory(), content.getId());
    }
}
