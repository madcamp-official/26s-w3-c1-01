"use client";

import type { Word } from "@/data/words";
import { claimedToKnow } from "@/lib/quiz";
import type { ConfidenceLevel } from "@/data/words";
import { STATS_ARE_PLACEHOLDER, type WordStats } from "@/lib/stats";

/**
 * 결과 화면의 콘텐츠 영역.
 *
 * 화면 맨 위에 오는 건 정답 여부가 아니라 '자기평가와 실제의 차이'다.
 * "안다고 했는데 틀렸다"가 이 제품이 보여주려는 전부이므로 그걸 가장 먼저 말한다.
 */

export default function ResultPanel({
  word,
  confidence,
  correct,
  pickedChoiceId,
  stats,
}: {
  word: Word;
  confidence: ConfidenceLevel;
  correct: boolean;
  pickedChoiceId: string;
  stats: WordStats | null;
}) {
  const claimed = claimedToKnow(confidence);
  const isIllusion = claimed && !correct;
  const picked = word.choices.find((c) => c.id === pickedChoiceId);

  return (
    <div className="flex flex-col gap-8">
      {/* 자기평가 vs 실제 */}
      <div
        className={`rounded-2xl border p-5 ${
          isIllusion ? "border-accent bg-accent-soft" : "border-border bg-card"
        }`}
        aria-live="polite"
      >
        <p className="text-sm font-medium text-muted">
          {isIllusion
            ? "안다고 답했지만, 설명은 빗나갔습니다"
            : correct
              ? "정확히 알고 있었습니다"
              : "모른다고 답했고, 실제로도 그랬습니다"}
        </p>
        <p className="mt-2 text-lg leading-relaxed">
          {isIllusion
            ? `이게 바로 "안다고 착각한 단어"입니다.`
            : correct
              ? "자기평가와 실제가 일치했습니다."
              : "적어도 착각하지는 않았습니다."}
        </p>
      </div>

      {/* 내가 고른 오답이 왜 그럴듯했는지 */}
      {!correct && picked?.misconception && (
        <section>
          <h2 className="mb-2 text-sm font-semibold text-muted">왜 이렇게 생각했을까요</h2>
          <p className="leading-relaxed">{picked.misconception}</p>
        </section>
      )}

      {/* 정확한 정의 */}
      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted">정확한 뜻</h2>
        <p className="leading-relaxed">{word.definition}</p>
      </section>

      {/* 설명에 들어갔어야 할 것 */}
      <section>
        <h2 className="mb-3 text-sm font-semibold text-muted">
          제대로 설명하려면 이게 들어가야 합니다
        </h2>
        <ul className="flex flex-col gap-2">
          {word.keyPoints.map((point) => (
            <li key={point} className="flex gap-2.5 leading-relaxed">
              <span aria-hidden className="text-muted">
                •
              </span>
              <span>{point}</span>
            </li>
          ))}
        </ul>
      </section>

      {/* 해설 */}
      <section className="rounded-2xl border border-border bg-card p-5">
        <h2 className="mb-2 text-sm font-semibold text-muted">놓치기 쉬운 지점</h2>
        <p className="leading-relaxed">{word.note}</p>
      </section>

      {/* 다른 사람들 */}
      {stats && (
        <section>
          <h2 className="mb-2 flex flex-wrap items-center gap-2 text-sm font-semibold text-muted">
            다른 사람들은
            {STATS_ARE_PLACEHOLDER && (
              <span className="rounded-full border border-border px-2 py-0.5 text-[11px] font-medium text-muted">
                예시 수치
              </span>
            )}
          </h2>
          <p className="leading-relaxed">
            이 단어를 <strong className="font-semibold">안다</strong>고 답한 사람 중{" "}
            <strong className="font-semibold">
              {Math.round(stats.confidentCorrectRate * 100)}%
            </strong>
            만 정확한 설명을 골랐습니다.
          </p>
          {STATS_ARE_PLACEHOLDER && (
            <p className="mt-2 text-xs leading-relaxed text-muted">
              아직 실제 사용자 데이터가 연결되지 않아 예시로 채워진 숫자입니다.
            </p>
          )}
        </section>
      )}
    </div>
  );
}
