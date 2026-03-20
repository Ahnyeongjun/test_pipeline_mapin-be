### db 전략 (기본값)

ingest → analysis → recommendation
(source=USER)            │
├── 후보 있음(요부분 기준이 애매함) → content_recommendations 저장  (새로 들어온 콘텐츠와 기존 콘텐츠 간의 관계만 추가)
│
└── 후보 부족
│
▼
YouTube 검색 → ingest (source=FALLBACK)
│
▼
analysis까지만 실행 (recommendation 실행 안 함)
→ 추천 풀에 추가됨
│
▼
다음 추천 요청 시 후보로 활용

### vector 전략

ingest → analysis → embedding(백터디비) → recommendation
(source=USER)                       │
├── 후보 있음 → content_recommendations 저장 ( 새로 들어온 콘텐츠와 기존 콘텐츠 간의 관계만 추가)
│
└── 후보 부족
│
▼
YouTube 검색 → ingest (source=FALLBACK)
│
▼
analysis → embedding까지만 실행
→ Qdrant + DB에 후보로 추가됨
│
▼
다음 추천 요청 시 후보로 활용

### 현재 디비 느낌

content_recommendations는 관계를 보여주기 위한 양방향 테이블. (그래프 디비의 느낌)

source_content_id | target_content_id | score | strategy | created_at
1         |        2          |   2   |    db    | 2025-03-18
1         |        3          |   1   |    db    | 2025-03-18

### FALLBACK 로직

FALLBACK ingest → analysis → (embedding)
↓
같은 카테고리의 기존 모든 콘텐츠 조회
↓
F가 반대 관점이면 → 기존 모든 콘텐츠 → F 관계 추가
(content_recommendations에 target=F로 insert)

### 결론

**컨텐츠가 들어오면 메타데이터를 추출한다. (YOUTUBE API → OPENAI)**

→

**특정 기준 이상으로 가깝다고 판단되는 주제를 찾는다.**
(RDB: 같은 카테고리 기준)
(VectorDB: 백터 유사도)

→

**기존 같은 카테고리 상대로 모든 양방향 관계를 맺는다.**

강한 반대관점: 경제 / 구조 / 시민  (level도 다르고 stakeholder도 다름)
약한 반대관점: 경제 / 사건 / 시민  (stakeholder만 다름)
반대관점 아님: 경제 / 사건 / 정부  (똑같음)

level: 관점 수준

stakeholder: 이해관계자 수준

→

**부족하다면  FALLBACK을 탄다.  (이 FALLBACK은 사용자 입력으로만 돈다.)**

fallback은 youtube api 로 비슷한 카테고리들을 가져온다.