# 백엔드 핸드오프 문서

> 프론트엔드와 합칠 때 참고용. API 계약의 원본은 [spec-frontend.md](./spec-frontend.md) /
> [spec-backend-ai.md](./spec-backend-ai.md)이고, 이 문서는 **실제 구현 상태·검증 결과·주의사항**을 정리한다.

## 1. 실행 방법

```bash
cd backend
cp .env.example .env   # OPENAI_API_KEY 채워넣기 (AI 파이프라인 쓸 때만 필요)
npm install
npm run dev             # http://localhost:3001
```

- 저장소는 **인메모리**. 서버 재시작 시 카드/세션 모두 초기화되고, `src/data/cards.seed.json`(시드 카드 4개)만 다시 로드됨.
- `.env`는 `.gitignore`에 포함되어 커밋되지 않음. 팀원마다 각자 키를 넣어야 함.

## 2. 구현되어 있고 curl로 검증까지 마친 API

### 사용자용 (프론트엔드가 붙일 것)
| Method | Path | 확인 완료 |
|---|---|---|
| POST | `/quiz-sessions` | ✅ |
| GET | `/quiz-sessions/:id/next-card` | ✅ (정답 필드 미포함 확인) |
| POST | `/quiz-sessions/:id/answers` | ✅ (채점/점수 누적 확인) |
| GET | `/quiz-sessions/:id/result` | ✅ |

요청/응답 스키마는 [spec-frontend.md](./spec-frontend.md) 2장과 **완전히 동일**하게 구현됨. 프론트는 그 문서의 Mock JSON을 그대로 실제 응답으로 바꿔 끼우면 됨.

### 관리용
| Method | Path | 확인 완료 |
|---|---|---|
| GET | `/cards?category=&difficulty=&status=` | ✅ |
| POST | `/cards` (수동 등록, `status: DRAFT`로 시작) | ✅ |
| POST | `/cards/generate` (AI 파이프라인 트리거) | ✅ (실제 OpenAI 키로 호출 성공) |
| PATCH | `/cards/:id/status` | ✅ |
| GET | `/health` | ✅ |

## 3. 시드 데이터 현황

`src/data/cards.seed.json`에 4개, 전부 `status: PUBLISHED`:
- 실화 2개 — 1932 에뮤 전쟁, 1518 스트라스부르 무도병
- AI창작 2개 — 비둘기 등록제, 우산 착용 의무화법

퀴즈를 데모하려면 이 4개만으로도 `POST /quiz-sessions`가 바로 동작함 (프론트가 백엔드 없이 기다릴 필요 없음).

## 4. 정답 유출 방지 구현 방식

`cardStore.toPublicCard()`가 `id/category/body/difficulty`만 뽑아서 반환하고,
`GET /next-card` 라우트는 이 함수를 거친 결과만 응답한다. `type`/`answer_title`/`answer_fact`/
`reveal_comment`/`source_url`은 **채점 시점(`POST /answers`) 응답에만** 존재.
→ 프론트 쪽에서 지문 조회 응답 객체를 그대로 정답 공개 화면에 재사용하면 안 되고, `answers` 응답을 따로 받아써야 함.

세션 순서 조작 방지: `POST /answers`에 세션이 기대하는 다음 카드와 다른 `card_id`가 오면 `409 unexpected_card_id` 반환.

## 5. AI 파이프라인 — 환각 이슈와 개선 내역

처음 `POST /cards/generate`를 실제 키로 호출했을 때 **실화 카드 생성 단계에서 LLM이 존재하지 않는
사건을 지어내는 환각이 발생**했다 (예: "1978년 마가렛 대처 낚시대회 소동" — 실존하지 않음).
사실 검증 단계(AI-06)가 이를 정확히 잡아내 `status: REJECTED`로 처리하긴 했지만, 1차 생성 환각률
자체가 낮지 않아 아래 3가지를 개선했다.

1. **소재 힌트 풀 도입** (`src/ai/topicPool.js`) — `topic`을 안 주면 위키백과 등에 잘 문서화된
   실제 사건 목록(에뮤 전쟁, 탕가니카 웃음 전염병, 벨기에 UFO 웨이브, 로스앤젤레스 전투 등)에서
   무작위로 골라 grounding 힌트로 사용
