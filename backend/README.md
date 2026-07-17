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

## AI 파이프라인 구조 (`src/ai/`)

```
소재 입력(category/topic)
  → 실화 지문 생성(generateRealCard) → 사실 검증(verifyRealCard)
  → AI창작 지문 생성(generateFabricatedCard, 실화 제목과 겹치지 않게) → 개연성 검증(verifyFabricatedCard)
  → 난이도 태깅(tagDifficulty) → cardStore.createCard()로 적재
```
- `src/ai/openaiClient.js`: OpenAI Chat Completions 호출 래퍼 (JSON 모드)
- `src/ai/prompts.js`: 단계별 프롬프트 템플릿
- `src/ai/pipeline.js`: 위 단계를 순서대로 실행하는 오케스트레이션
