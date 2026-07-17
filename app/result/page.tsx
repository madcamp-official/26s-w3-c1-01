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
import { buildShareText, cellEmoji, scoreSession, verdict } from "@/lib/quiz";
import { track } from "@/lib/analytics";
import ShareButton from "@/components/ShareButton";
import SuggestBox from "@/components/SuggestBox";

/**
 * 세션 요약. 이 프로젝트의 확산은 전부 이 화면에 걸려 있다.
 *
 * 점수는 정답 개수가 아니라 '착각 지수'(안다고 했는데 설명 못 한 개수)다.
 *
 * 첫 화면에 착각 지수 + 격자 + 공유를 다 넣어 스크롤 없이 보이게 하고, 내가 쓴
 * 답변과 단어 추천은 그 아래에 둔다. 공유가 목적이므로 공유 버튼이 스크롤 밖으로
 * 밀려나면 안 된다.
 */

/** 범례는 한 줄로 붙인다. 여러 줄로 늘어놓으면 그 자체가 읽을거리가 된다. */
const LEGEND = "🟩 설명해냄 · 🟨 애매 · 🟥 착각 · ⬜ 모른다고 함";

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
      claimed: score.claimed,
      illusions: score.illusions,
      durationMs: Date.now() - session.startedAt,
    });
  }, [session]);

  if (!session || session.answers.length === 0) return null;

  const score = scoreSession(session.order, session.answers);
  const shareUrl = typeof window !== "undefined" ? window.location.origin : "";
  const byWord = new Map(session.answers.map((a) => [a.wordId, a]));
  const ordered = session.order.map((id) => byWord.get(id)).filter((a) => !!a);

  function handleRetry() {
    track("retry_click", { from: "result" });
    resetSession();
    router.push("/");
  }

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col px-5 py-10">
      <p className="text-center text-sm text-muted">착각 지수</p>

      <p className="mt-1 text-center text-6xl font-bold tracking-tight">
        <span className={score.illusions > 0 ? "text-accent" : ""}>{score.illusions}</span>
        <span className="text-muted">/{score.total}</span>
      </p>

      <div className="mt-6 text-center text-4xl tracking-[0.15em]" aria-hidden>
        {ordered.map((a) => (
          <span key={a.wordId}>{cellEmoji(a)}</span>
        ))}
      </div>
      <p className="sr-only">
        {score.total}문항 중 안다고 답하고 틀린 것이 {score.illusions}개입니다.
      </p>

      <p className="mt-4 text-center text-xs text-muted">{LEGEND}</p>

      <p className="mt-5 text-center text-lg">{verdict(score)}</p>

      <div className="mt-8 flex flex-col gap-2.5">
        <ShareButton text={buildShareText(score, shareUrl)} label="결과 공유" kind="result" />
        <button
          type="button"
          onClick={handleRetry}
          className="w-full rounded-xl border border-border px-5 py-4 font-medium transition-colors hover:border-foreground"
        >
          다시 하기
        </button>
      </div>

      {/* 여기부터는 스크롤해서 보는 영역 */}
      <section className="mt-12">
        <h2 className="mb-3 text-sm font-semibold text-muted">내가 쓴 설명</h2>
        <ul className="flex flex-col gap-3">
          {ordered.map((a) => (
            <li key={a.wordId} className="rounded-xl border border-border px-4 py-3">
              <p className="flex items-center gap-2 font-medium">
                <span aria-hidden>{cellEmoji(a)}</span>
                {getWord(a.wordId)?.word}
              </p>
              <p
                className={`mt-1 whitespace-pre-wrap text-sm leading-relaxed ${
                  a.text ? "" : "text-muted"
                }`}
              >
                {a.text ? a.text : a.knew ? "한 글자도 쓰지 못했습니다" : "모른다고 답했습니다"}
              </p>
            </li>
          ))}
        </ul>
      </section>

      <div className="mt-8">
        <SuggestBox />
      </div>

      <p className="mt-8 text-center text-xs text-muted">
        공유 텍스트에는 단어도 정답도 들어가지 않습니다.
      </p>
    </main>
  );
}
