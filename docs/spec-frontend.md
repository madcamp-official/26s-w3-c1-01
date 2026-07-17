# 기능 명세서 — 담당: 프론트엔드 (Person A)

> 전체 개요/데이터 모델의 원본은 [ai-contents-expansion-spec.md](./ai-contents-expansion-spec.md) 참고.
> 이 문서는 프론트엔드 담당자가 **백엔드 완성을 기다리지 않고 병렬로 작업**할 수 있도록,
> API 계약(Contract)과 Mock 데이터를 포함한다.

## 1. 담당 범위

- 지문 노출, 선택지 UI, 응답 제출
- 정답 공개 연출(정오답 / 근거 / 코멘트)
- 점수·진행률 표시, 결과 요약 화면
- (선택) 카테고리·난이도 필터 시작 화면

담당 기능 ID: `FE-01` ~ `FE-09` (아래 3장 참고)

---

## 2. API 계약 (Backend와 합의된 인터페이스)

⚠️ 이 계약은 백엔드 담당자(Person B)의 [spec-backend-ai.md](./spec-backend-ai.md)와 **동일해야 함**.
계약을 변경해야 하면 반드시 상대방과 합의 후 양쪽 문서를 함께 수정한다.

### 2.1 퀴즈 세트 생성
```
POST /quiz-sessions
Request  { "category": "역사", "difficulty": "MEDIUM", "count": 10 }
Response { "session_id": "sess_123", "total": 10 }
```

### 2.2 다음 카드(지문만) 조회 — 정답 필드 절대 포함 안 됨
```
GET /quiz-sessions/{session_id}/next-card
Response {
  "card": {
    "id": "card_001",
    "category": "역사",
    "body": "1932년, 한 나라의 군대가 실제로 에뮤(타조 닮은 새)와의 전쟁을 벌였다. 농작물을 망치는 에뮤 떼를 막기 위해 기관총까지 동원한 정식 군사 작전을 펼쳤지만, 에뮤들이 흩어지고 빠르게 도망다니는 바람에 결국 군대가 패배를 인정하고 철수했다.",
    "difficulty": "MEDIUM"
  },
  "progress": { "current": 3, "total": 10 }
}
```
카드가 더 없을 경우: `{ "card": null, "progress": { "current": 10, "total": 10 } }`

### 2.3 응답 제출 및 채점 — 이 응답에만 정답 필드 포함
```
POST /quiz-sessions/{session_id}/answers
Request  { "card_id": "card_001", "user_choice": "REAL" }   // "REAL" | "FABRICATED"
Response {
  "card_id": "card_001",
  "is_correct": true,
  "correct_type": "REAL",
  "answer_title": "1932년 호주의 에뮤 전쟁",
  "answer_fact": "호주 정부가 밀농사를 망치는 에뮤 떼를 몰아내기 위해 기관총 부대를 투입했으나 실패하고 철수한 실제 군사작전.",
  "reveal_comment": "네, 진짜입니다. 심지어 의회에서까지 논의된 정식 군사작전이었어요.",
  "source_url": "https://en.wikipedia.org/wiki/Emu_War",
  "score": { "correct": 3, "total": 3 }
}
```

### 2.4 세션 결과 요약
```
GET /quiz-sessions/{session_id}/result
Response {
  "session_id": "sess_123",
  "total": 10,
  "correct": 7,
  "cards": [
    { "card_id": "card_001", "user_choice": "REAL", "correct_type": "REAL", "is_correct": true }
  ]
}
```

### 2.5 에러 응답

항상 200을 가정하지 말고 아래 케이스를 처리할 것.

| 상황 | 발생 API | 응답 |
|---|---|---|
| 존재하지 않는 세션 ID | next-card / answers / result | `404 { "error": "session_not_found" }` |
| 필터(category/difficulty)에 맞는 카드가 하나도 없음 | 퀴즈 세트 생성 | `422 { "error": "no_cards_available" }` |
| `user_choice`가 `REAL`/`FABRICATED`가 아님 | 응답 제출 | `400 { "error": "invalid_user_choice" }` |
| 세션이 기대하는 다음 카드가 아닌 `card_id` 제출 (진행 순서를 벗어난 경우) | 응답 제출 | `409 { "error": "unexpected_card_id", "expected": "card_001" }` |

