"use client";

import { useState } from "react";
import { track } from "@/lib/analytics";

/**
 * 단어 추천 받기.
 *
 * ⚠️ 백엔드가 없어서 지금은 **아무 데도 전송되지 않는다.** track()이 콘솔과
 * localStorage 버퍼에만 남기고, GA가 붙어 있으면 GA로도 간다. 즉 GA를 연결하기
 * 전까지 사용자가 "보내기"를 눌러도 개발자는 그 내용을 볼 수 없다.
 *
 * 통계 가짜 수치와 같은 종류의 문제다. 공개 배포 전에 GA나 백엔드를 붙이거나,
 * 이 컴포넌트를 빼야 한다. docs/PROJECT_NOTES.md의 "배포 전 체크리스트" 참고.
 *
 * 그럼에도 지금 만들어 두는 이유: 어차피 붙일 이벤트 seam이 이미 있고,
 * 무엇을 물어볼지(문구·예시)를 미리 정해 둬야 배포 직전에 급조하지 않는다.
 */

export default function SuggestBox() {
  const [value, setValue] = useState("");
  const [sent, setSent] = useState(false);

  function submit() {
    const word = value.trim();
    if (!word) return;
    track("word_suggested", { word, length: word.length });
    setSent(true);
    setValue("");
  }

  return (
    <section className="rounded-2xl border border-border bg-card p-5">
      <h2 className="font-semibold">💡 이런 단어도 넣어 주세요</h2>
      <p className="mt-1 text-sm text-muted">
        한 단어면 충분해요. 다음 단어 후보로 담아 둡니다.
      </p>

      {sent ? (
        <p className="mt-4 text-sm font-medium text-correct" role="status">
          담아 뒀습니다. 고맙습니다.
        </p>
      ) : (
        <>
          <div className="mt-4 flex gap-2">
            <label htmlFor="suggest" className="sr-only">
              추천할 단어
            </label>
            <input
              id="suggest"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => {
                // 한글 조합 중의 Enter는 글자 확정용이라 가로채면 안 된다.
                if (e.key === "Enter" && !e.nativeEvent.isComposing) submit();
              }}
              placeholder="예: 엔트로피, GDP, 백신..."
              className="min-w-0 flex-1 rounded-xl border border-border bg-background px-4 py-3 text-base outline-none placeholder:text-muted focus:border-foreground"
            />
            <button
              type="button"
              onClick={submit}
              disabled={!value.trim()}
              className="shrink-0 rounded-xl border border-border px-4 py-3 text-sm font-medium transition-colors hover:border-foreground disabled:opacity-40"
            >
              보내기
            </button>
          </div>
          <p className="mt-2 text-xs text-muted">개인정보는 적지 말아 주세요.</p>
        </>
      )}
    </section>
  );
}
