/**
 * 통계 조회 seam.
 *
 * 지금은 seed 파일에서 읽지만 나중에 API에서 읽게 된다. UI는 이 파일 너머를
 * 알 필요가 없다. 그래서 지금 당장 필요 없는데도 Promise를 반환한다 — 나중에
 * fetch로 바꿔도 호출부를 안 고치기 위해서다.
 *
 * 백엔드가 생기면 이 파일의 함수 본문만 갈아끼우면 된다.
 */

import { SEED_STATS, STATS_ARE_PLACEHOLDER, type WordStats } from "@/data/seedStats";

export type { WordStats };
export { STATS_ARE_PLACEHOLDER };

export async function getWordStats(wordId: string): Promise<WordStats | null> {
  // 나중: return fetch(`/api/stats/${wordId}`).then(r => r.json())
  return SEED_STATS[wordId] ?? null;
}

/** choiceId → 백분율(0~100). 반올림 오차는 무시한다. */
export function toPercentages(stats: WordStats): Record<string, number> {
  const total = Object.values(stats.choiceCounts).reduce((a, b) => a + b, 0);
  if (total === 0) return {};
  return Object.fromEntries(
    Object.entries(stats.choiceCounts).map(([id, n]) => [id, Math.round((n / total) * 100)]),
  );
}
