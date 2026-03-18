package com.mapin.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_recommendations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recommendations_source_target",
                columnNames = {"source_content_id", "target_content_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_content_id", nullable = false)
    private Long sourceContentId;

    @Column(name = "target_content_id", nullable = false)
    private Long targetContentId;

    /**
     * 반대 관점 점수.
     * 2: 강한 반대관점 (perspectiveLevel + stakeholder 모두 다름)
     * 1: 약한 반대관점 (stakeholder만 다름)
     * 0: 같은 관점 (저장 안 함)
     */
    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "strategy", nullable = false, length = 20)
    private String strategy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ContentRecommendation(Long sourceContentId, Long targetContentId, int score, String strategy) {
        this.sourceContentId = sourceContentId;
        this.targetContentId = targetContentId;
        this.score = score;
        this.strategy = strategy;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
