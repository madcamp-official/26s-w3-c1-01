"use client";

import { matchKeywords, type Word } from "@/data/words";
import type { Judgment } from "@/lib/session";
import type { WordStats } from "@/lib/stats";

/**
 * 정답 공개 + 자기판정.
 *
 * 키워드 체크는 **힌트지 채점이 아니다.** 초록 체크가 몇 개 켜졌는지를 보고
 * 사용자가 감을 잡되, 최종 판정은 본인이 한다. 그래서 체크가 0개여도 "맞았다"를
 * 누를 수 있고, 3개여도 "틀렸다"를 누를 수 있다. 이게 중요하다 — 키워드 매칭이
 * 판정까지 하면 "국민이 주인인 나라"라고 잘 쓴 사람이 틀렸다는 소리를 듣는다.
 *
 * 판정 버튼이 곧 다음 단어로 넘어가는 버튼이다. 확인 → 판정 → 다음으로 탭을
 * 세 번 시키면 그만큼 이탈한다.
 */

const JUDGMENTS: { value: Judgment; label: string }[] = [
  { value: "correct", label: "맞았다" },
  { value: "partial", label: "애매하다" },
  { value: "wrong", label: "틀렸다" },
];

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
  onJudge: (judgment: Judgment) => void;
}) {
  const blank = text.length === 0;
  const hits = matchKeywords(text, word.keywords);

  return (
    <div className="mt-6 flex flex-col gap-5">
      <section>
        <h2 className="mb-1.5 text-xs font-semibold text-muted">내가 쓴 설명</h2>
        <p
          className={`whitespace-pre-wrap rounded-xl border border-border px-4 py-3 leading-relaxed ${
            blank ? "text-muted" : ""
          }`}
        >
          {blank ? "한 글자도 쓰지 못했습니다" : text}
        </p>
      </section>

      <section>
        <h2 className="mb-1.5 text-xs font-semibold text-muted">실제 뜻</h2>
        <p className="rounded-xl border border-correct bg-correct-soft px-4 py-3 leading-relaxed">
          {word.definition}
        </p>

        <ul className="mt-3 flex flex-wrap gap-2">
          {word.keywords.map((kw, i) => (
            <li
              key={kw.label}
              className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm transition-colors ${
                hits[i]
                  ? "border-correct bg-correct-soft font-medium text-correct"
                  : "border-border text-muted"
              }`}
            >
              <span aria-hidden>{hits[i] ? "✓" : "○"}</span>
              {kw.label}
              <span className="sr-only">{hits[i] ? " 언급함" : " 언급 안 함"}</span>
            </li>
          ))}
        </ul>
      </section>

      {/* stats가 null이면 통째로 사라진다. 표본이 모자란다는 뜻이지 에러가 아니다. */}
      {stats && (
        <p className="text-xs text-muted">
          안다고 답한 사람 중 {Math.round(stats.confidentCorrectRate * 100)}%만 설명해냈습니다
        </p>
      )}

      <div>
        <p className="mb-2.5 text-center font-medium">내 설명, 맞았나요?</p>
        <div className="flex gap-2">
          {JUDGMENTS.map((j) => (
            <button
              key={j.value}
              type="button"
              onClick={() => onJudge(j.value)}
              className="flex-1 rounded-xl border border-border py-4 text-sm font-medium transition-colors hover:border-foreground"
            >
              {j.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
