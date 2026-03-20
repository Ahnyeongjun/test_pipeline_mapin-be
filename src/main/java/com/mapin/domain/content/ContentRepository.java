package com.mapin.domain.content;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {

    Optional<Content> findByCanonicalUrl(String canonicalUrl);

    List<Content> findAllByVectorIdIn(List<String> vectorIds);

    // 같은 카테고리의 자신 제외 전체 (score 계산용)
    List<Content> findByCategoryAndIdNot(String category, Long excludeId);

    // FALLBACK 처리: 같은 카테고리의 USER 콘텐츠만
    List<Content> findByCategoryAndSourceAndIdNot(String category, String source, Long excludeId);

    // 동일 카테고리 FALLBACK 중복 검색 방지
    boolean existsByCategoryAndSource(String category, String source);
}
