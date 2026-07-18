/**
 * 이벤트 추적.
 *
 * 설계 원칙: 분석 도구가 하나도 없어도 앱은 100% 정상 동작해야 한다.
 * 그래서 track()은 절대 throw하지 않고, 실패는 조용히 삼킨다.
 *
 * 현재 싱크(sink) 4개:
 *   1. console      — 개발 중 확인용
 *   2. localStorage — 내 행동을 되돌려 볼 수 있게. 링 버퍼(200개).
 *   3. GA4          — window.gtag가 있으면 자동으로 탄다. 없으면 조용히 건너뛴다.
 *   4. /api/events  — 우리 백엔드. 통계와 단어 추천이 여기서 나온다.
 *
 * 이벤트 스키마는 docs/PROJECT_NOTES.md 참고.
 */

import { getSessionId, getUtm } from "./session";

export type EventName =
  | "landing_view"
  | "test_start"
  | "confidence_selected"
  | "answer_submitted"
  | "result_view"
  /** 자기판정. correct가 여기서 정해진다 — answer_submitted 시점엔 아직 모른다. */
  | "self_judged"
  | "next_word_click"
  | "retry_click"
  | "share_click"
  | "session_complete"
  /** 이 앱이 서버에 저장하는 유일한 자유 입력. `suggestions` 테이블로도 들어간다. */
  | "word_suggested";

export type EventPayload = {
  event: EventName;
  sessionId?: string;
  wordId?: string;
  timestamp: number;
  source?: string;
  medium?: string;
  campaign?: string;
  [key: string]: unknown;
};

const BUFFER_KEY = "kbi.events.v1";
const BUFFER_MAX = 200;

declare global {
  interface Window {
    gtag?: (command: string, ...args: unknown[]) => void;
    dataLayer?: unknown[];
  }
}

function toBuffer(payload: EventPayload): void {
  try {
    const raw = window.localStorage.getItem(BUFFER_KEY);
    const list: EventPayload[] = raw ? JSON.parse(raw) : [];
    list.push(payload);
    // 링 버퍼 — 오래된 것부터 버려 localStorage 쿼터를 넘기지 않는다.
    window.localStorage.setItem(BUFFER_KEY, JSON.stringify(list.slice(-BUFFER_MAX)));
  } catch {
    /* storage 막혀 있어도 무시 */
  }
}

function toGa(payload: EventPayload): void {
  // GA가 설정되지 않았으면 window.gtag 자체가 없다. 그게 정상 경로다.
  if (typeof window.gtag !== "function") return;
  const { event, ...params } = payload;
  window.gtag("event", event, params);
}

/**
 * 백엔드 전송.
 *
 * 서버가 무엇을 저장할지는 여기가 아니라 `server/src/events.ts`의 화이트리스트가
 * 정한다. 여기서 뭘 더 실어 보내도 표에 없으면 버려진다 — 실수로 개인정보를
 * 흘리는 걸 막는 장치이므로, 새 필드를 보내려면 서버 쪽 표도 같이 고쳐야 한다.
 *
 * 항상 동일 출처(`/api`)로 보낸다. 운영에선 Caddy가, 개발 중엔 Next의 rewrites가
 * 백엔드로 넘긴다.
 */
function toApi(payload: EventPayload): void {
  const body = JSON.stringify(payload);
  // sendBeacon은 페이지를 떠나는 중에도 전송이 보장되고 응답을 기다리지 않는다.
  // Blob의 type이 곧 Content-Type이라 이게 없으면 서버가 body를 파싱하지 못한다.
  if (navigator.sendBeacon?.("/api/events", new Blob([body], { type: "application/json" }))) return;
  // 큐가 꽉 찼거나 sendBeacon이 없는 브라우저용 폴백.
  fetch("/api/events", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => {});
}

export function track(event: EventName, props: Record<string, unknown> = {}): void {
  if (typeof window === "undefined") return; // 서버에서 호출되면 조용히 무시

  try {
    const utm = getUtm();
    const payload: EventPayload = {
      event,
      sessionId: getSessionId(),
      timestamp: Date.now(),
      ...(utm.source ? { source: utm.source } : {}),
      ...(utm.medium ? { medium: utm.medium } : {}),
      ...(utm.campaign ? { campaign: utm.campaign } : {}),
      ...props,
    };

    if (process.env.NODE_ENV !== "production") {
      console.debug("[track]", event, payload);
    }
    toBuffer(payload);
    toGa(payload);
    toApi(payload);
  } catch {
    // 추적 실패가 사용자 경험을 망가뜨리는 일은 없어야 한다.
  }
}

/** 개발/디버깅용 — 브라우저 콘솔에서 쌓인 이벤트를 확인할 때. */
export function getBufferedEvents(): EventPayload[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(BUFFER_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}
