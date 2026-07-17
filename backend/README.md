# ai-contents backend

API 계약은 [`../docs/spec-backend-ai.md`](../docs/spec-backend-ai.md) 참고.

## 실행

```bash
cd backend
cp .env.example .env   # OPENAI_API_KEY 채워넣기
npm install
npm run dev   # 또는 npm start
```

기본 포트: `3001` (환경변수 `PORT`로 변경 가능)

## 환경변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `PORT` | 서버 포트 | `3001` |
| `OPENAI_API_KEY` | AI 파이프라인용 OpenAI 키 (`/cards/generate` 사용 시 필요) | - |
| `OPENAI_MODEL` | 사용할 모델 | `gpt-4o-mini` |
| `CORS_ORIGIN` | 콤마로 구분된 허용 origin 목록. 미설정/`*`이면 전체 허용(로컬 개발 기본값) | `*` |

## 데이터 저장

- 현재는 **인메모리**: 서버 재시작 시 초기화되며, `src/data/cards.seed.json`이 시드로 로드됨.
- 퀴즈 세션도 인메모리(Map)에 저장 — 추후 DB 전환 시 `src/store/*.js`만 교체하면 됨.

## 엔드포인트

### 사용자용 (퀴즈)
- `POST /quiz-sessions`
- `GET /quiz-sessions/:id/next-card`
- `POST /quiz-sessions/:id/answers`
- `GET /quiz-sessions/:id/result`

### 관리용 (콘텐츠 카드)
- `GET /cards?category=&difficulty=&status=`
- `POST /cards` — 새 카드 수동 생성 (`status: DRAFT`로 시작)
- `POST /cards/generate` — AI 파이프라인 트리거. `{ "category": "역사", "topic": "선택 힌트" }` 전달 시
  실화 카드 1개 + AI창작 카드 1개를 생성·검증까지 마쳐 저장 (`OPENAI_API_KEY` 필요, 없으면 502)
- `PATCH /cards/:id/status` — `DRAFT|VERIFIED|PUBLISHED|REJECTED` 전이

`status=PUBLISHED`인 카드만 퀴즈 세션에 노출됩니다. `/cards/generate`로 만든 카드는
`VERIFIED`/`REJECTED`까지만 자동 처리되며, 실제 노출하려면 검수 후 `PATCH .../status`로 `PUBLISHED` 전환이 필요합니다.

## 카드 검수 CLI

저장소가 인메모리라 반드시 **서버가 켜져 있는 상태**에서 실행해야 함 (내부적으로 HTTP API를 호출).

```bash
npm run review -- list DRAFT          # DRAFT 카드 목록 확인
npm run review -- publish <cardId>    # 발행
npm run review -- reject <cardId>     # 반려
```

## AI 파이프라인 구조 (`src/ai/`)

```
소재 입력(category/topic, 없으면 topicPool에서 검증된 실제 사건 소재를 무작위 선택)
  → 실화 지문 생성(generateRealCard) → 사실 검증(verifyRealCard)
    → 검증 실패 시 반려 사유를 피드백으로 넘겨 최대 2회 재시도(generateVerifiedRealCard)
  → AI창작 지문 생성(generateFabricatedCard, 실화 제목과 겹치지 않게) → 개연성 검증(verifyFabricatedCard)
  → 난이도 태깅(tagDifficulty) → cardStore.createCard()로 적재
```
- `src/ai/openaiClient.js`: OpenAI Chat Completions 호출 래퍼 (JSON 모드)
- `src/ai/prompts.js`: 단계별 프롬프트 템플릿
- `src/ai/pipeline.js`: 위 단계를 순서대로 실행하는 오케스트레이션
- `src/ai/topicPool.js`: 실화 카드 grounding용 검증된 실제 사건 소재 목록

실화 생성 시 LLM 환각(존재하지 않는 사건을 지어내는 문제)을 줄이기 위해 topicPool 힌트 +
"확신 없으면 지어내지 말 것" 프롬프트 제약 + 검증 실패 시 피드백 재시도를 적용함.
자세한 배경은 [`../docs/backend-handoff.md`](../docs/backend-handoff.md) 5장 참고.
