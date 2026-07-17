# 프론트엔드 → 백엔드 핸드오프 문서

> 대상: 백엔드 + AI 파이프라인 담당자 (`spec.md`)
> 작성: 프론트엔드 담당자 (`frontend.md`)
> 목적: 프론트엔드가 무엇을 어떻게 구현했는지, 실제 API 연동 시 무엇이 필요한지 전달

---

## 1. 현재 상태 요약

`frontend.md`의 FE-01 ~ FE-09 전 항목 구현 완료. 지금은 **`src/mock/cards.json` 기반 mock 데이터로 전체 플로우(시작 → 지문 → 정답 공개 → 결과)가 동작**하는 상태이며, 실제 백엔드가 준비되면 **환경변수 하나만 바꾸면 코드 수정 없이 연동**되도록 설계되어 있다.

- 기술 스택: React + TypeScript + Vite + Tailwind CSS
- 화면 전환: 별도 라우터 없이 SPA 내부 state로 전환 (`start` → `quiz` → `result`)
- `npx tsc -b` 통과, 실제 브라우저에서 전체 플로우 수동 검증 완료 (콘솔 에러 없음)

---

## 2. API 계약 — spec.md와 100% 동일하게 구현함

`src/types.ts`에 아래 4개 엔드포인트의 요청/응답 타입을 그대로 정의했다. **spec.md 2장과 다른 점이 있으면 반드시 알려달라** — 지금은 완전히 동일하다고 가정하고 만들었다.

| 엔드포인트 | 파일 |
|---|---|
| `POST /quiz-sessions` | `src/api/session.ts` |
| `GET /quiz-sessions/{id}/next-card` | `src/api/nextCard.ts` |
| `POST /quiz-sessions/{id}/answers` | `src/api/answers.ts` |
| `GET /quiz-sessions/{id}/result` | `src/api/result.ts` |

특히 아래 두 가지는 프론트 구조상 반드시 지켜져야 하는 전제다:

- **`next-card` 응답에는 정답 관련 필드가 절대 없어야 함.** FE는 `QuizCard` 타입 자체에 `type`/`answer_*`/`reveal_comment`/`source_url` 필드를 정의하지 않았다(spec.md BE-08과 동일한 방향). 만약 백엔드가 실수로 이 필드들을 내려주더라도 FE는 타입상 접근하지 않으므로 화면에는 노출되지 않지만, 네트워크 응답 자체에 포함되는 건 스펙 위반이니 BE-08 DTO 분리를 꼭 지켜달라.
- **`answers` 응답의 `score`는 매 제출마다 그 시점까지의 누적 점수**로 해석해 렌더링한다 (`{ correct, total }`). 서버가 "지금까지 몇 번째 문제까지 풀었는지" 기준으로 누적 계산해서 내려준다는 전제.

---

## 3. 프론트 프로젝트 구조

```
src/
├─ types.ts              # API 계약 타입 (spec.md 2~3장과 1:1 매핑)
├─ api/
│  ├─ client.ts           # fetch 래퍼 + mock/real 분기 + mock 세션 저장소
│  ├─ session.ts           # POST /quiz-sessions
│  ├─ nextCard.ts          # GET  /quiz-sessions/{id}/next-card
│  ├─ answers.ts           # POST /quiz-sessions/{id}/answers
│  └─ result.ts            # GET  /quiz-sessions/{id}/result
├─ mock/cards.json         # 통합 전 개발용 mock 카드 3장(실화 2 + 창작 1)
├─ hooks/useQuizFlow.ts    # 세션 진행 상태 머신 (카드 로드 ↔ 정답 공개 상태 분리)
├─ screens/                # StartScreen / QuizScreen / ResultScreen
└─ components/             # CardBody, ChoiceButtons, RevealPanel, ScoreProgress, ResultCardList
```

---

## 4. 실제 백엔드로 전환하는 방법

프론트는 `.env`의 두 값만으로 mock ↔ 실서버를 전환한다 (`.env.example` 참고):

```
VITE_USE_MOCK=false
VITE_API_BASE_URL=http://<백엔드 서버 주소>
```

`VITE_USE_MOCK=false`로 바꾸면 `src/api/*.ts`의 모든 호출이 `fetch(`${VITE_API_BASE_URL}${path}`)`로 그대로 나간다. **프론트 코드 변경 없이** 연동 가능하도록 이미 분리해뒀다.

---

## 5. 백엔드 담당자에게 확인/요청할 사항

통합을 막는 블로커는 아니지만(현재 mock으로 개발 계속 가능), 실제 연동 전에 확정이 필요하다.

1. **카테고리 목록**: `spec.md`/`frontend.md` 모두 카테고리 enum이 명시돼 있지 않고 예시로 "역사"만 등장한다. FE-08(카테고리 필터)은 지금 `GET /cards`로 카테고리를 동적 조회한다고 가정하고 하드코딩된 임시 목록(`역사/과학/인물`)을 쓰고 있다. 실제 목록 or 조회 방식을 확정해달라.
2. **에러 응답 포맷**: 두 스펙 문서 모두 성공 케이스만 정의돼 있다. 세션 만료, 잘못된 `card_id` 제출, 서버 오류 시 HTTP status/body 포맷이 없어 FE는 현재 표준 fetch 에러(`res.ok` 체크 + 메시지 텍스트)로 임시 처리 중이다. 확정되면 `src/api/client.ts`의 에러 처리 로직에 반영하겠다.
3. **API base URL / CORS**: 로컬 통합 테스트용 백엔드 주소와, 프론트 개발 서버(Vite, 기본 `localhost:5173`)에 대한 CORS 허용 여부를 알려달라.
4. **세션 재조회/새로고침 대응 여부**: 현재 FE는 `session_id`를 브라우저 메모리(React state)에만 들고 있어 새로고침하면 세션이 끊긴다. 필요하면 서버가 `session_id`로 진행 상태를 복원해주는 API가 있는지, 혹은 FE에서 `sessionStorage`로 보완할지 논의가 필요하다 (현재 스펙에는 없음, 필수 아니면 스킵 가능).

---

## 6. 로컬에서 프론트 실행해보는 법

```bash
npm install
npm run dev        # http://localhost:5173, 기본값은 mock 모드
npx tsc -b         # 타입 체크
```

`.env`를 만들지 않으면 `VITE_USE_MOCK`이 기본 `true`로 동작해 mock 데이터로 바로 확인 가능하다.
