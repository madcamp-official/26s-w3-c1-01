import { useEffect, useState } from "react";
import type { AnswerResult } from "../types";

interface Props {
  reveal: AnswerResult;
  onNext: () => void;
}

const STEPS = ["result", "title", "fact", "comment"] as const;
type Step = (typeof STEPS)[number];

/** FE-04: ✅/❌ → answer_title → answer_fact → reveal_comment 순으로 클릭마다 한 단계씩 공개. */
export function RevealPanel({ reveal, onNext }: Props) {
  const [stepIndex, setStepIndex] = useState(0);

  useEffect(() => {
    setStepIndex(0);
  }, [reveal.card_id]);

  const step: Step = STEPS[stepIndex];
  const isLastStep = stepIndex === STEPS.length - 1;

  const advance = () => {
    if (isLastStep) {
      onNext();
    } else {
      setStepIndex((i) => i + 1);
    }
  };

  return (
    <div className="mt-6 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <div
        className={`text-2xl font-bold ${reveal.is_correct ? "text-green-600 dark:text-green-400" : "text-red-500 dark:text-red-400"}`}
      >
        {reveal.is_correct ? "✅ 정답입니다!" : "❌ 오답입니다"}
      </div>

      {stepIndex >= 1 && (
        <p className="mt-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {reveal.answer_title}
        </p>
      )}
      {stepIndex >= 2 && (
        <p className="mt-2 text-gray-700 dark:text-gray-300">{reveal.answer_fact}</p>
      )}
      {stepIndex >= 3 && (
        <>
          <p className="mt-2 italic text-gray-500 dark:text-gray-400">{reveal.reveal_comment}</p>
          {reveal.source_url && (
            <a
              href={reveal.source_url}
              target="_blank"
              rel="noreferrer"
              className="mt-2 inline-block text-sm text-blue-600 underline dark:text-blue-400"
            >
              출처 보기
            </a>
          )}
        </>
      )}

      <button
        type="button"
        onClick={advance}
        className="mt-6 w-full rounded-xl bg-gray-900 px-4 py-3 font-semibold text-white transition hover:bg-gray-700 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white"
      >
        {step === "comment" ? "다음 카드" : "다음"}
      </button>
    </div>
  );
}
