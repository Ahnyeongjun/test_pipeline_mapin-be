package com.mapin.content.dto;

import com.mapin.content.domain.Content;

public record RecommendationCandidate(
        Content content,
        double topicSimilarity,
        int perspectiveDistance,
        double perspectiveScore,
        double qualityScore,
        double finalScore,
        boolean qualified
) {
}
