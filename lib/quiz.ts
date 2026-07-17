/**
 * 착각 지수 계산과 공유 텍스트 생성.
 *
 * 이 앱의 점수는 정답률이 아니라 '착각률'이다. 주제가 "안다고 착각한 단어"이므로
 * 몇 개 맞혔는지는 부차적이고, "안다고 했는데 틀린 개수"가 핵심 지표다.
 *
 * 공유 텍스트는 Wordle 규칙을 따른다: 정답도, 어떤 단어였는지도 노출하지 않는다.
 * 스포일러가 있으면 받은 사람이 풀 이유가 사라지고 확산이 멈춘다.
 */

import type { ConfidenceLevel } from "@/data/words";
import type { SessionAnswer } from "./session";

/** "안다"고 주장한 것으로 간주하는 자기평가 수준. */
export function claimedToKnow(c: ConfidenceLevel): boolean {
  return c === "high" || c === "mid";
}

export type CellKind = "illusion" | "known" | "lucky" | "honest";

const CELL_EMOJI: Record<CellKind, string> = {
  illusion: "🟥", // 안다고 했는데 틀림 ← 이 앱의 주제
  known: "🟩", // 안다고 했고 맞음
  lucky: "🟦", // 모른다고 했는데 맞음
  honest: "⬜", // 모른다고 했고 틀림
};

export function cellKind(answer: SessionAnswer): CellKind {
  const claimed = claimedToKnow(answer.confidence);
  if (claimed && !answer.correct) return "illusion";
  if (claimed && answer.correct) return "known";
  if (!claimed && answer.correct) return "lucky";
  return "honest";
}

export function cellEmoji(answer: SessionAnswer): string {
  return CELL_EMOJI[cellKind(answer)];
}

export type SessionScore = {
  total: number;
  correct: number;
  /** 안다고 했는데 틀린 개수 = 착각 지수 */
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
    correct: ordered.filter((a) => a.correct).length,
    illusions: ordered.filter((a) => cellKind(a) === "illusion").length,
    claimed: ordered.filter((a) => claimedToKnow(a.confidence)).length,
    grid: ordered.map(cellEmoji).join(""),
  };
}

/** 착각 지수에 붙는 한 줄 평. */
export function verdict(score: SessionScore): string {
  if (score.total === 0) return "아직 푼 단어가 없습니다.";
  if (score.illusions === 0) {
    return score.correct === score.total
      ? "착각이 없었습니다. 아는 것과 안다고 믿는 것이 일치합니다."
      : "틀린 건 있어도 착각은 없었습니다. 모르는 걸 모른다고 아는 편입니다.";
  }
  if (score.illusions >= 4) return "안다고 믿었던 것 대부분이 착각이었습니다.";
  if (score.illusions >= 2) return "안다고 답한 단어 중 상당수를 설명하지 못했습니다.";
  return "대체로 정확하지만, 착각한 단어가 하나 있었습니다.";
}

/**
 * 공유 텍스트. 단어명·정답·선택지 무엇도 드러나지 않는다.
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

/** 단어 하나를 공유할 때 (정답 노출 없음). */
export function buildWordShareText(word: string, url: string): string {
  return [`"${word}"을(를) 정확히 설명할 수 있나요?`, "", url].join("\n");
}
