"use client";

import type { Choice } from "@/data/words";

/**
 * 보기 선택 단계.
 *
 * 결과 화면에서도 같은 컴포넌트를 재사용한다(revealed=true). 그래야 제출 전후로
 * 보기의 위치가 흔들리지 않아서, 자기가 뭘 골랐는지 눈으로 바로 추적할 수 있다.
 */

export default function ChoiceList({
  choices,
  value,
  onChange,
  revealed = false,
  answerId,
  percentages,
  disabled = false,
}: {
  choices: Choice[];
  value: string | null;
  onChange?: (id: string) => void;
  revealed?: boolean;
  answerId?: string;
  percentages?: Record<string, number>;
  disabled?: boolean;
}) {
  return (
    <fieldset className="w-full" disabled={disabled}>
      <legend className="sr-only">보기 중 하나를 선택하세요</legend>
      <div className="flex flex-col gap-2.5">
        {choices.map((choice, i) => {
          const selected = value === choice.id;
          const isAnswer = revealed && answerId === choice.id;
          const isWrongPick = revealed && selected && !isAnswer;
          const pct = percentages?.[choice.id];

          let tone = "border-border";
          if (isAnswer) tone = "border-correct bg-correct-soft";
          else if (isWrongPick) tone = "border-accent bg-accent-soft";
          else if (selected && !revealed) tone = "border-foreground bg-card";
          else if (!revealed) tone = "border-border hover:border-muted";
          else tone = "border-border opacity-60";

          const Wrapper = revealed ? "div" : "label";

          return (
            <Wrapper
              key={choice.id}
              className={`relative overflow-hidden rounded-xl border transition-colors ${tone} ${
                revealed ? "" : "cursor-pointer"
              }`}
            >
              {/* 선택 비율 막대 — 텍스트 뒤에 깔린다 */}
              {revealed && pct !== undefined && (
                <div
                  aria-hidden
                  className="absolute inset-y-0 left-0 bg-foreground/[0.06]"
                  style={{ width: `${pct}%` }}
                />
              )}

              <div className="relative flex items-start gap-3 px-4 py-4">
                {!revealed && (
                  <input
                    type="radio"
                    name="choice"
                    value={choice.id}
                    checked={selected}
                    onChange={() => onChange?.(choice.id)}
                    className="mt-0.5 size-4 shrink-0 accent-[var(--foreground)]"
                  />
                )}
                {revealed && (
                  <span
                    aria-hidden
                    className="mt-0.5 w-4 shrink-0 text-center text-sm font-semibold"
                  >
                    {isAnswer ? "✓" : isWrongPick ? "✕" : String.fromCharCode(65 + i)}
                  </span>
                )}

                <span className="flex-1 leading-relaxed">
                  {choice.text}
                  {revealed && selected && (
                    <span className="ml-2 text-xs font-medium text-muted">내 선택</span>
                  )}
                </span>

                {revealed && pct !== undefined && (
                  <span className="shrink-0 text-sm tabular-nums text-muted">{pct}%</span>
                )}
              </div>
            </Wrapper>
          );
        })}
      </div>
    </fieldset>
  );
}
