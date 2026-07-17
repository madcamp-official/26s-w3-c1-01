# 내가 안다고 착각한 단어 — 작업 노트

몰입캠프 3주차 공통과제 III · **옵션 2 (Launch & Spread)**

이 문서는 README가 아니라 **작업/핸드오프 노트**다. 구조, 결정의 이유, 백엔드
연결 지점, 배포 전에 반드시 해야 할 것을 담는다. 다른 사람이나 다른 환경의
에이전트가 이 파일만 읽고 이어서 작업할 수 있는 것이 목표다.

---

## 1. 이 제품이 하려는 것

사용자가 **익숙한 단어를 실제로는 설명하지 못한다는 걸 스스로 발견**하게 만든다.
학습 플랫폼이 아니다. 1~2분 안에 끝나는 한 번의 경험이고, 그 경험이 공유될 수
있는지를 확인하는 실험이다.

흐름: 랜딩 → 자기평가 → 보기 선택 → 결과(정답 + 해설 + 통계) → ×5 → 요약 + 공유

---

## 2. 핵심 설계 결정과 그 이유

### 2.1 객관식인데도 '착각'이 성립하게 만든 방법

원래 주제(설명해 보게 하기)는 **주관식이어야 효과가 제대로 난다.** 재인
(보기에서 고르기)은 회상(직접 설명하기)보다 훨씬 쉬워서, 소거법으로 맞히고
"난 역시 알았네"로 끝나면 착각이 드러나기는커녕 강화된다.

그럼에도 객관식으로 간 이유는 주관식 채점에 LLM이 필요하고, 그건 1주일 공개
배포 목표와 맞지 않아서다. 대신 두 가지로 보완했다:

1. **오답 보기 = 흔한 오해.** 아무 말이나 넣은 보기는 그냥 퀴즈다. 오답이
   *사용자가 실제로 믿고 있던 문장*이어야 고르는 순간 착각이 성립한다.
   `data/words.ts`의 `misconception` 필드가 그 역할이다. **이 필드의 품질이
   사실상 제품의 품질이다.**
2. **점수를 정답률이 아니라 착각률로.** 자기평가와 정답 여부의 *차이*가 점수다.

### 2.2 착각 지수와 이모지 격자

Wordle이 퍼진 건 게임이 아니라 **정답을 노출하지 않는 이모지 격자** 때문이었다.
단어 하나 풀고 "나 민주주의 틀림"은 공유할 이유가 없어서, 5단어를 한 세션으로
묶고 요약 화면을 만들었다.

| 이모지 | 조건 | 의미 |
| --- | --- | --- |
| 🟥 | 안다고 답함 + 오답 | **착각** ← 이 제품의 주제 |
| 🟩 | 안다고 답함 + 정답 | 진짜 알았음 |
| 🟦 | 모른다고 답함 + 정답 | 찍었는데 맞음 |
| ⬜ | 모른다고 답함 + 오답 | 착각은 없었음 |

**착각 지수 = 🟥의 개수.** 공유 텍스트에는 단어명·정답·선택지가 일절 들어가지
않는다. 스포일러가 있으면 받은 사람이 풀 이유가 사라지고 확산이 멈춘다.

"안다"의 기준은 자기평가 `high`(정확히 설명할 수 있다)와 `mid`(대충 안다)까지다.
"대충 안다"고 하고 못 하는 것이야말로 지식의 착각이므로 포함시켰다.
(`lib/quiz.ts`의 `claimedToKnow`)

### 2.3 세션 = sessionStorage

로그인이 없으므로 `sessionId`가 유일한 사용자 식별자다. 탭을 닫으면 사라지게
해서 "한 번의 방문 = 한 세션"으로 맞췄다.

공유 링크로 `/w/inertia`에 바로 들어오면 **그 단어가 세션의 1번**이 되고 나머지가
뒤에 붙는다. (`lib/session.ts`의 `ensureSession`)

---

## 3. 구조

```
app/
  layout.tsx          메타데이터(OG 포함), 폰트, 전역 CSS
  page.tsx            랜딩 — 대표 단어 훅 + 시작 버튼 (서버 컴포넌트)
  w/[slug]/page.tsx   단어별 라우트. generateStaticParams로 5개 전부 SSG
  result/page.tsx     세션 요약 + 착각 지수 + 공유 (클라이언트)
components/
  StartButton.tsx     세션 생성 + UTM 캡처 + landing_view
  WordPlay.tsx        3단계 흐름 오케스트레이션 (자기평가→보기→결과)
  ConfidencePicker.tsx
  ChoiceList.tsx      선택/결과 겸용 (revealed 프롭으로 전환)
  ResultPanel.tsx     정답·해설·핵심요소·통계
  ShareButton.tsx     Web Share API → 클립보드 폴백
data/
  words.ts            ★ 단어 데이터 단일 진실 공급원
  seedStats.ts        ⚠️ 가짜 통계. 배포 전 교체 필수
lib/
  session.ts          sessionId, UTM, 답안 저장 (useSyncExternalStore 구독)
  analytics.ts        track() + 이벤트 타입 + GA 연동 지점
  stats.ts            통계 조회 seam
  quiz.ts             착각 지수 계산, 이모지 격자, 공유 텍스트
```

