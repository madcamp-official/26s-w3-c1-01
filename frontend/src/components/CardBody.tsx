import type { QuizCard } from "../types";

export function CardBody({ card }: { card: QuizCard }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <div className="mb-3 flex gap-2 text-xs font-medium text-gray-500 dark:text-gray-400">
        <span className="rounded-full bg-gray-100 px-2 py-1 dark:bg-gray-700">{card.category}</span>
        {card.difficulty && (
          <span className="rounded-full bg-gray-100 px-2 py-1 dark:bg-gray-700">{card.difficulty}</span>
        )}
      </div>
      <p className="text-lg leading-relaxed text-gray-900 dark:text-gray-100">{card.body}</p>
    </div>
  );
}
