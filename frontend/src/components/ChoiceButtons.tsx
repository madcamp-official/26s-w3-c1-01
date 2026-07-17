import { useState } from "react";
import type { ContentType } from "../types";

interface Props {
  disabled: boolean;
  onChoose: (choice: ContentType) => void;
}

/** FE-02/FE-03: 클릭 즉시 제출하므로 별도 확정 단계는 없음. 제출 중엔 두 버튼 모두 잠근다. */
export function ChoiceButtons({ disabled, onChoose }: Props) {
  const [pending, setPending] = useState<ContentType | null>(null);

  const handleClick = (choice: ContentType) => {
    if (disabled) return;
    setPending(choice);
    onChoose(choice);
  };

  return (
    <div className="mt-6 grid grid-cols-2 gap-3">
      <button
        type="button"
        disabled={disabled}
        onClick={() => handleClick("REAL")}
        className="rounded-xl border-2 border-blue-500 px-4 py-4 text-lg font-semibold text-blue-600 transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-50 dark:text-blue-400 dark:hover:bg-blue-950"
      >
        실화
        {pending === "REAL" && disabled && <span className="ml-2 animate-pulse">...</span>}
      </button>
      <button
        type="button"
        disabled={disabled}
        onClick={() => handleClick("FABRICATED")}
        className="rounded-xl border-2 border-purple-500 px-4 py-4 text-lg font-semibold text-purple-600 transition hover:bg-purple-50 disabled:cursor-not-allowed disabled:opacity-50 dark:text-purple-400 dark:hover:bg-purple-950"
      >
        AI 창작
        {pending === "FABRICATED" && disabled && <span className="ml-2 animate-pulse">...</span>}
      </button>
    </div>
  );
}
