import { useState } from "react";
import { createQuizSession } from "../api/session";
import type { CreateSessionResponse, Difficulty } from "../types";

interface Props {
  onStart: (session: CreateSessionResponse) => void;
}

const CATEGORIES = ["전체", "역사", "사회", "과학", "인물"];
const DIFFICULTIES: Array<Difficulty | "전체"> = ["전체", "EASY", "MEDIUM", "HARD"];

/** FE-08: 카테고리/난이도 필터(선택 기능). */
export function StartScreen({ onStart }: Props) {
  const [category, setCategory] = useState("전체");
  const [difficulty, setDifficulty] = useState<Difficulty | "전체">("전체");
  const [count, setCount] = useState(5);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleStart = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const session = await createQuizSession({
        category: category === "전체" ? undefined : category,
        difficulty: difficulty === "전체" ? undefined : difficulty,
        count,
      });
      onStart(session);
    } catch (e) {
      setError(e instanceof Error ? e.message : "세션을 시작하지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 p-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">실화 vs AI 창작</h1>
        <p className="mt-1 text-gray-500 dark:text-gray-400">
          지문을 읽고 실화인지 AI가 지어낸 이야기인지 맞혀보세요.
        </p>
      </div>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">카테고리</span>
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-800"
        >
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">난이도</span>
        <select
          value={difficulty}
          onChange={(e) => setDifficulty(e.target.value as Difficulty | "전체")}
          className="rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-800"
        >
          {DIFFICULTIES.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">문항 수</span>
        <input
          type="number"
          min={1}
          max={20}
          value={count}
          onChange={(e) => setCount(Number(e.target.value))}
          className="rounded-lg border border-gray-300 px-3 py-2 dark:border-gray-600 dark:bg-gray-800"
        />
      </label>

      {error && <p className="text-sm text-red-500">{error}</p>}

      <button
        type="button"
        onClick={handleStart}
        disabled={isLoading}
        className="rounded-xl bg-blue-600 px-4 py-3 font-semibold text-white transition hover:bg-blue-700 disabled:opacity-50"
      >
        {isLoading ? "시작하는 중..." : "퀴즈 시작"}
      </button>
    </div>
  );
}
