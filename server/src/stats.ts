import { pool } from "./db.js";

/**
 * 통계 집계.
 *
 * ⚠️ 표본이 MIN_SAMPLE보다 적으면 수치를 아예 안 준다(null). UI는 null이면
 * 문구를 통째로 숨긴다.
 *
 * 이게 이 파일에서 제일 중요한 규칙이다. 3명 중 1명 틀린 걸 "33%만
 * 설명해냈습니다"라고 보여주는 건, 지어낸 수치를 보여주던 예전(seedStats)과
 * 정확히 같은 거짓말이다 — 진짜 쿼리에서 나왔다는 것만 다르다. 숫자가 없으면
 * 없다고 하는 게 맞고, 그동안 랜딩에는 사회적 증거가 없다. 그게 정직한 상태다.
 */
const MIN_SAMPLE = 30;

/**
 * 랜딩은 모든 방문자가 때리는 경로다. 매 요청마다 전체 스캔을 돌릴 이유가 없고,
 * 30초 늦은 비율은 아무도 눈치채지 못한다.
 */
const CACHE_MS = 30_000;

export type WordStats = {
  /** "안다"고 답한 사람 중 스스로 맞았다고 판정한 비율 (0~1) */
  confidentCorrectRate: number;
};

const cache = new Map<string, { at: number; value: WordStats | null }>();

export async function wordStats(wordId: string): Promise<WordStats | null> {
  const hit = cache.get(wordId);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.value;

  // self_judged는 "안다" 경로에서만 발생하므로 correct ⊆ confident다.
  // 세션 단위로 세는 이유: 한 사람이 새로고침으로 같은 단어를 두 번 답해도
  // 한 명으로 친다.
  const { rows } = await pool.query<{ confident: string; correct: string }>(
    `SELECT
       count(DISTINCT session_id) FILTER (
         WHERE event = 'confidence_selected' AND props->>'knew' = 'true'
       ) AS confident,
       count(DISTINCT session_id) FILTER (
         WHERE event = 'self_judged' AND props->>'judgment' = 'correct'
       ) AS correct
     FROM events
     WHERE word_id = $1`,
    [wordId],
  );

  const confident = Number(rows[0]?.confident ?? 0);
  const correct = Number(rows[0]?.correct ?? 0);
  const value = confident >= MIN_SAMPLE ? { confidentCorrectRate: correct / confident } : null;

  cache.set(wordId, { at: Date.now(), value });
  return value;
}

/**
 * 관리자 요약. 대시보드는 만들지 않는다 — 이 JSON을 curl로 보면 된다.
 *
 * 이 엔드포인트가 있는 이유는 과제 옵션 2가 "반응을 바탕으로 최소 한 번 이상
 * 실제로 수정"할 것을 요구하기 때문이다. 수치를 볼 방법이 없으면 옵션 2를
 * 수행할 수 없다. 그 이상은 만들지 않는다.
 */
export async function summary() {
  const [funnel, bySource, words, suggestions] = await Promise.all([
    // 이 실험의 본선 지표. landing → started → completed → shared.
    pool.query(
      `SELECT
         count(DISTINCT session_id) FILTER (WHERE event = 'landing_view')     AS landing,
         count(DISTINCT session_id) FILTER (WHERE event = 'test_start')       AS started,
         count(DISTINCT session_id) FILTER (WHERE event = 'session_complete') AS completed,
         count(DISTINCT session_id) FILTER (WHERE event = 'share_click')      AS shared
       FROM events`,
    ),
    // "팀원/지인이 아닌, 공개 경로로 유입된 실제 사용자"를 가려내려면 유입별로 봐야 한다.
    pool.query(
      `SELECT
         coalesce(source, '(direct)') AS source,
         count(DISTINCT session_id) FILTER (WHERE event = 'test_start')       AS started,
         count(DISTINCT session_id) FILTER (WHERE event = 'session_complete') AS completed
       FROM events
       GROUP BY 1
       ORDER BY 2 DESC`,
    ),
    // 단어 선정이 제품의 전부다. blank(한 글자도 못 씀)가 높은 단어가 제일 잘 고른 단어고,
    // seen 대비 claimed_known이 낮으면 아무도 모르는 단어라 착각이 안 생긴다.
    pool.query(
      `SELECT
         word_id,
         count(DISTINCT session_id) FILTER (WHERE event = 'confidence_selected') AS seen,
         count(DISTINCT session_id) FILTER (
           WHERE event = 'confidence_selected' AND props->>'knew' = 'true') AS claimed_known,
         count(DISTINCT session_id) FILTER (
           WHERE event = 'self_judged' AND props->>'judgment' = 'correct') AS judged_correct,
         count(DISTINCT session_id) FILTER (
           WHERE event = 'self_judged' AND props->>'judgment' = 'partial') AS judged_partial,
         count(DISTINCT session_id) FILTER (
           WHERE event = 'self_judged' AND props->>'judgment' = 'wrong')   AS judged_wrong,
         count(DISTINCT session_id) FILTER (
           WHERE event = 'answer_submitted' AND props->>'gaveUp' = 'true') AS blank
       FROM events
       WHERE word_id IS NOT NULL
       GROUP BY word_id
       ORDER BY seen DESC`,
    ),
    pool.query(
      `SELECT word, count(*) AS n, max(created_at) AS last_at
       FROM suggestions
       GROUP BY word
       ORDER BY n DESC, last_at DESC
       LIMIT 100`,
    ),
  ]);

  return {
    minSample: MIN_SAMPLE,
    funnel: funnel.rows[0],
    bySource: bySource.rows,
    words: words.rows,
    suggestions: suggestions.rows,
  };
}
