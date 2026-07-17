import type { Progress, Score } from "../types";

export function ScoreProgress({ progress, score }: { progress: Progress | null; score: Score }) {
  const current = progress?.current ?? 0;
  const total = progress?.total ?? 0;
  const pct = total > 0 ? Math.round((current / total) * 100) : 0;

  return (
    <div className="mb-4">
      <div className="flex items-center justify-between text-sm font-medium text-gray-600 dark:text-gray-300">
        <span>
          {current}/{total}
        </span>
        <span>
          점수 {score.correct}/{score.total}
        </span>
      </div>
      <div className="mt-1 h-2 w-full rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          className="h-2 rounded-full bg-blue-500 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