라우트를 단어별로 쪼갠 이유는 **공유 링크가 특정 단어를 가리켜야 해서**다.
한 단어 안의 3단계는 라우트 이동 없이 클라이언트 상태로 넘긴다 — 제출 후
로딩이 걸리면 착각이 드러나는 순간의 긴장이 풀린다.

### 왜 `useSyncExternalStore`인가

sessionStorage는 외부 저장소라 렌더 중에 읽으면 SSR 하이드레이션이 깨진다.
effect에서 `setState`로 끌어오는 방식은 React 19 린트 룰
(`react-hooks/set-state-in-effect`)에 걸리고 캐스케이딩 렌더를 만든다.
그래서 구독 방식으로 갔다. **스냅샷은 참조가 안정적이어야 하므로**
`lib/session.ts`에서 원본 문자열이 같으면 같은 객체를 돌려주도록 캐싱한다.
이 캐시를 빼면 무한 루프가 난다.

---

## 4. ⚠️ 배포 전 반드시 할 것

- [ ] **`data/seedStats.ts`의 수치는 전부 지어낸 것이다.** 이 상태로 공개하면
      실제 사용자에게 허위 통계를 보여주게 된다. 아래 셋 중 하나를 할 것:
      1. 실제 백엔드 연결 후 `STATS_ARE_PLACEHOLDER = false` (권장)
      2. 소규모 파일럿으로 진짜 숫자를 받아 파일을 덮어쓰기
      3. UI에서 통계 영역 제거
      → `STATS_ARE_PLACEHOLDER`가 `true`인 동안은 UI에 "예시 수치" 배지가 강제
        노출된다. 실수로 거짓말한 채 배포되는 걸 막는 장치이므로 배지만 지우지 말 것.
- [ ] **단어 5개의 정의·해설 사실 검증.** 팀에서 직접 읽고 확인할 것.
      틀린 해설을 공개하는 게 이 제품의 가장 빠른 실패 경로다.
- [ ] `NEXT_PUBLIC_SITE_URL`을 실제 배포 도메인으로 설정 (OG/공유 링크 절대경로)
- [ ] OG 이미지(`app/opengraph-image.tsx`) — 현재 없음. 링크 미리보기가 밋밋하다.
- [ ] 지표 1~2개 확정 + 시작 시점 예상 수치 기록 (과제 요구사항)

---

## 5. 백엔드 연결 지점

지금은 백엔드가 없다. 붙일 때 손댈 곳은 **딱 세 군데**이고, 컴포넌트는 하나도
건드릴 필요가 없다.

### 5.1 이벤트 수집 — `lib/analytics.ts`

`toApi()` 함수와 `track()` 안의 `// toApi(payload);` 주석을 풀면 끝.
`sendBeacon`을 먼저 시도하고 실패 시 `fetch(keepalive)`로 폴백하도록 이미 써 뒀다.

이벤트 페이로드 스키마:

```ts
{
  event: EventName,        // 아래 9종
  sessionId?: string,      // 익명 UUID (sessionStorage)
  wordId?: string,         // 해당하는 경우
  timestamp: number,       // Date.now()
  source?, medium?, campaign?,  // UTM (세션 내내 유지)
  ...이벤트별 추가 필드
}
```

| 이벤트 | 발생 시점 | 추가 필드 |
| --- | --- | --- |
| `landing_view` | 랜딩 진입 | — |
| `test_start` | 시작 클릭 / 공유링크 직접 진입 | `wordId`, `entry: "landing" \| "direct"` |
| `confidence_selected` | 자기평가 선택 | `wordId`, `confidence` |
| `answer_submitted` | 정답 확인 클릭 | `wordId`, `choiceId`, `confidence`, `correct` |
| `result_view` | 결과 표시 | `wordId`, `correct` |
| `next_word_click` | 다음 단어 | `wordId`, `nextWordId?` |
| `retry_click` | 다시 하기 | `wordId?`, `from?` |
| `share_click` | 공유 버튼 | `kind: "result" \| "word"`, `wordId?` |
| `session_complete` | 요약 화면 도달 | `total`, `correct`, `illusions`, `durationMs` |

