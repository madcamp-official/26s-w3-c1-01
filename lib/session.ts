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

// 저장 형식이 바뀌면 키를 올린다. 예전 세션은 읽히지 않고 새로 시작된다.
const SESSION_KEY = "kbi.session.v4";

export type UtmParams = {
  source?: string;
  medium?: string;
  campaign?: string;
};

/** 사용자 본인의 판정. LLM이 아니라 사람이 자기 설명과 정답을 나란히 보고 정한다. */
export type Judgment = "correct" | "partial" | "wrong";

export type SessionAnswer = {
  wordId: string;
  /** 설명을 쓰기 전에 "안다"를 눌렀는지. 이 값과 judgment의 차이가 착각 지수다. */
  knew: boolean;
  /**
   * 사용자가 실제로 쓴 설명. "모른다"를 눌러 건너뛰었으면 null.
   *
   * ⚠️ **이 값은 서버로 보내지 않는다. 의도적인 결정이다.**
   *
   * 자유 입력이라 개인정보가 섞일 수 있고, 우리가 알아야 할 것(착각률)은
   * 판정 이벤트만으로 다 나온다. 안 받으면 유출될 것도 없다. 대신 "사람들이
   * 민주주의를 어떻게 설명하는가"라는 값진 데이터를 영영 못 본다 — 그걸
   * 알고 버린 것이다.
   *
   * 보내고 싶어지면 서버의 화이트리스트(`server/src/events.ts`)에 추가해야만
   * 저장된다. 그 표를 고치는 순간이 이 결정을 뒤집는 순간이니, 팀과 먼저 합의할 것.
   */
  text: string | null;
  /** "모른다"를 눌렀으면 판정 자체가 없으므로 null. */
  judgment: Judgment | null;
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
