import { useEffect, useMemo, useState } from "react";
import { getResult } from "../api/result";
import { ResultCardList } from "../components/ResultCardList";
import type { AnswerResult, SessionResult } from "../types";

interface Props {
  sessionId: string;
  history: AnswerResult[];
  onRestart: () => void;
}

/** FE-07: 총점 + 카드별 정오답 리스트, 카드 재열람. */
export function ResultScreen({ sessionId, history, onRestart }: Props) {
  const [result, setResult] = useState<SessionResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const detailsByCardId = useMemo(() => {
    return new Map(history.map((h) => [h.card_id, h]));
  }, [history]);

  useEffect(() => {
    getResult(sessionId)
      .then(setResult)
      .catch((e) => setError(e instanceof Error ? e.message : "결과를 불러오지 못했습니다."));
  }, [sessionId]);

  return (
    <div className="mx-auto max-w-md p-6">
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">결과</h1>

      {error && <p className="mt-3 text-sm text-red-500">{error}</p>}

      {result && (
        <>
          <p className="mt-2 text-lg text-gray-700 dark:text-gray-300">
            {result.correct} / {result.total} 정답
          </p>
          <ResultCardList cards={result.cards} detailsByCardId={detailsByCardId} />
        </>
      )}

      <button
        type="button"
        onClick={onRestart}
        className="mt-6 w-full rounded-xl bg-blue-600 px-4 py-3 font-semibold text-white transition hover:bg-blue-700"
      >
        다시 시작
      </button>
    </div>
  );
}
