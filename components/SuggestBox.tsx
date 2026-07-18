"use client";

import { useState } from "react";
import { track } from "@/lib/analytics";

/**
 * 단어 추천 받기.
 *
 * 이 앱이 서버에 저장하는 **유일한 자유 입력**이다. 설명 원문은 저장하지 않기로
 * 했는데 여기만 예외인 이유는, 다음에 무슨 단어를 넣을지가 이 제품의 전부인데
 * 그건 사용자한테 묻는 것 말고 알아낼 방법이 없기 때문이다.
 *
 * 그래서 40자에서 자르고("한 단어면 충분해요"), 개인정보를 적지 말라고 명시하고,
 * 서버(`server/src/events.ts`)에서 한 번 더 자른다.
 *
 * 전송 실패해도 "담아 뒀습니다"가 뜬다. sendBeacon은 응답을 안 보기 때문인데,
 * 여기서 실패를 알려 봐야 사용자가 할 수 있는 게 없다.
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
