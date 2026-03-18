package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.dto.ContentRecommendationResponse;
import java.util.List;

public interface RecommendationStrategy {

    List<ContentRecommendationResponse> recommend(Content source, int topK);
}
