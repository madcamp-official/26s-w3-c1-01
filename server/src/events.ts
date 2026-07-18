import { pool } from "./db.js";

/**
 * 이벤트 수집. 무엇을 저장할지는 오직 이 파일이 정한다.
 *
 * ⚠️ 화이트리스트가 이 파일의 존재 이유다.
 *
 * "사용자가 쓴 설명 원문은 저장하지 않는다"는 결정을 관례가 아니라 구조로
 * 강제한다. 프론트가 실수로 `text`를 실어 보내도 여기서 조용히 버려진다.
 * 새 필드를 저장하려면 아래 표에 손으로 추가해야 하고, 그 순간이 "이거
 * 개인정보인가?"를 묻게 되는 지점이다. 주석으로 적어 둔 규칙은 언젠가
 * 깨지지만 이 표는 안 깨진다.
 *
 * 이벤트 이름과 prop 이름은 프론트의 `lib/analytics.ts`와 짝이다. 한쪽만
 * 바꾸면 데이터가 조용히 사라진다 — 에러가 아니라 그냥 빈칸이 된다.
 */

/** 이벤트별로 저장을 허용하는 prop. 여기 없는 키는 전부 버린다. */
const ALLOWED_PROPS = {
  landing_view: [],
  test_start: ["entry"],
  confidence_selected: ["knew"],
  answer_submitted: ["knew", "answerLength", "gaveUp"],
  result_view: ["knew"],
  self_judged: ["judgment", "gaveUp"],
  next_word_click: ["nextWordId"],
  retry_click: ["from"],
  share_click: ["kind"],
  session_complete: ["total", "claimed", "illusions", "durationMs"],
  /** `word`는 이 앱이 저장하는 **유일한** 자유 입력이다. 아래 길이 제한 참고. */
  word_suggested: ["word", "length"],
} as const satisfies Record<string, readonly string[]>;

export type EventName = keyof typeof ALLOWED_PROPS;

/** id·UTM 같은 기계가 만든 값은 이보다 길 이유가 없다. 길면 뭔가 잘못된 것이다. */
const MAX_FIELD = 64;
/** 추천 단어는 사람이 친 글이라 따로 짧게 자른다. 한 단어면 충분하다고 물었다. */
const MAX_SUGGESTION = 40;

export type CleanEvent = {
  event: EventName;
  sessionId: string | null;
  wordId: string | null;
  clientTs: Date | null;
  source: string | null;
  medium: string | null;
  campaign: string | null;
  props: Record<string, string | number | boolean>;
};

function str(value: unknown, max = MAX_FIELD): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, max) : null;
}

/**
 * 들어온 body를 저장 가능한 형태로 깎는다. 모르는 건 전부 버린다.
 * 저장할 게 없으면 null — 호출부는 그냥 조용히 넘어간다.
 */
export function clean(body: unknown): CleanEvent | null {
  if (!body || typeof body !== "object" || Array.isArray(body)) return null;
  const raw = body as Record<string, unknown>;

  const name = raw.event;
  if (typeof name !== "string" || !Object.hasOwn(ALLOWED_PROPS, name)) return null;
  const event = name as EventName;

  const props: Record<string, string | number | boolean> = {};
  for (const key of ALLOWED_PROPS[event] as readonly string[]) {
    const value = raw[key];
    if (typeof value === "boolean") {
      props[key] = value;
    } else if (typeof value === "number" && Number.isFinite(value)) {
      props[key] = value;
    } else if (typeof value === "string") {
      const text = str(value, key === "word" ? MAX_SUGGESTION : MAX_FIELD);
      if (text) props[key] = text;
    }
  }

  // 기기 시계는 틀어져 있을 수 있어서 순서는 server_ts로 본다.
  // client_ts는 "사용자 기기에서 언제였나"를 나중에 따질 때만 쓰는 참고값이다.
  const ts = typeof raw.timestamp === "number" ? new Date(raw.timestamp) : null;

  return {
    event,
    sessionId: str(raw.sessionId),
    wordId: str(raw.wordId),
    clientTs: ts && !Number.isNaN(ts.getTime()) ? ts : null,
    source: str(raw.source),
    medium: str(raw.medium),
    campaign: str(raw.campaign),
    props,
  };
}

export async function insert(e: CleanEvent): Promise<void> {
  await pool.query(
    `INSERT INTO events (session_id, event, word_id, client_ts, source, medium, campaign, props)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
    [
      e.sessionId,
      e.event,
      e.wordId,
      e.clientTs,
      e.source,
      e.medium,
      e.campaign,
      JSON.stringify(e.props),
    ],
  );

  // 추천 단어를 이벤트 로그에만 두면 꺼내 볼 때마다 JSONB를 헤집어야 한다.
  // 사람이 직접 읽을 유일한 데이터라 따로 뽑아 둔다.
  if (e.event === "word_suggested" && typeof e.props.word === "string") {
    await pool.query(`INSERT INTO suggestions (session_id, word) VALUES ($1, $2)`, [
      e.sessionId,
      e.props.word,
    ]);
  }
}
