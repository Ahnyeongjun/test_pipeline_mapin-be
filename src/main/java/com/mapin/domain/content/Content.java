package com.mapin.domain.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contents", uniqueConstraints = {
    @UniqueConstraint(name = "uk_contents_canonical_url", columnNames = "canonical_url")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_url", nullable = false, length = 1000)
    private String canonicalUrl;

    @Column(nullable = false, length = 30)
    private String platform;

    @Column(name = "external_content_id", nullable = false, length = 100)
    private String externalContentId;

    @Column(nullable = false, length = 500)
    private String title;

    @Lob
    private String description;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "channel_title", length = 255)
    private String channelTitle;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "youtube_category_id", length = 20)
    private String youtubeCategoryId;

    @Column(length = 50)
    private String duration;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "vector_id", length = 200)
    private String vectorId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "perspective_level", length = 30)
    private String perspectiveLevel;

    @Column(name = "perspective_stakeholder", length = 30)
    private String perspectiveStakeholder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", columnDefinition = "jsonb")
    private List<String> keywords;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "tone", length = 50)
    private String tone;

    @Column(name = "bias_level", length = 20)
    private String biasLevel;

    @Column(name = "is_opinionated")
    private Boolean isOpinionated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 콘텐츠 유입 경로.
     * USER: 사용자가 직접 ingest한 콘텐츠 (추천 source가 됨)
     * FALLBACK: 추천 후보 부족 시 YouTube 검색으로 자동 수집된 콘텐츠 (추천 pool 역할만)
     */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /**
     * 파이프라인 처리 상태 (Saga 상태 머신).
     * INGESTED → ANALYZED → (EMBEDDED →) COMPLETED
     * 실패: ANALYSIS_FAILED | EMBEDDING_FAILED | RECOMMENDATION_FAILED
     */
    @Column(name = "pipeline_status", length = 30)
    private String pipelineStatus;

    @Builder
    public Content(String canonicalUrl, String platform, String externalContentId,
            String title, String description, String thumbnailUrl, String channelTitle,
            OffsetDateTime publishedAt, String youtubeCategoryId, String duration,
            Long viewCount, String status, String source) {
        this.canonicalUrl = canonicalUrl;
        this.platform = platform;
        this.externalContentId = externalContentId;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.channelTitle = channelTitle;
        this.publishedAt = publishedAt;
        this.youtubeCategoryId = youtubeCategoryId;
        this.duration = duration;
        this.viewCount = viewCount;
        this.status = status;
        this.source = source;
    }

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void updatePipelineStatus(String pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public void updateEmbedding(String embeddingModel, String vectorId) {
        this.embeddingModel = embeddingModel;
        this.vectorId = vectorId;
    }

    public void updatePerspective(String category, String perspectiveLevel, String perspectiveStakeholder,
            List<String> keywords, String summary, String tone, String biasLevel, boolean isOpinionated) {
        this.category = category;
        this.perspectiveLevel = perspectiveLevel;
        this.perspectiveStakeholder = perspectiveStakeholder;
        this.keywords = keywords;
        this.summary = summary;
        this.tone = tone;
        this.biasLevel = biasLevel;
        this.isOpinionated = isOpinionated;
    }
}
