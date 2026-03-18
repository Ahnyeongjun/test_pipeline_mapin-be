package com.mapin.common.domain;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public Content(String canonicalUrl, String platform, String externalContentId,
            String title, String description, String thumbnailUrl, String channelTitle,
            OffsetDateTime publishedAt, String youtubeCategoryId, String duration,
            Long viewCount, String status) {
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

    public void updateEmbedding(String embeddingModel, String vectorId) {
        this.embeddingModel = embeddingModel;
        this.vectorId = vectorId;
    }

    public void updatePerspective(String category, String perspectiveLevel, String perspectiveStakeholder) {
        this.category = category;
        this.perspectiveLevel = perspectiveLevel;
        this.perspectiveStakeholder = perspectiveStakeholder;
    }
}
