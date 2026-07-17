# 기능 명세서 — 담당: 백엔드 + AI 파이프라인 (Person B)

> 전체 개요/데이터 모델의 원본은 [ai-contents-expansion-spec.md](./ai-contents-expansion-spec.md) 참고.
> 이 문서는 백엔드+AI 파이프라인 담당자가 **프론트엔드 완성을 기다리지 않고 병렬로 작업**할 수 있도록,
> API 계약(Contract)과 데이터 모델, AI 생성/검증 로직을 포함한다.

## 1. 담당 범위

- 콘텐츠 카드 저장소 및 검수 워크플로
- 퀴즈 세션 생성·채점·결과 집계 API
- AI 파이프라인: 실화/AI창작 지문 생성, 정답 근거·코멘트 생성, 사실/개연성 검증

담당 기능 ID: `BE-01`~`BE-09`, `AI-01`~`AI-09` (아래 4~5장 참고)

---

## 2. API 계약 (Frontend와 합의된 인터페이스)

⚠️ 이 계약은 프론트엔드 담당자(Person A)의 [spec-frontend.md](./spec-frontend.md)와 **동일해야 함**.
계약을 변경해야 하면 반드시 상대방과 합의 후 양쪽 문서를 함께 수정한다.

### 2.1 퀴즈 세트 생성
```
POST /quiz-sessions
Request  { "category": "역사", "difficulty": "MEDIUM", "count": 10 }
Response { "session_id": "sess_123", "total": 10 }
```
구현 메모: `PUBLISHED` 상태 카드 중 조건에 맞는 것을 셔플해 `count`개 선정, 세션에 카드 순서 고정 저장.

### 2.2 다음 카드(지문만) 조회
```
GET /quiz-sessions/{session_id}/next-card
Response {
  "card": { "id": "card_001", "category": "역사", "body": "...", "difficulty": "MEDIUM" },
  "progress": { "current": 3, "total": 10 }
}
```
⚠️ 직렬화 시 `type`, `answer_title`, `answer_fact`, `reveal_comment`, `source_url` 필드는 **물리적으로 응답 스키마에서 제외**한다 (BE-08). ORM 엔티티를 그대로 반환하지 말고 반드시 별도 DTO를 사용할 것.

### 2.3 응답 제출 및 채점
```
POST /quiz-sessions/{session_id}/answers
Request  { "card_id": "card_001", "user_choice": "REAL" }
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
  "session_id": "sess_123", "total": 10, "correct": 7,
  "cards": [ { "card_id": "card_001", "user_choice": "REAL", "correct_type": "REAL", "is_correct": true } ]
}
```

### 2.5 에러 응답

| 상황 | 발생 API | 응답 |
|---|---|---|
| 존재하지 않는 세션 ID | next-card / answers / result | `404 { "error": "session_not_found" }` |
| 필터(category/difficulty)에 맞는 카드가 하나도 없음 | 퀴즈 세트 생성 | `422 { "error": "no_cards_available" }` |
| `user_choice`가 `REAL`/`FABRICATED`가 아님 | 응답 제출 | `400 { "error": "invalid_user_choice" }` |
| 세션이 기대하는 다음 카드가 아닌 `card_id` 제출 | 응답 제출 | `409 { "error": "unexpected_card_id", "expected": "card_001" }` |

---

## 3. 데이터 모델

### 3.1 ContentCard

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | 카드 고유 ID |
| `category` | string | 분류 태그 |
| `body` | text | 지문 텍스트 |
| `type` | enum(`REAL`,`FABRICATED`) | 정답 |
| `answer_title` | string | 정답 요약 |
| `answer_fact` | text | 정답 근거 상세 |
| `reveal_comment` | text | 공개 코멘트 |
| `source_url` | string? | 실화 카드 출처 |
| `difficulty` | enum(`EASY`,`MEDIUM`,`HARD`)? | 난이도 |
| `status` | enum(`DRAFT`,`VERIFIED`,`PUBLISHED`,`REJECTED`) | 검수 상태 |
| `created_at`/`updated_at` | datetime | |

### 3.2 QuizSession / Answer

