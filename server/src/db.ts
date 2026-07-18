import { Pool } from "pg";

/**
 * Postgres 연결과 스키마.
 *
 * 마이그레이션 도구를 안 쓴다. 테이블이 둘뿐이고 컬럼을 바꿀 계획도 없어서,
 * `IF NOT EXISTS` 한 방이 도구를 들이는 것보다 싸다. 컬럼 변경이 필요해지는
 * 순간이 도구를 붙일 때다.
 */

export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
});

/**
 * `events`가 원본이고 통계는 전부 여기서 파생된다. 집계 테이블을 따로 두면
 * 집계 방식을 바꿀 때 이미 쌓인 데이터를 다시 계산할 수 없다. 지금 규모(하루
 * 수천 건)에서는 매번 세는 게 충분히 빠르고, 그 대가로 "착각을 어떻게 셀
 * 것인가"를 나중에 바꿀 자유가 남는다.
 *
 * IP는 저장하지 않는다. `session_id`는 sessionStorage에 있는 익명 UUID라
 * 탭을 닫으면 사라지고 사람과 이어지지 않는다.
 */
const SCHEMA = `
CREATE TABLE IF NOT EXISTS events (
  id          BIGSERIAL PRIMARY KEY,
  session_id  TEXT,
  event       TEXT NOT NULL,
  word_id     TEXT,
  client_ts   TIMESTAMPTZ,
  server_ts   TIMESTAMPTZ NOT NULL DEFAULT now(),
  source      TEXT,
  medium      TEXT,
  campaign    TEXT,
  props       JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 통계 쿼리가 word_id로 좁히고 event로 FILTER 하므로 이 순서.
CREATE INDEX IF NOT EXISTS events_word_event_idx ON events (word_id, event);
CREATE INDEX IF NOT EXISTS events_server_ts_idx  ON events (server_ts DESC);

CREATE TABLE IF NOT EXISTS suggestions (
  id          BIGSERIAL PRIMARY KEY,
  session_id  TEXT,
  word        TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
`;

/**
 * 부팅 시 스키마 보장.
 *
 * compose의 healthcheck가 있어도 db가 재시작하는 순간엔 어긋날 수 있어서
 * 몇 번 기다렸다 재시도한다. 여기서 그냥 죽으면 컨테이너가 재시작 루프를 돈다.
 */
export async function migrate(retries = 10): Promise<void> {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      await pool.query(SCHEMA);
      return;
    } catch (err) {
      if (attempt === retries) throw err;
      await new Promise((resolve) => setTimeout(resolve, 1000 * attempt));
    }
  }
}
