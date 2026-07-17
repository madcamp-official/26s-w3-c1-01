/**
 * 착각 지수 계산과 공유 텍스트 생성.
 *
 * 이 앱의 점수는 정답률이 아니라 '착각률'이다. 주제가 "안다고 착각한 단어"이므로
 * 몇 개 맞혔는지는 부차적이고, "안다고 했는데 설명 못 한 개수"가 핵심 지표다.
 *
 * 공유 텍스트는 Wordle 규칙을 따른다: 정답도, 어떤 단어였는지도 노출하지 않는다.
 * 스포일러가 있으면 받은 사람이 풀 이유가 사라지고 확산이 멈춘다.
 */

import type { SessionAnswer } from "./session";

/**
 * 상태가 3개인 이유: "모른다"를 누르면 설명을 쓰지 않고 넘어가므로 판정 자체가
 * 없다. 모른다고 하고 맞히는 경우(찍어서 맞음)가 객관식과 달리 존재하지 않는다.
 */
export type CellKind = "illusion" | "known" | "admitted";

const CELL_EMOJI: Record<CellKind, string> = {
  illusion: "🟥", // 안다고 했는데 설명 못 함 ← 이 앱의 주제
  known: "🟩", // 안다고 했고 설명해냄
  admitted: "⬜", // 모른다고 인정함
};

export function cellKind(answer: SessionAnswer): CellKind {
  if (!answer.knew) return "admitted";
  return answer.correct ? "known" : "illusion";
}

export function cellEmoji(answer: SessionAnswer): string {
  return CELL_EMOJI[cellKind(answer)];
}

export type SessionScore = {
  total: number;
  /** 안다고 했는데 설명 못 한 개수 = 착각 지수 */
  illusions: number;
  /** 안다고 답한 문항 수 */
  claimed: number;
  grid: string;
};

/** order 순서대로 정렬해 점수를 낸다. answers는 응답 순서라 섞여 있을 수 있다. */
export function scoreSession(order: string[], answers: SessionAnswer[]): SessionScore {
  const byWord = new Map(answers.map((a) => [a.wordId, a]));
  const ordered = order.map((id) => byWord.get(id)).filter((a): a is SessionAnswer => !!a);

  return {
    total: ordered.length,
    illusions: ordered.filter((a) => cellKind(a) === "illusion").length,
    claimed: ordered.filter((a) => a.knew).length,
    grid: ordered.map(cellEmoji).join(""),
  };
}

/** 착각 지수에 붙는 한 줄. 짧게 유지할 것 — 읽을 글자를 늘리지 않는다. */
export function verdict(score: SessionScore): string {
  if (score.claimed === 0) return "아무것도 안다고 하지 않았습니다.";
  if (score.illusions === 0) return "안다고 한 건 전부 설명해냈습니다.";
  if (score.illusions === score.claimed) return "안다고 한 걸 하나도 설명하지 못했습니다.";
  if (score.illusions >= 3) return "안다고 믿은 것 대부분이 착각이었습니다.";
  if (score.illusions >= 2) return "안다고 한 단어를 설명하지 못했습니다.";
  return "착각한 단어가 하나 있었습니다.";
}

/**
 * 공유 텍스트. 단어명·정답 무엇도 드러나지 않는다.
 * 이모지 격자만으로 "얼마나 착각했는지"가 전달되는 게 목표다.
 */
export function buildShareText(score: SessionScore, url: string): string {
  return [
    "내가 안다고 착각한 단어",
    `착각 지수 ${score.illusions}/${score.total}`,
    "",
    score.grid,
    "",
    url,
  ].join("\n");
}