| 필드 | 타입 | 설명 |
|---|---|---|
| `session_id` | string | |
| `card_ids` | string[] | 세션에 고정된 카드 순서 |
| `card_id` | string | 응답 대상 카드 |
| `user_choice` | enum(`REAL`,`FABRICATED`) | |
| `is_correct` | boolean | |
| `answered_at` | datetime | |

---

## 4. 세부 기능 명세 (BE)

| 기능 ID | 기능명 | 상세 설명 |
|---|---|---|
| BE-01 | 카드 조회 API | `GET /cards`(필터: category, difficulty), `GET /cards/{id}` — 정답 필드 제외 |
| BE-02 | 퀴즈 세트 생성 API | `POST /quiz-sessions` |
| BE-03 | 응답 제출 및 채점 API | `POST /quiz-sessions/{id}/answers` |
| BE-04 | 세션 결과 집계 API | `GET /quiz-sessions/{id}/result` |
| BE-05 | 콘텐츠 카드 저장소 | ContentCard CRUD, `status=PUBLISHED`만 사용자 API 노출 |
| BE-06 | AI 파이프라인 트리거 | 신규 카드 생성 요청 → AI 파이프라인 실행 → 결과 `DRAFT` 적재 |
| BE-07 | 검수 워크플로 | `DRAFT → VERIFIED/REJECTED → PUBLISHED` 상태 전이 관리 |
| BE-08 | 정답 유출 방지 | 조회용 DTO와 채점용 DTO를 분리 구현 (엔티티 직접 반환 금지) |
| BE-09 | 중복/재출제 방지 | 세션 내 카드 중복 금지, (선택) 사용자별 최근 풀이 이력 기반 제한 |

---

## 5. 세부 기능 명세 (AI 파이프라인)

| 기능 ID | 기능명 | 상세 설명 |
|---|---|---|
| AI-01 | 실화 소재 수집 | 실제 사건 소재(주제/연도/출처) 수집·선정 |
| AI-02 | 실화 지문 생성 | 사실 기반, 허구 첨가 없이 흥미 위주 문체로 각색 (`body` 생성) |
| AI-03 | AI창작 지문 생성 | 실화 카드와 동일 톤·분량으로 "있을 법한" 가상 사건 생성. 실존 고유명사·연도 재사용 금지 |
| AI-04 | 정답 근거 생성 | 실화: `answer_fact`+`source_url` / AI창작: 창작 모티프·그럴듯한 이유 |
| AI-05 | 공개 코멘트 생성 | `reveal_comment` 생성 (짧고 흥미로운 톤) |
| AI-06 | 사실 검증 (실화) | 연도·고유명사·사건 전개 오류 탐지, 실패 시 `REJECTED` |
| AI-07 | 개연성 검증 (창작) | 시대적 모순·논리 비약 여부 검증, 지나치게 허무맹랑하거나 실화와 구분 불가한 경우 재생성 |
| AI-08 | 난이도 태깅 | 실화/창작 구분 난이도를 추정해 `difficulty` 부여 |
| AI-09 | 결과 적재 | 생성·검증 완료 카드를 `DRAFT`/`VERIFIED` 상태로 BE 저장소에 전달 |

### 5.1 파이프라인 처리 순서
```
소재 수집(AI-01)
  → 실화 지문 생성(AI-02) → 사실 검증(AI-06) → 정답 근거 생성(AI-04)
  → AI창작 지문 생성(AI-03, 동일 소재/카테고리 기준) → 개연성 검증(AI-07)
  → 공개 코멘트 생성(AI-05) → 난이도 태깅(AI-08) → 결과 적재(AI-09)
```

---

## 6. 완료 기준 체크리스트

- [ ] `next-card` 응답에 정답 관련 필드가 절대 포함되지 않음 (DTO 분리 확인)
- [ ] `answers` 채점 로직이 `type`과 `user_choice`를 정확히 비교
- [ ] 실화 카드가 사실 검증(AI-06) 실패 시 `PUBLISHED`로 노출되지 않음
- [ ] AI창작 카드가 실존 고유명사/연도를 재사용하지 않음 (AI-03 가드)
- [ ] 세션 결과 API가 세션 내 모든 카드의 정오답을 정확히 집계
- [ ] Mock 없이도 실제 생성된 카드로 FE와 통합 테스트 가능한 상태 (Seed 데이터 최소 3~5개 확보)
