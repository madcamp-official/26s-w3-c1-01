"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { WORD_IDS } from "@/data/words";
import { ensureSession } from "@/lib/session";
import { track } from "@/lib/analytics";

/**
 * 랜딩의 시작 버튼 + landing_view 추적.
 *
 * 세션은 여기서 만들어진다. 이때 URL의 UTM이 캡처되어 세션 내내 유지되므로,
 * 랜딩이 유입 파라미터를 잡을 수 있는 유일한 지점이다.
 *
 * 공유 링크로 /w/xxx에 바로 들어간 사용자는 랜딩을 안 거치므로 WordPlay가
 * 대신 세션을 만든다. test_start는 어느 쪽이든 세션당 정확히 한 번만 발생한다.
 */

export default function StartButton({ startWordId }: { startWordId: string }) {
  const router = useRouter();

  useEffect(() => {
    // 세션을 미리 만들어 두어야 landing_view에도 sessionId와 UTM이 실린다.
    ensureSession(WORD_IDS, startWordId);
    track("landing_view");
  }, [startWordId]);

  function handleStart() {
    // 세션은 이미 위 effect에서 만들어졌다. 여기서는 의도만 기록한다.
    track("test_start", { wordId: startWordId, entry: "landing" });
    router.push(`/w/${startWordId}`);
  }

  return (
    <button
      type="button"
      onClick={handleStart}
      className="w-full rounded-xl bg-foreground px-5 py-4 text-base font-medium text-background transition-opacity hover:opacity-90"
    >
      30초 만에 확인하기
    </button>
  );
}