---

## 3. 세부 기능 명세 (FE)

| 기능 ID | 기능명 | 상세 설명 |
|---|---|---|
| FE-01 | 카드 지문 노출 | `body`만 표시. 정답 관련 필드는 응답 전까지 화면 상태에 존재하지 않아야 함 |
| FE-02 | 선택지 UI | "실화 / AI 창작" 2지선다, 선택 전 재선택 가능 |
| FE-03 | 응답 제출 | 선택 즉시 `POST /answers` 호출 |
| FE-04 | 정답 공개 연출 | ✅/❌ → `answer_title` → `answer_fact` → `reveal_comment` 순차 노출 |
| FE-05 | 점수/진행 표시 | `score`, `progress` 값을 화면 상단에 표시 (예: 3/10) |
| FE-06 | 다음 카드 이동 | 정답 공개 후 "다음" 액션 → `GET /next-card` 재호출 |
| FE-07 | 결과 요약 화면 | `GET /result` 기반 총점 + 카드별 정오답 리스트, 카드 재열람 |
| FE-08 | 카테고리/난이도 필터 | 시작 화면에서 옵션 선택 → `POST /quiz-sessions` 파라미터로 전달 (선택 기능) |
| FE-09 | 오답 노출 방지 가드 | "지문 로드 상태"와 "정답 공개 상태"를 별도 state로 분리, `next-card` 응답에는 정답 필드가 아예 없으므로 실수로 렌더링될 수 없는 구조로 설계 |

---

## 4. 백엔드 없이 개발하기 위한 Mock 전략

백엔드가 준비되기 전까지 아래 Mock 데이터로 화면을 먼저 구현한다.

```json
// mock/next-card.json
{
  "card": {
    "id": "card_001",
    "category": "역사",
    "body": "1932년, 한 나라의 군대가 실제로 에뮤(타조 닮은 새)와의 전쟁을 벌였다. 농작물을 망치는 에뮤 떼를 막기 위해 기관총까지 동원한 정식 군사 작전을 펼쳤지만, 에뮤들이 흩어지고 빠르게 도망다니는 바람에 결국 군대가 패배를 인정하고 철수했다.",
    "difficulty": "MEDIUM"
  },
  "progress": { "current": 1, "total": 3 }
}
```
```json
// mock/answer.json
{
  "card_id": "card_001",
  "is_correct": true,
  "correct_type": "REAL",
  "answer_title": "1932년 호주의 에뮤 전쟁",
  "answer_fact": "호주 정부가 밀농사를 망치는 에뮤 떼를 몰아내기 위해 기관총 부대를 투입했으나 실패하고 철수한 실제 군사작전.",
  "reveal_comment": "네, 진짜입니다. 심지어 의회에서까지 논의된 정식 군사작전이었어요.",
  "source_url": "https://en.wikipedia.org/wiki/Emu_War",
  "score": { "correct": 1, "total": 1 }
}
```

API 클라이언트는 `next-card` / `answers` 호출부를 함수로 분리해두고, 개발 중엔 Mock JSON을 반환하도록 구현 → 통합 시점에 실제 fetch로 교체만 하면 되도록 한다.

---

## 5. 완료 기준 체크리스트

- [ ] 지문 노출 화면에서 정답 관련 정보가 어떤 경우에도 사전 노출되지 않음
- [ ] 실화/AI창작 선택 후 응답 제출 → 정답 공개 UI 정상 동작
- [ ] 점수/진행률이 카드 진행에 따라 정확히 갱신
- [ ] 결과 요약 화면에서 전체 카드 정오답 리스트 확인 가능
- [ ] Mock 데이터 기반 개발본이 실제 API 연동 시 별도 리팩터링 없이 교체됨
