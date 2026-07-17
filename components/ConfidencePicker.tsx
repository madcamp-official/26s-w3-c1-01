"use client";

import type { ConfidenceLevel } from "@/data/words";

/**
 * 자기평가 단계. 이 응답이 나중에 정답 여부와 대조되어 '착각 지수'가 된다.
 *
 * 라디오 그룹 시맨틱을 쓴다. 네이티브 radio input이므로 화살표 키 이동과
 * 스크린리더 그룹 읽기가 공짜로 따라온다.
 */

const OPTIONS: { value: ConfidenceLevel; label: string; hint: string }[] = [
  { value: "high", label: "정확히 설명할 수 있다", hint: "누가 물어도 막힘없이" },
  { value: "mid", label: "대충 안다", hint: "설명하라면 좀 버벅일 듯" },
  { value: "low", label: "들어는 봤다", hint: "뜻은 잘 모르겠다" },
  { value: "none", label: "모른다", hint: "" },
];

export default function ConfidencePicker({
  word,
  value,
  onChange,
}: {
  word: string;
  value: ConfidenceLevel | null;
  onChange: (v: ConfidenceLevel) => void;
}) {
  return (
    <fieldset className="w-full">
      <legend className="mb-6 text-lg leading-relaxed">
        <span className="font-semibold">{word}</span>
        <span className="text-muted">, 얼마나 알고 있나요?</span>
      </legend>

      <div className="flex flex-col gap-2.5">
        {OPTIONS.map((opt) => {
          const selected = value === opt.value;
          return (
            <label
              key={opt.value}
              className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-4 transition-colors ${
                selected
                  ? "border-foreground bg-card"
                  : "border-border hover:border-muted"
              }`}
            >
              <input
                type="radio"
                name="confidence"
                value={opt.value}
                checked={selected}
                onChange={() => onChange(opt.value)}
                className="size-4 shrink-0 accent-[var(--foreground)]"
              />
              <span className="flex flex-col">
                <span className="font-medium">{opt.label}</span>
                {opt.hint && <span className="text-sm text-muted">{opt.hint}</span>}
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
