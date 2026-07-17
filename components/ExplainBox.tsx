"use client";

import { useEffect, useRef, useState } from "react";

/**
 * 설명 입력 + 5초 게이트.
 *
 * 이 컴포넌트가 이 앱의 전부다. "어 나 이거 알지! ...막상 쓰려니 힘드네?"라는
 * 5초를 만들어내는 게 목적이고, 나머지 화면은 그 5초로 데려가거나 그 5초를
 * 확인시켜 줄 뿐이다.
 *
 * 왜 버튼을 5초간 막는가: 안 막으면 빈칸으로 즉시 넘겨버린다. 그러면 착각이
 * 드러나기 전에 화면이 지나가고 아무 일도 일어나지 않는다. 강제로 빈 텍스트
 * 박스를 마주하게 하는 5초가 곧 제품이다.
 *
 * 빈칸 제출은 막지 않는다. 안다고 해놓고 한 글자도 못 쓰는 것 — 그게 가장 순수한
 * 착각이라 오히려 살려둬야 한다. 별도의 "포기" 버튼이 필요 없는 이유이기도 하다.
 */

const GATE_SECONDS = 5;

export default function ExplainBox({ onSubmit }: { onSubmit: (text: string) => void }) {
  const [text, setText] = useState("");
  const [left, setLeft] = useState(GATE_SECONDS);
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    ref.current?.focus();

    // 남은 시간을 마감 시각에서 역산한다. setInterval은 탭이 백그라운드로 가면
    // 호출이 밀리므로, 횟수를 세면 5초보다 오래 걸린다.
    const deadline = Date.now() + GATE_SECONDS * 1000;
    const id = setInterval(() => {
      const remain = Math.ceil((deadline - Date.now()) / 1000);
      setLeft(remain > 0 ? remain : 0);
      if (remain <= 0) clearInterval(id);
    }, 200);
    return () => clearInterval(id);
  }, []);

  const locked = left > 0;

  function submit() {
    if (locked) return;
    onSubmit(text.trim());
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== "Enter") return;
    // 한글 입력 중의 Enter는 글자를 확정하는 키다. 이걸 제출로 가로채면
    // "민주주의"를 치다가 마지막 글자를 확정하는 순간 제출돼 버린다.
    if (e.nativeEvent.isComposing) return;
    if (e.shiftKey) return; // 줄바꿈은 Shift+Enter로 남겨둔다
    e.preventDefault();
    submit();
  }

  return (
    <div className="mt-8 flex flex-col gap-3">
      <label htmlFor="explain" className="sr-only">
        이 단어를 설명해 보세요
      </label>
      <textarea
        id="explain"
        ref={ref}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        rows={4}
        placeholder="설명해 보세요"
        className="w-full resize-none rounded-xl border border-border bg-card px-4 py-4 text-base leading-relaxed outline-none placeholder:text-muted focus:border-foreground"
      />

      <button
        type="button"
        onClick={submit}
        disabled={locked}
        aria-describedby={locked ? "gate-hint" : undefined}
        className="w-full rounded-xl bg-foreground px-5 py-4 font-medium text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-30"
      >
        {locked ? `${left}` : "정답 확인"}
      </button>

      <p id="gate-hint" className="text-center text-sm text-muted">
        {locked ? "잠깐만 더 생각해 보세요" : "Enter로 확인 · Shift+Enter 줄바꿈"}
      </p>
    </div>
  );
}
