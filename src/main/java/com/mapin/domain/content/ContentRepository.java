package com.mapin.domain.content;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

    Optional<Content> findByCanonicalUrl(String canonicalUrl);

    List<Content> findAllByVectorIdIn(List<String> vectorIds);

    // 같은 카테고리의 자신 제외 전체 (coreKeywords 없을 때 fallback용)
    List<Content> findByCategoryAndIdNot(String category, Long excludeId);

    // coreKeywords 겹치는 콘텐츠 검색 (GIN 인덱스 활용)
    @Query(value = """
            SELECT * FROM contents c
            WHERE c.id != :excludeId
            AND c.core_keywords IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements_text(c.core_keywords) ck
                CROSS JOIN jsonb_array_elements_text(CAST(:coreKeywordsJson AS jsonb)) qk
                WHERE ck = qk
            )
            """, nativeQuery = true)
    List<Content> findByCoreKeywordsOverlap(
            @Param("coreKeywordsJson") String coreKeywordsJson,
            @Param("excludeId") Long excludeId);

    // FALLBACK 처리: coreKeywords 겹치는 USER 콘텐츠만
    @Query(value = """
            SELECT * FROM contents c
            WHERE c.id != :excludeId
            AND c.source = :source
            AND c.core_keywords IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements_text(c.core_keywords) ck
                CROSS JOIN jsonb_array_elements_text(CAST(:coreKeywordsJson AS jsonb)) qk
                WHERE ck = qk
            )
            """, nativeQuery = true)
    List<Content> findByCoreKeywordsOverlapAndSource(
            @Param("coreKeywordsJson") String coreKeywordsJson,
            @Param("source") String source,
            @Param("excludeId") Long excludeId);

    // 동일 카테고리 FALLBACK 중복 검색 방지
    boolean existsByCategoryAndSource(String category, String source);
}
