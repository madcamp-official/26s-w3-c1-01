/**
 * 익명 세션 + UTM 유지.
 *
 * 로그인이 없으므로 sessionId가 유일한 사용자 식별자다. sessionStorage에 두어
 * 탭을 닫으면 사라지게 했다 (= 한 번의 방문 = 한 세션).
 *
 * UTM은 유입 링크에만 붙어 있고 이후 페이지 이동에서는 사라지므로, 첫 진입에
 * 한 번 낚아채서 세션 내내 들고 다녀야 한다. 그래야 "인스타 유입 사용자의
 * 완주율"같은 걸 나중에 볼 수 있다.
 */

import type { ConfidenceLevel } from "@/data/words";

const SESSION_KEY = "kbi.session.v1";

export type UtmParams = {
  source?: string;
  medium?: string;
  campaign?: string;
};

export type SessionAnswer = {
  wordId: string;
  confidence: ConfidenceLevel;
  choiceId: string;
  correct: boolean;
  at: number;
};

export type QuizSession = {
  sessionId: string;
  startedAt: number;
  /** 이번 세션에서 풀 단어 순서 */
  order: string[];
  answers: SessionAnswer[];
  utm: UtmParams;
};

function makeId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * 스냅샷 캐시.
 *
 * useSyncExternalStore는 스냅샷이 값이 아니라 '참조'로 같아야 재렌더를 멈춘다.
 * JSON.parse는 매번 새 객체를 만들므로 그대로 넘기면 무한 루프가 난다.
 * 그래서 원본 문자열이 그대로면 이전 객체를 그대로 돌려준다.
 */
let cachedRaw: string | null = null;
let cachedSession: QuizSession | null = null;

const listeners = new Set<() => void>();

function parse(raw: string | null): QuizSession | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as QuizSession;
    // 저장 형식이 바뀌었거나 손상된 경우 조용히 버린다.
    if (!parsed?.sessionId || !Array.isArray(parsed.order)) return null;
    return parsed;
  } catch {
    return null;
  }
}

function read(): QuizSession | null {
  if (typeof window === "undefined") return null;
  let raw: string | null = null;
  try {
    raw = window.sessionStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
  if (raw === cachedRaw) return cachedSession;
  cachedRaw = raw;
  cachedSession = parse(raw);
  return cachedSession;
}

function write(session: QuizSession): void {
  if (typeof window === "undefined") return;
  try {
    const raw = JSON.stringify(session);
    window.sessionStorage.setItem(SESSION_KEY, raw);
    cachedRaw = raw;
    cachedSession = session;
  } catch {
    // 사파리 프라이빗 모드 등에서 storage가 막혀도 앱은 계속 동작해야 한다.
    cachedSession = session;
  }
  listeners.forEach((l) => l());
}

/** useSyncExternalStore용. 세션은 외부 저장소이므로 구독해서 읽는다. */
export function subscribeSession(onChange: () => void): () => void {
  listeners.add(onChange);
  return () => {
    listeners.delete(onChange);
  };
}

export function getSessionSnapshot(): QuizSession | null {
  return read();
}

/** SSR/하이드레이션 시점엔 sessionStorage가 없다. 항상 같은 값이라 참조도 안정적이다. */
export function getServerSessionSnapshot(): QuizSession | null {
  return null;
}

/** URL에서 UTM을 읽는다. 없으면 빈 객체. */
export function readUtmFromUrl(): UtmParams {
  if (typeof window === "undefined") return {};
  const p = new URLSearchParams(window.location.search);
  const utm: UtmParams = {};
  const source = p.get("utm_source");
  const medium = p.get("utm_medium");
  const campaign = p.get("utm_campaign");
  if (source) utm.source = source;
  if (medium) utm.medium = medium;
  if (campaign) utm.campaign = campaign;
  return utm;
}

/**
 * 세션을 보장한다. 없으면 만든다.
 * @param startWordId 이 단어를 첫 번째로 오게 한다 (공유 링크로 특정 단어에 바로 들어온 경우).
 */
export function ensureSession(allWordIds: string[], startWordId?: string): QuizSession {
  const existing = read();
  if (existing) return existing;

  const order = startWordId
    ? [startWordId, ...allWordIds.filter((id) => id !== startWordId)]
    : [...allWordIds];

  const session: QuizSession = {
    sessionId: makeId(),
    startedAt: Date.now(),
    order,
    answers: [],
    utm: readUtmFromUrl(),
  };
  write(session);
  return session;
}

export function resetSession(): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(SESSION_KEY);
  } catch {
    /* noop */
  }
  cachedRaw = null;
  cachedSession = null;
  listeners.forEach((l) => l());
}

/** 같은 단어를 다시 답하면 마지막 답으로 덮어쓴다. */
export function recordAnswer(answer: SessionAnswer): QuizSession | null {
  const session = read();
  if (!session) return null;
  const answers = session.answers.filter((a) => a.wordId !== answer.wordId);
  answers.push(answer);
  const next = { ...session, answers };
  write(next);
  return next;
}

export function getUtm(): UtmParams {
  return read()?.utm ?? {};
}

export function getSessionId(): string | undefined {
  return read()?.sessionId;
}
