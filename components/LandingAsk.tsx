"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { WORD_IDS } from "@/data/words";
import { ensureSession } from "@/lib/session";
import { setPendingAnswer } from "@/lib/handoff";
import { track } from "@/lib/analytics";

/**
 * 랜딩의 안다/모른다 버튼 + landing_view 추적.
 *
 * 랜딩이 곧 첫 단어의 질문 화면이다. "시작" 버튼을 한 번 누르고 똑같은 질문을
 * 또 받는 건 화면 낭비라, 랜딩에서 바로 답하고 넘어간다.
 *
 * 세션은 여기서 만들어진다. 이때 URL의 UTM이 캡처되어 세션 내내 유지되므로,
 * 랜딩이 유입 파라미터를 잡을 수 있는 유일한 지점이다.
 *
 * 공유 링크로 /w/xxx에 바로 들어간 사용자는 랜딩을 안 거치므로 WordPlay가
 * 대신 세션을 만든다. test_start는 어느 쪽이든 세션당 정확히 한 번만 발생한다.
 */

export default function LandingAsk({ startWordId }: { startWordId: string }) {
  const router = useRouter();

  useEffect(() => {
    // 세션을 미리 만들어 두어야 landing_view에도 sessionId와 UTM이 실린다.
    ensureSession(WORD_IDS, startWordId);
    track("landing_view");
  }, [startWordId]);

  function handleAnswer(knew: boolean) {
    track("test_start", { wordId: startWordId, entry: "landing" });
    track("confidence_selected", { wordId: startWordId, knew });
    setPendingAnswer(startWordId, knew);
    router.push(`/w/${startWordId}`);
  }

  return (
    <div className="flex gap-3">
      <button
        type="button"
        onClick={() => handleAnswer(true)}
        className="flex-1 rounded-xl border border-border py-5 text-lg font-medium transition-colors hover:border-foreground"
      >
        안다
      </button>
      <button
        type="button"
        onClick={() => handleAnswer(false)}
        className="flex-1 rounded-xl border border-border py-5 text-lg font-medium transition-colors hover:border-foreground"
      >
        모른다
      </button>
    </div>
  );
}
