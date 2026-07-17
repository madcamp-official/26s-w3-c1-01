"use client";

import { useState } from "react";
import { track } from "@/lib/analytics";

/**
 * 공유 버튼.
 *
 * 모바일에선 네이티브 공유 시트(Web Share API), 데스크톱에선 클립보드 복사로
 * 자동 분기한다. Web Share API는 https + 사용자 제스처가 있어야 뜨고, 사용자가
 * 시트를 그냥 닫으면 AbortError가 나는데 그건 에러가 아니라 정상 취소다.
 */

export default function ShareButton({
  text,
  label,
  kind,
  wordId,
  variant = "primary",
}: {
  text: string;
  label: string;
  /** 무엇을 공유했는지 — share_click 이벤트에 실린다 */
  kind: "result" | "word";
  wordId?: string;
  variant?: "primary" | "secondary";
}) {
  const [copied, setCopied] = useState(false);

  async function handleShare() {
    track("share_click", { kind, ...(wordId ? { wordId } : {}) });

    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ text });
        return;
      } catch (err) {
        // 사용자가 공유 시트를 닫은 것뿐이면 아무것도 하지 않는다.
        if (err instanceof Error && err.name === "AbortError") return;
        // 그 외 실패는 클립보드로 폴백한다.
      }
    }

    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // 클립보드까지 막힌 환경(비-https 등)에서는 조용히 포기한다.
    }
  }

  const base =
    "w-full rounded-xl px-5 py-3.5 font-medium transition-colors disabled:opacity-50";
  const tone =
    variant === "primary"
      ? "bg-foreground text-background hover:opacity-90"
      : "border border-border hover:border-muted";

  return (
    <button type="button" onClick={handleShare} className={`${base} ${tone}`}>
      {copied ? "복사했습니다" : label}
    </button>
  );
}
