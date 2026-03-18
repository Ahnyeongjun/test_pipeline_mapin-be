package com.mapin.recommendation.client;

import com.mapin.common.domain.Content;
import java.util.List;

public interface RecommendationStrategy {

    /**
     * 주어진 콘텐츠에 대해 반대 관점 콘텐츠 후보를 반환한다.
     *
     * @param content 기준 콘텐츠
     * @param limit   최대 반환 개수
     * @return 추천 후보 목록 (빈 리스트 가능)
     */
    List<Content> recommend(Content content, int limit);
}
