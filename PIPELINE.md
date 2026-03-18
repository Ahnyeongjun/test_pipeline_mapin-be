# Mapin Pipeline 로직 정리

## 전체 흐름

```
POST /api/ingest?url=...
        │
        ▼
   [ingestJob]
  URL → YouTube 메타데이터 fetch → contents 저장
        │
        └── ContentIngestedEvent 발행
                │
                ▼
          [analysisJob]
        GPT로 관점 분류 (category / perspectiveLevel / perspectiveStakeholder)
        → contents 업데이트
                │
                └── ContentAnalyzedEvent 발행
                        │
                ┌───────┴────────┐
          (db 전략)          (vector 전략)
                │                    │
                ▼                    ▼
       [recommendationJob]     [embeddingJob]
                           OpenAI로 텍스트 임베딩
                           → Qdrant upsert
                           → contents.vector_id 저장
                                    │
                                    └── ContentEmbeddedEvent 발행
                                                │
                                                ▼
                                       [recommendationJob]
                │                               │
                └───────────────────────────────┘
                                │
                                ▼
                  반대 관점 콘텐츠 후보 선정
                  → content_recommendations 저장
```

모든 Job 간 연결은 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 조합으로 처리한다.
트랜잭션 커밋 이후에 이벤트가 발행되므로 DB 반영이 보장된 상태에서 다음 Job이 실행된다.

---

## 도메인별 책임

### ingest
URL을 받아 YouTube 콘텐츠 메타데이터를 수집하고 DB에 저장한다.

| 클래스 | 역할 |
|--------|------|
| `IngestController` | `POST /api/ingest` 수신, JobLauncher로 ingestJob 실행 |
| `IngestTasklet` | videoId 추출 → 중복 확인 → 신규면 메타데이터 fetch 후 저장 → `ContentIngestedEvent` 발행 |
| `YoutubeMetadataClient` | YouTube Data API v3 `/videos` 호출 |
| `YoutubeSearchClient` | YouTube Data API v3 `/search` 호출 (recommendation fallback에서도 사용) |
| `YoutubeUrlParser` | URL에서 videoId 추출, canonical URL 생성 |

**중복 처리**: `canonical_url` unique 제약으로 이미 존재하는 콘텐츠면 fetch 없이 바로 이벤트만 발행.

---

### analysis
GPT를 통해 콘텐츠의 관점을 3가지 축으로 분류한다.

| 클래스 | 역할 |
|--------|------|
| `ContentIngestedEventHandler` | `ContentIngestedEvent` 수신 → analysisJob 실행 |
| `AnalysisTasklet` | content 조회 → GPT 분류 → `content.updatePerspective()` → `ContentAnalyzedEvent` 발행 |
| `GptPerspectiveClassifier` | OpenAI Structured Output으로 JSON 스키마 기반 분류 (`!test` 프로파일) |
| `MockPerspectiveClassifier` | 고정값 반환 (`test` 프로파일) |

**분류 결과** (contents 테이블에 저장):
- `category`: 정치 / 경제 / 사회 / 생활·문화 / IT·과학 / 세계 / 연예 / 스포츠
- `perspective_level`: 사건(무슨 일) / 원인(왜 발생) / 구조(시스템 문제)
- `perspective_stakeholder`: 정부 / 전문가 / 시민 / 기업 / 국제

---

### embedding
텍스트를 벡터로 변환하여 Qdrant에 저장한다. `vector` 전략에서만 활성화된다.

| 클래스 | 역할 |
|--------|------|
| `ContentAnalyzedEventHandler` | `ContentAnalyzedEvent` 수신 → embeddingJob 실행 |
| `EmbeddingTasklet` | content 조회 → 임베딩 생성 → Qdrant upsert → `content.updateEmbedding()` → `ContentEmbeddedEvent` 발행 |
| `OpenAiEmbeddingClient` | OpenAI Embeddings API 호출 (`!test` 프로파일) |
| `QdrantVectorStoreClient` | Qdrant REST API upsert / search (`!test` 프로파일) |

**포인트 ID**: Qdrant 포인트 ID로 `content.id`를 그대로 사용하여 벡터 검색 결과를 DB 조회로 바로 역참조할 수 있다.

---

### recommendation
반대 관점 콘텐츠를 찾아 `content_recommendations` 테이블에 저장한다.

| 클래스 | 역할 |
|--------|------|
| `ContentAnalyzedEventHandler` | `ContentAnalyzedEvent` 수신 → recommendationJob 실행 (`db` 전략 전용) |
| `ContentEmbeddedEventHandler` | `ContentEmbeddedEvent` 수신 → recommendationJob 실행 (`vector` 전략 전용) |
| `RecommendationTasklet` | 전략 실행 → 후보 부족 시 fallback → 결과 저장 |
| `DbRecommendationStrategy` | SQL 필터: 같은 `category` + 다른 `perspective_stakeholder` |
| `VectorRecommendationStrategy` | 벡터 유사도로 후보 좁힌 뒤 다른 `perspective_stakeholder` 필터 |

**Fallback**: 추천 후보가 1개 미만이면 `category` 키워드로 YouTube 검색 → videoId 로그 출력 (실제 ingest는 별도 트리거).

---

## 추천 전략 선택

`application.yml`의 `pipeline.recommendation.strategy` 값으로 제어한다.

| 값 | 흐름 | 특징 |
|----|------|------|
| `db` (기본값) | analysis 완료 → recommendation | Qdrant 불필요, 즉시 실행 |
| `vector` | analysis 완료 → embedding → recommendation | Qdrant 필요, 같은 주제 후보를 벡터로 1차 필터링 |

`vector` 전략에서 `VectorRecommendationStrategy`, `ContentEmbeddedEventHandler` 빈이 `@ConditionalOnProperty`로 활성화된다.

---

## 이벤트 흐름 요약

| 이벤트 | 발행 위치 | 구독 위치 |
|--------|-----------|-----------|
| `ContentIngestedEvent` | `IngestTasklet` | `analysis.ContentIngestedEventHandler` |
| `ContentAnalyzedEvent` | `AnalysisTasklet` | `embedding.ContentAnalyzedEventHandler` (vector 전략) |
| | | `recommendation.ContentAnalyzedEventHandler` (db 전략) |
| `ContentEmbeddedEvent` | `EmbeddingTasklet` | `recommendation.ContentEmbeddedEventHandler` (vector 전략) |

---

## 주요 엔티티

### contents
| 컬럼 | 설명 |
|------|------|
| `canonical_url` | `https://www.youtube.com/watch?v={videoId}` 형태의 정규화 URL (unique) |
| `category` | GPT 분류 카테고리 |
| `perspective_level` | 관점 깊이 (사건/원인/구조) |
| `perspective_stakeholder` | 관점 주체 (정부/전문가/시민/기업/국제) |
| `vector_id` | Qdrant 포인트 ID (= content.id) |
| `embedding_model` | 임베딩 생성에 사용한 모델명 |

### content_recommendations
| 컬럼 | 설명 |
|------|------|
| `source_content_id` | 기준 콘텐츠 |
| `target_content_id` | 추천된 반대 관점 콘텐츠 |
| `strategy` | 사용된 전략 (`db` or `vector`) |
