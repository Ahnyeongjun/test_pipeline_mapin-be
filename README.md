# Mapin Pipeline

`Mapin Pipeline`은 다양한 관점의 뉴스·영상 콘텐츠를 추천하는 Spring Boot 백엔드입니다.

사용자가 YouTube URL을 입력하면, 서버는 영상 메타데이터를 수집하고 GPT로 관점을 분류한 뒤 반대 관점의 콘텐츠를 추천합니다. 추천 방식은 DB 기반과 벡터 유사도 기반 두 가지 전략을 지원하며, 후보가 부족할 경우 YouTube 검색을 통해 fallback 콘텐츠를 자동으로 수집합니다.

## 기술 스택

- Java 21
- Spring Boot 3
- Spring Batch
- Gradle
- PostgreSQL
- Qdrant (벡터 DB, `vector` 전략 사용 시)
- Spring Data JPA
- Docker Compose
- OpenAI API (GPT, Embeddings)
- YouTube Data API v3
- springdoc-openapi 2.8.6 (Swagger UI)

## 아키텍처

패키지는 도메인 기능 단위로 구성되어 있습니다.

- `ingest` — URL 수신, YouTube 메타데이터 수집, `contents` 저장
- `analysis` — GPT로 관점 분류 (`category`, `perspectiveLevel`, `perspectiveStakeholder`)
- `embedding` — OpenAI 임베딩 생성 → Qdrant upsert (`vector` 전략 전용)
- `recommendation` — 반대 관점 콘텐츠 선정 → `content_recommendations` 저장
- `common` — 도메인 엔티티, 공통 설정 (비동기, OpenAI, Swagger)

### 처리 흐름

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
        GPT로 관점 분류
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
                                    │
                                    └── ContentEmbeddedEvent 발행
                                                │
                                                ▼
                                       [recommendationJob]
```

모든 Job 간 연결은 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 조합으로 처리합니다.

### 추천 전략

`PIPELINE_RECOMMENDATION_STRATEGY` 환경변수로 제어합니다.

| 값 | 흐름 | 특징 |
|----|------|------|
| `db` (기본값) | analysis 완료 → recommendation | Qdrant 불필요, 즉시 실행 |
| `vector` | analysis 완료 → embedding → recommendation | Qdrant 필요, 벡터 유사도로 1차 후보 필터링 |

### Fallback

후보 콘텐츠가 부족할 경우 category 키워드로 YouTube를 검색하여 `source=FALLBACK`으로 자동 ingest합니다. FALLBACK 콘텐츠는 추천 pool 역할만 하며, 다른 콘텐츠의 추천 source가 되지 않습니다.

## 관점 분류 기준

### category
정치 / 경제 / 사회 / 생활·문화 / IT·과학 / 세계 / 연예 / 스포츠

### perspectiveLevel
| 값 | 의미 |
|----|------|
| 사건 | 무슨 일이 있었는지 |
| 원인 | 왜 발생했는지 |
| 구조 | 시스템·구조적 문제 |

### perspectiveStakeholder
정부 / 전문가 / 시민 / 기업 / 국제

## 환경 변수

프로젝트 루트에 `.env` 파일을 생성하세요. 기본 템플릿은 `.env.sample`을 참고합니다.

```env
# DB
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/mapin
SPRING_DATASOURCE_USERNAME=mapin
SPRING_DATASOURCE_PASSWORD=mapin

# OpenAI
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4.1
OPENAI_EMBEDDING_MODEL=text-embedding-3-small

# YouTube Data API v3
YOUTUBE_API_KEY=AIza...

# Qdrant (vector 전략 사용 시)
QDRANT_HOST=localhost
QDRANT_PORT=6333
QDRANT_COLLECTION_NAME=mapin_contents
QDRANT_VECTOR_SIZE=1536
QDRANT_DISTANCE=Cosine

# 추천 전략: db(기본) 또는 vector
PIPELINE_RECOMMENDATION_STRATEGY=db
```

## Docker Compose로 로컬 실행

PostgreSQL과 Qdrant를 함께 실행합니다.

```bash
docker compose up -d
```

실행 상태 확인:

```bash
docker compose ps
```

헬스 체크:

```bash
curl http://localhost:8080/actuator/health
```

종료:

```bash
docker compose down
```

### Postgres 권한/유저 오류가 날 때

아래와 같은 오류가 나오면:

```text
FATAL: role "mapin" does not exist
```

대부분은 이전 volume이 다른 계정으로 초기화된 경우입니다. volume을 지우고 다시 실행하세요.

```bash
docker compose down -v
docker compose up -d
```

IntelliJ에서 Docker DB에 붙을 때:

- Host: `localhost`
- Port: `5433`
- Database: `mapin`
- User: `mapin`
- Password: `.env`의 `SPRING_DATASOURCE_PASSWORD`

## 앱만 로컬 실행

PostgreSQL이 이미 실행 중이라면 앱만 직접 실행할 수 있습니다.

```bash
./gradlew bootRun
```

## 빌드 및 테스트

```bash
./gradlew test
./gradlew bootJar
```

## API

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### 콘텐츠 ingest

```bash
curl -X POST "http://localhost:8080/api/ingest?url=https://www.youtube.com/watch?v=rcn1BOyEZxM"
```

예시 응답:

```json
{
  "jobId": "a246be64-ca2b-4f59-9305-7b1f1d7b71d9",
  "contentId": 42,
  "status": "ACCEPTED"
}
```

### 추천 목록 조회

```bash
curl http://localhost:8080/api/recommendations/{contentId}
```

완료 응답 예시:

```json
[
  {
    "contentId": 261,
    "canonicalUrl": "https://www.youtube.com/watch?v=zSeP_UGWlk4",
    "title": "[뉴스특보] 트럼프 분노한 날, 호르무즈 폭격...'항만 전쟁' 격화 / 연합뉴스TV",
    "thumbnailUrl": "https://i.ytimg.com/vi/zSeP_UGWlk4/hqdefault.jpg",
    "channelTitle": "연합뉴스TV",
    "publishedAt": "2026-03-18T05:42:07Z",
    "category": "세계",
    "perspectiveLevel": "원인",
    "perspectiveStakeholder": "전문가",
    "topicSimilarity": 0.5913,
    "perspectiveDistance": 2,
    "finalScore": 0.6948
  }
]
```

## 현재 MVP 메모

- 관점 분류(`perspectiveLevel`, `perspectiveStakeholder`)는 GPT Structured Output 기반입니다.
- 추천 점수(`finalScore`)는 topicSimilarity + perspectiveDistance 가중 합산으로 계산합니다.
- 전체 ingest·추천 이력을 저장하여, 이후 누적 데이터 기반으로 추천 기준을 고도화할 수 있도록 설계했습니다.
- GPT TPM 제한 방지를 위해 fallback ingest 간 1.5초 딜레이와 비동기 스레드 풀 제한(2)을 적용합니다.

## 다음 단계 제안

- YouTube Search API 일일 quota 소진 문제 → 내부 DB 우선 탐색 후 부족 시 검색 호출로 전환
- 분류 축(perspectiveLevel/Stakeholder) 고정 방식 → LLM 추천 결과 누적 후 데이터 기반으로 기준 도출
- 실패한 Job에 대한 retry / timeout 정책 추가
- Flyway 또는 Liquibase 기반 명시적 스키마 관리 도입
