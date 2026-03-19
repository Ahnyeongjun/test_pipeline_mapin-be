# 추천 흐름 요약

### 1. 사용자가 YouTube URL 입력

- 사용자가 영상 URL 하나를 입력한다.

### 2. ingest

- URL에서 `videoId`를 추출한다.
- YouTube Data API v3로 메타데이터를 조회한다.
- `contents` 테이블에 제목, 설명, 썸네일, 채널명, 게시일, 조회수 등을 저장한다.
- 이미 존재하는 URL이면 fetch 없이 바로 다음 단계로 넘어간다.

### 3. AI 관점 분류

- GPT로 콘텐츠를 분석해 아래 정보를 추출하고 `contents` 테이블에 저장한다.
  - `category`, `perspectiveLevel`, `perspectiveStakeholder`
  - `keywords`, `summary`, `tone`, `biasLevel`, `isOpinionated`
- 이 시점부터 각 콘텐츠는 **카테고리 + 관점 라벨 + 분석 정보**를 갖는다.

### 4. 내부 DB 기반 추천 시도

- 같은 `category` + 다른 `perspectiveStakeholder` 조건으로 후보를 탐색한다.
- 각 후보에 대해 주제 유사도(`topicSimilarity`)와 관점 거리(`perspectiveDistance`)로 점수를 계산한다.
- 점수 기준 상위 2~3개를 `content_recommendations`에 저장한다.

### 5. 내부 후보가 충분하면 바로 완료

- 기준을 통과한 후보가 충분하면 추천 결과를 저장하고 완료한다.

### 6. 내부 후보가 부족하면 fallback 실행

- 후보 수가 1개 미만이면 fallback으로 넘어간다.
- `keywords`로 YouTube Search API를 호출해 외부 후보 영상 목록을 확보한다.
- 검색된 영상을 `source=FALLBACK`으로 ingest → analysis 순으로 처리한다.
- FALLBACK 콘텐츠는 추천 pool 역할만 하며, 다른 콘텐츠의 추천 source가 되지 않는다.

### 7. 추천 재시도 및 최종 저장

- 새로 수집된 후보를 포함해 다시 점수를 계산하고 상위 후보를 선정한다.
- 후보가 충분하면 저장 후 완료, 여전히 부족하면 있는 것만 저장한다.

---

# 위 방식의 예상되는 어려움

### 1. YouTube Data API v3 사용량 한계

fallback 실행 시 생각보다 quota를 많이 소비한다. 하루 10,000건 제공이지만 fallback 한 번에 최대 50~100건을 소비할 수 있다.

[YouTube API 할당량 확장 신청](https://support.google.com/youtube/contact/yt_api_form?hl=ko)

→ 팀원 계정을 여러 개 사용하는 방식으로 단기 대응 가능하지만, 근본적인 해결은 아니다.

### 2. 관점 축만으로는 다양한 추천이 어려움

`perspectiveLevel`(사건/원인/구조), `perspectiveStakeholder`(정부/전문가/시민/기업/국제)로 반대 관점을 정의했지만, 내부 DB 콘텐츠가 특정 조합에 몰릴 경우 관점 조건을 충족해도 실질적으로 비슷한 성격의 콘텐츠만 추천된다.

---

# 현재 진행 중인 사항 및 아이디어 (2026/03/19)

## 문제

- YouTube Search API 하루 제공량이 생각보다 빠르게 소진된다
- `perspectiveLevel`, `perspectiveStakeholder` 등 임의로 정한 축으로 구현해봤지만 결과가 유의미하지 않다

## 아이디어

analysis 단계에서 이미 GPT로 `keywords`, `summary`, `tone`, `biasLevel`, `isOpinionated`까지 뽑아두었으므로, 추천을 위해 LLM을 추가 호출하는 것은 비효율적이다. 이미 추출된 정보를 점수 계산에 더 적극적으로 활용하는 방향으로 개선한다.

- `keywords` 겹치는 정도 → 같은 주제인지 확인
- `tone` 차이 → 논조가 다른 콘텐츠 우선
- `biasLevel` → 편향 수준 고려
- `isOpinionated` → 사실 보도 vs 의견 콘텐츠 구분

이를 통해 LLM 추가 호출 없이 점수 계산 로직 개선만으로 추천 품질을 높인다.

- 기준 도출 방향: 추천 결과를 DB에 누적 → 데이터 검토 → 점수 가중치 조정
- 추후 내부 DB가 충분히 확장되면 → 내부 DB 우선 탐색 → 부족한 것만 fallback 이용

## 진행 중

> 점수 계산 로직 개선 중
