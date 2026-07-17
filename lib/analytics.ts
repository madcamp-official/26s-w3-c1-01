/**
 * 이벤트 추적.
 *
 * 설계 원칙: 분석 도구가 하나도 없어도 앱은 100% 정상 동작해야 한다.
 * 그래서 track()은 절대 throw하지 않고, 실패는 조용히 삼킨다.
 *
 * 현재 싱크(sink) 3개:
 *   1. console      — 개발 중 확인용
 *   2. localStorage — 백엔드 없이도 내 행동을 되돌려 볼 수 있게. 링 버퍼(200개).
 *   3. GA4          — window.gtag가 있으면 자동으로 탄다. 없으면 조용히 건너뛴다.
 *
 * 백엔드가 붙으면 sendToApi()의 주석만 풀면 된다. 다른 파일은 손댈 필요 없다.
 * 이벤트 스키마는 docs/PROJECT_NOTES.md 참고.
 */

import { getSessionId, getUtm } from "./session";

export type EventName =
  | "landing_view"
  | "test_start"
  | "confidence_selected"
  | "answer_submitted"
  | "result_view"
  | "next_word_click"
  | "retry_click"
  | "share_click"
  | "session_complete";

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

// 백엔드 연결 지점. 서버가 생기면 이 함수만 살리면 된다.
// function toApi(payload: EventPayload): void {
//   const body = JSON.stringify(payload);
//   // sendBeacon은 페이지를 떠나는 중에도 전송이 보장되고 응답을 기다리지 않는다.
//   if (navigator.sendBeacon?.("/api/events", new Blob([body], { type: "application/json" }))) return;
//   fetch("/api/events", { method: "POST", body, keepalive: true }).catch(() => {});
// }

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
    // toApi(payload);
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
