import { useState } from "react";
import type { AnswerResult, ResultCard } from "../types";

interface Props {
  cards: ResultCard[];
  /** 퀴즈 진행 중 로컬에 캐시해둔 상세 reveal 데이터(있으면 재열람 시 제목/근거/코멘트까지 표시). */
  detailsByCardId: Map<string, AnswerResult>;
}

const LABEL: Record<"REAL" | "FABRICATED", string> = { REAL: "실화", FABRICATED: "AI 창작" };

export function ResultCardList({ cards, detailsByCardId }: Props) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <ul className="mt-4 flex flex-col gap-2">
      {cards.map((c) => {
        const detail = detailsByCardId.get(c.card_id);
        const isExpanded = expandedId === c.card_id;
        return (
          <li
            key={c.card_id}
            className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
          >
            <button
              type="button"
              onClick={() => setExpandedId(isExpanded ? null : c.card_id)}
              className="flex w-full items-center justify-between text-left"
            >
              <span className="font-medium text-gray-900 dark:text-gray-100">
                {c.is_correct ? "✅" : "❌"} {detail?.answer_title ?? c.card_id}
              </span>
              <span className="text-sm text-gray-500 dark:text-gray-400">
                내 선택: {LABEL[c.user_choice]} · 정답: {LABEL[c.correct_type]}
              </span>
            </button>
            {isExpanded && (
              <div className="mt-3 border-t border-gray-100 pt-3 text-sm text-gray-700 dark:border-gray-700 dark:text-gray-300">
                {detail ? (
                  <>
                    <p className="font-semibold">{detail.answer_title}</p>
                    <p className="mt-1">{detail.answer_fact}</p>
                    <p className="mt-1 italic text-gray-500 dark:text-gray-400">
                      {detail.reveal_comment}
                    </p>
                  </>
                ) : (
                  <p className="text-gray-400">상세 정보는 이번 세션에서만 재열람할 수 있어요.</p>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
