"use client";

import type { Word } from "@/data/words";
import { STATS_ARE_PLACEHOLDER, type WordStats } from "@/lib/stats";

/**
 * 정답 공개 + 자기판정.
 *
 * LLM도 키워드 매칭도 쓰지 않는 이유: 내가 쓴 "다수결로 정하는 거"와 정답이
 * 나란히 놓이면 부정할 수가 없다. 판정은 채점기가 아니라 본인이 한다.
 * (키워드 매칭은 "국민이 주인인 나라"를 틀렸다고 하는데, 그러면 사용자는
 * 자기가 틀렸다고 느끼는 게 아니라 앱이 고장났다고 느낀다.)
 *
 * 판정 버튼이 곧 다음 단어로 넘어가는 버튼이다. 확인 → 판정 → 다음으로 탭을
 * 세 번 시키면 그만큼 이탈한다.
 */

export default function RevealPanel({
  word,
  text,
  stats,
  onJudge,
}: {
  word: Word;
  /** 사용자가 쓴 설명. 빈 문자열이면 한 글자도 못 쓴 것 — 가장 순수한 착각이다. */
  text: string;
  stats: WordStats | null;
  onJudge: (correct: boolean) => void;
}) {
  const blank = text.length === 0;

  return (
    <div className="mt-8 flex flex-col gap-6">
      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted">내가 쓴 설명</h2>
        <p
          className={`whitespace-pre-wrap rounded-xl border border-border px-4 py-3 leading-relaxed ${
            blank ? "text-muted" : ""
          }`}
        >
          {blank ? "한 글자도 쓰지 못했습니다" : text}
        </p>
      </section>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted">실제 뜻</h2>
        <p className="rounded-xl border border-correct bg-correct-soft px-4 py-3 leading-relaxed">
          {word.definition}
        </p>
        <ul className="mt-3 flex flex-wrap gap-2">
          {word.keyPoints.map((point) => (
            <li key={point} className="rounded-full border border-border px-3 py-1 text-sm">
              {point}
            </li>
          ))}
        </ul>
      </section>

      {stats && (
        <p className="text-sm text-muted">
          안다고 답한 사람 중 {Math.round(stats.confidentCorrectRate * 100)}%만 설명해냈습니다
          {STATS_ARE_PLACEHOLDER && " (예시 수치)"}
        </p>
      )}

      <div>
        <p className="mb-3 text-center font-medium">내 설명, 맞았나요?</p>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => onJudge(true)}
            className="flex-1 rounded-xl border border-border py-4 font-medium transition-colors hover:border-foreground"
          >
            맞았다
          </button>
          <button
            type="button"
            onClick={() => onJudge(false)}
            className="flex-1 rounded-xl border border-border py-4 font-medium transition-colors hover:border-foreground"
          >
            틀렸다
          </button>
        </div>
      </div>
    </div>
  );
}
