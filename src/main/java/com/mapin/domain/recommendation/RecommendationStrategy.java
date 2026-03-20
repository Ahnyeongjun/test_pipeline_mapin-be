package com.mapin.domain.recommendation;

import com.mapin.domain.content.Content;
import java.util.List;

public interface RecommendationStrategy {

    /**
     * 주어진 콘텐츠와 관계를 맺을 후보 목록을 반환한다.
     * score 계산 및 필터링은 호출자(RecommendationTasklet)에서 수행한다.
     */
    List<Content> getCandidates(Content content);
}