2. **실화 프롬프트 강화** (`src/ai/prompts.js: realCardPrompt`) — "100% 확신하는 유명한 사건만
   사용, 세부사항을 지어내지 말 것"을 명시하고 좋은 예시를 시스템 프롬프트에 포함. `source_url`도
   불확실하면 반드시 `null`로 두도록 강제 (전에는 `https://www.bbc.com/news/uk-12345678` 같은
   가짜 URL을 지어내는 경우가 있었음)
3. **검증 실패 시 재시도** (`src/ai/pipeline.js: generateVerifiedRealCard`) — `AI-06` 검증에서
   반려되면 반려 사유를 다음 생성 시도의 피드백으로 넘겨 최대 2회까지 재시도

개선 후 카테고리 3개(역사/과학/사회)로 재테스트한 결과 **3/3 모두 첫 시도에서 `VERIFIED`** 통과
(탕가니카 웃음 전염병, 벨기에 UFO 웨이브, 로스앤젤레스 전투). 다만 여전히 LLM 기반이라 환각
가능성이 완전히 0은 아니므로:
- `/cards/generate` 결과 중 `status: REJECTED`인 카드는 **절대 그대로 발행(PUBLISHED)하면 안 됨**
- `VERIFIED`도 LLM 자체 검증이라 100% 신뢰 불가 — 실제 서비스에 쓰려면 `source_url`까지 사람이
  한 번 더 확인 후 `PATCH .../status`로 `PUBLISHED` 전환하는 걸 권장
- 자동 생성만으로 콘텐츠 파이프라인을 완전히 무인화하기엔 아직 이름 → `npm run review`로 검수 가능 (6장 참고)

## 5-1. 카드 검수 CLI

`backend/scripts/review-cli.js`. 저장소가 인메모리라 반드시 서버가 켜진 상태에서 HTTP로 호출한다.

```bash
npm run review -- list DRAFT
npm run review -- publish <cardId>
npm run review -- reject <cardId>
```

## 5-2. CORS / 배포 환경변수

`CORS_ORIGIN` 환경변수로 허용 origin을 제한할 수 있음 (콤마 구분, 미설정 시 전체 허용 — 로컬 개발 기본값).
배포 시엔 프론트 도메인으로 반드시 제한할 것. `.env.example`에 `PORT`/`OPENAI_API_KEY`/`OPENAI_MODEL`/`CORS_ORIGIN` 정리되어 있음.

## 6. 테스트 방식

- 실제 서버 기동 후 curl로 전체 플로우(세션 생성 → next-card 4회 순회 → answers → result) 검증
- OpenAI 키 없이도 파이프라인 로직을 검증하기 위해 `openaiClient.chatJSON`을 몬키패치한 드라이런 스크립트 사용
  (레포에는 포함 안 됨, 세션 내 임시 스크립트)
- 별도 자동화 테스트(예: jest)는 아직 없음 — 필요하면 추가 논의

## 7. 프론트엔드 연동 시 체크리스트

- [x] `spec-frontend.md`의 API 계약과 실제 응답을 필드 단위로 대조 완료 (happy path 100% 일치, 에러 응답 4종을 2.5절에 새로 문서화함)
- [ ] `next-card` 응답에 정답 필드가 없다는 전제로 화면 상태를 설계했는지 확인 (FE-09)
- [ ] 로컬 개발 중엔 CORS 전체 허용(기본값). 배포 도메인 확정되면 `CORS_ORIGIN` 환경변수로 제한할 것
- [ ] 로컬 개발 시 백엔드 포트는 `3001`, 프론트 API 베이스 URL을 이에 맞출 것

## 8. 남은 작업 (백엔드 관점)

- 세션/카드 영속 저장소 전환 (현재 의도적으로 인메모리로 시작 — 서버 재시작 시 초기화됨. DB로 옮길 때는
  `src/store/*.js`만 교체하면 되도록 이미 분리해둠)
- (선택) 자동화 테스트(jest 등) 추가 — 지금은 curl/드라이런 스크립트로 수동 검증만 함