`test_start`와 `session_complete`는 **세션당 정확히 한 번**만 발생하도록 되어 있다.
이 둘의 비율이 곧 완주율(퍼널의 핵심 지표)이다.

### 5.2 통계 조회 — `lib/stats.ts`

`getWordStats()` 본문만 갈아끼운다. 이미 `Promise`를 반환하므로 호출부는 그대로다.

```ts
export async function getWordStats(wordId: string): Promise<WordStats | null> {
  return fetch(`/api/stats/${wordId}`).then(r => r.json());
}
```

필요한 응답 형태:
```ts
{ totalAnswers: number, choiceCounts: Record<choiceId, number>, confidentCorrectRate: number }
```
`confidentCorrectRate` = (confidence가 high|mid이면서 correct인 수) / (high|mid인 수)

### 5.3 GA4

`window.gtag`가 존재하면 `track()`이 자동으로 이벤트를 흘려보낸다
(`lib/analytics.ts`의 `toGa`). GA를 붙이려면 `app/layout.tsx`에 gtag 스크립트만
넣으면 되고 다른 코드는 손댈 필요 없다. **GA가 없어도 앱은 정상 동작한다.**

디버깅: 브라우저 콘솔에서 최근 이벤트 200개를 확인할 수 있다.
```js
JSON.parse(localStorage.getItem("kbi.events.v1"))
```

---

## 6. 단어 추가하기

`data/words.ts`의 `WORDS` 배열에 추가하면 라우트·SSG·세션 순서에 자동 반영된다.
다른 파일은 손댈 필요 없다.

지켜야 할 것:
- `id`는 URL에 노출된다. **바꾸면 기존 공유 링크가 깨진다.**
- 오답 3개 중 최소 1개는 **실제로 흔한 오해**여야 하고 `misconception`을 채운다.
- `definition`/`note`의 사실 정확성은 사람이 검토한다.

AI로 대량 생성할 때는 "그럴듯한 오답"이 아니라 **"실제로 많은 사람이 믿고 있는
오해"**를 요구해야 한다. 이 둘은 다르고, 전자만 나오면 제품이 그냥 퀴즈가 된다.
생성 후 사실 검증은 사람이 한다.

---

## 7. 실행

```bash
npm install
npm run dev      # http://localhost:3000
npm run build    # 전 라우트 정적 생성 확인
npx eslint .
```

환경변수는 `NEXT_PUBLIC_SITE_URL` 하나뿐이고, 없어도 로컬에서는 동작한다.

스택: Next.js 16 (App Router, Turbopack) · React 19 · TypeScript · Tailwind v4
배포 대상: Vercel

---

## 8. 진행 기록

### 2026-07-17 — 초기 MVP 구현

**처음 계획한 것**: 단어를 보여주고 자기평가 → 주관식 설명 → 정답 공개 → 통계.

**실제로 만든 것**: 주관식은 LLM 채점이 필요해 1주일 목표와 맞지 않아 객관식으로
내렸다. 대신 오답을 '흔한 오해'로 채우고, 점수를 정답률이 아니라 착각률로 바꿔서
주제가 유지되도록 했다.

**계획에서 바뀐 것 2가지**:
1. 단어 1개 단위 → **5개 세션 + 요약 화면**. 단어 하나짜리 결과는 공유할 이유가
   없다고 판단. 요약의 이모지 격자가 확산의 유일한 엔진이라고 봤다.
2. 통계를 그냥 seed로 채우려다 → **가짜임을 UI에 강제 노출**하는 플래그를 넣었다.
   공개 배포가 전제인 과제에서 지어낸 수치를 실제 사용자에게 보여주는 건
   과제 목적(실제 반응 측정)과 자기모순이다.

**검증한 것**: 랜딩 → 5단어 → 요약 전체 흐름, 이벤트 9종 발생 순서,
`test_start`/`session_complete` 세션당 1회, UTM 캡처, 공유 링크 직접 유입 시
해당 단어가 1번으로 오는 것. 빌드/린트 통과.

**아직 안 한 것**: 실제 배포, OG 이미지, 백엔드, 실제 사용자 반응.

### (다음 기록은 여기에)

- 공개 채널과 시점:
- 시작 시점 예상 수치:
- 실제 결과:
- 예상과 달랐던 점:
- 반응을 보고 실제로 고친 것:  ← **옵션 2의 필수 조건. 이게 없으면 과제 미이행.**
