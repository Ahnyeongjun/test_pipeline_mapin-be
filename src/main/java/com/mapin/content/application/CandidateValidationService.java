package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.dto.RecommendationCandidate;
import org.springframework.stereotype.Service;

@Service
public class CandidateValidationService {

    public RecommendationCandidate validate(Content source, Content candidate, double topicSimilarity) {
        int distance = calculateDistance(source, candidate);

        double perspectiveScore = switch (distance) {
            case 2 -> 1.0;
            case 1 -> 0.7;
            default -> 0.0;
        };

        double qualityScore = calculateQualityScore(candidate);

        boolean qualified = topicSimilarity >= 0.75 && distance >= 1;

        double finalScore = 0.6 * topicSimilarity
                + 0.25 * perspectiveScore
                + 0.15 * qualityScore;

        return new RecommendationCandidate(
                candidate,
                topicSimilarity,
                distance,
                perspectiveScore,
                qualityScore,
                finalScore,
                qualified
        );
    }

    private int calculateDistance(Content source, Content candidate) {
        int distance = 0;
        if (!safeEquals(source.getPerspectiveLevel(), candidate.getPerspectiveLevel())) {
            distance++;
        }
        if (!safeEquals(source.getPerspectiveStakeholder(), candidate.getPerspectiveStakeholder())) {
            distance++;
        }
        return distance;
    }

    private double calculateQualityScore(Content candidate) {
        Long viewCount = candidate.getViewCount();
        if (viewCount == null) return 0.3;
        if (viewCount >= 1_000_000) return 1.0;
        if (viewCount >= 100_000) return 0.8;
        if (viewCount >= 10_000) return 0.6;
        if (viewCount >= 1_000) return 0.4;
        return 0.2;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
