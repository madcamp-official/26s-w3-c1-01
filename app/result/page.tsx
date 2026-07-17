"use client";

import { useEffect, useRef, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { getWord } from "@/data/words";
import {
  getServerSessionSnapshot,
  getSessionSnapshot,
  resetSession,
  subscribeSession,
} from "@/lib/session";
import { buildShareText, cellEmoji, cellKind, scoreSession, verdict } from "@/lib/quiz";
import { track } from "@/lib/analytics";
import ShareButton from "@/components/ShareButton";

/**
 * 세션 요약. 이 프로젝트의 확산은 전부 이 화면에 걸려 있다.
 *
 * 점수는 정답 개수가 아니라 '착각 지수'(안다고 했는데 틀린 개수)다.
 * 주제가 착각이므로 몇 개 맞혔는지는 부차적이다.
 */

const LEGEND: { emoji: string; label: string }[] = [
  { emoji: "🟥", label: "안다고 했는데 틀림" },
  { emoji: "🟩", label: "안다고 했고 맞음" },
  { emoji: "🟦", label: "모른다고 했는데 맞음" },
  { emoji: "⬜", label: "모른다고 했고 틀림" },
];

export default function ResultPage() {
  const router = useRouter();
  const tracked = useRef(false);

  const session = useSyncExternalStore(
    subscribeSession,
    getSessionSnapshot,
    getServerSessionSnapshot,
  );

  // 세션 없이 URL로 직접 들어온 경우 보여줄 게 없다. 하이드레이션 중에는
  // 스냅샷이 항상 null이므로, 마운트 후에 저장소를 직접 읽어 판단한다.
  useEffect(() => {
    const s = getSessionSnapshot();
    if (!s || s.answers.length === 0) router.replace("/");
  }, [router]);

  useEffect(() => {
    if (!session || session.answers.length === 0 || tracked.current) return;
    tracked.current = true; // StrictMode 이중 실행으로 두 번 쏘는 걸 막는다.
    const score = scoreSession(session.order, session.answers);
    track("session_complete", {
      total: score.total,
      correct: score.correct,
      illusions: score.illusions,
      durationMs: Date.now() - session.startedAt,
    });
  }, [session]);

  if (!session || session.answers.length === 0) return null;

  const score = scoreSession(session.order, session.answers);
  const shareUrl = typeof window !== "undefined" ? window.location.origin : "";
  const byWord = new Map(session.answers.map((a) => [a.wordId, a]));
  const illusionWords = session.order
    .map((id) => byWord.get(id))
    .filter((a) => a && cellKind(a) === "illusion")
    .map((a) => getWord(a!.wordId)?.word)
    .filter(Boolean);

  function handleRetry() {
    track("retry_click", { from: "result" });
    resetSession();
    router.push("/");
  }

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col px-5 py-12">
      <p className="text-sm font-medium text-muted">내가 안다고 착각한 단어</p>

      <h1 className="mt-3 text-4xl font-bold tracking-tight">
        착각 지수 <span className="text-accent">{score.illusions}</span>
        <span className="text-muted">/{score.total}</span>
      </h1>

      <p className="mt-4 text-lg leading-relaxed text-muted">{verdict(score)}</p>

      {/* 이모지 격자 — 공유 텍스트에 들어가는 것과 동일하다 */}
      <div className="mt-8 rounded-2xl border border-border bg-card p-5">
        <div className="text-center text-3xl tracking-[0.2em]" aria-hidden>
          {session.order
            .map((id) => byWord.get(id))
            .filter(Boolean)
            .map((a) => (
              <span key={a!.wordId}>{cellEmoji(a!)}</span>
            ))}
        </div>
        <p className="sr-only">
          총 {score.total}문항 중 안다고 답하고 틀린 것이 {score.illusions}개입니다.
        </p>

        <ul className="mt-5 flex flex-col gap-1.5">
          {LEGEND.map((l) => (
            <li key={l.emoji} className="flex items-center gap-2 text-sm text-muted">
              <span aria-hidden>{l.emoji}</span>
              <span>{l.label}</span>
            </li>
          ))}
        </ul>
      </div>

      {illusionWords.length > 0 && (
        <section className="mt-8">
          <h2 className="mb-2 text-sm font-semibold text-muted">안다고 착각한 단어</h2>
          <p className="text-lg leading-relaxed">{illusionWords.join(", ")}</p>
        </section>
      )}

      <div className="mt-10 flex flex-col gap-2.5">
        <ShareButton
          text={buildShareText(score, shareUrl)}
          label="내 결과 공유하기"
          kind="result"
        />
        <button
          type="button"
          onClick={handleRetry}
          className="w-full rounded-xl border border-border px-5 py-3.5 font-medium transition-colors hover:border-muted"
        >
          처음부터 다시 하기
        </button>
      </div>

      <p className="mt-6 text-center text-xs leading-relaxed text-muted">
        공유 텍스트에는 단어도 정답도 들어가지 않습니다.
      </p>
    </main>
  );
}
