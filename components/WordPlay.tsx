"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

import { WORD_IDS, type Word } from "@/data/words";
import {
  ensureSession,
  getServerSessionSnapshot,
  getSessionSnapshot,
  recordAnswer,
  subscribeSession,
} from "@/lib/session";
import { getWordStats, type WordStats } from "@/lib/stats";
import { track } from "@/lib/analytics";

import ExplainBox from "./ExplainBox";
import RevealPanel from "./RevealPanel";

/**
 * 단어 하나의 흐름.
 *
 *   안다  → 설명 쓰기(5초 게이트) → 정답 공개 + 자기판정 → 다음
 *   모른다 → 정답 공개만 → 다음
 *
 * "모른다"에 설명을 쓰게 하지 않는 이유: 모른다고 인정한 사람은 이 앱의 관심사가
 * 아니다. 착각은 "안다"고 한 사람에게만 일어난다. 모른다는 정직한 퇴로이고,
 * 그 대가로 결과 격자가 ⬜로 심심해진다.
 *
 * 설계 원칙: 사용자가 읽어야 하는 글자를 최소화한다. 라우트 이동 없이 클라이언트
 * 상태로 단계를 넘긴다 — 제출 후 로딩이 걸리면 착각이 드러나는 순간의 긴장이 풀린다.
 */

type Step = "ask" | "explain" | "reveal";

export default function WordPlay({ word }: { word: Word }) {
  const router = useRouter();

  const [step, setStep] = useState<Step>("ask");
  const [knew, setKnew] = useState<boolean | null>(null);
  const [text, setText] = useState("");
  const [stats, setStats] = useState<WordStats | null>(null);

  const headingRef = useRef<HTMLHeadingElement>(null);
  const isFirstRender = useRef(true);

  const session = useSyncExternalStore(
    subscribeSession,
    getSessionSnapshot,
    getServerSessionSnapshot,
  );
  // 하이드레이션 중에는 세션을 읽을 수 없다. 그동안은 기본 순서로 그린다.
  const order = session?.order ?? WORD_IDS;

  // 세션 보장. 공유 링크로 이 단어에 바로 들어왔다면 이 단어가 세션의 1번이 된다.
  useEffect(() => {
    const isNewSession = getSessionSnapshot() === null;
    ensureSession(WORD_IDS, word.id);
    if (isNewSession) track("test_start", { wordId: word.id, entry: "direct" });
  }, [word.id]);

  useEffect(() => {
    let alive = true;
    getWordStats(word.id).then((s) => {
      if (alive) setStats(s);
    });
    return () => {
      alive = false;
    };
  }, [word.id]);

  // 단계가 바뀌면 제목으로 포커스를 옮긴다. 스크린리더 사용자가 화면 전환을
  // 알 수 있어야 하고, 키보드 탭 순서도 위로 돌아와야 한다.
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    // 설명 단계에서는 ExplainBox가 textarea로 포커스를 가져간다.
    if (step === "explain") return;
    headingRef.current?.focus();
  }, [step]);

  const index = order.indexOf(word.id);
  const isLast = index === order.length - 1;

  function goNext() {
    const nextId = order[index + 1];
    track("next_word_click", { wordId: word.id, ...(nextId ? { nextWordId: nextId } : {}) });
    router.push(nextId ? `/w/${nextId}` : "/result");
  }

  function handleKnew(v: boolean) {
    setKnew(v);
    track("confidence_selected", { wordId: word.id, knew: v });

    if (v) {
      setStep("explain");
      return;
    }

    // 모른다 → 쓸 것도 판정할 것도 없다. 정답만 보여주고 넘긴다.
    recordAnswer({ wordId: word.id, knew: false, text: null, correct: false, at: Date.now() });
    setStep("reveal");
    track("result_view", { wordId: word.id, knew: false });
  }

  function handleExplained(value: string) {
    setText(value);
    // correct는 아직 모른다. 사용자가 판정해야 정해진다.
    track("answer_submitted", {
      wordId: word.id,
      knew: true,
      answerLength: value.length,
      gaveUp: value.length === 0,
    });
    setStep("reveal");
    track("result_view", { wordId: word.id, knew: true });
  }

  function handleJudge(correct: boolean) {
    recordAnswer({ wordId: word.id, knew: true, text, correct, at: Date.now() });
    track("self_judged", { wordId: word.id, correct, gaveUp: text.length === 0 });
    goNext();
  }

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col px-5 py-8">
      {/* 진행 표시 */}
      <div className="mb-12 flex items-center gap-3">
        <Link href="/" aria-label="처음으로" className="text-sm text-muted hover:text-foreground">
          ←
        </Link>
        <div aria-hidden className="flex flex-1 gap-1.5">
          {order.map((id, i) => (
            <span
              key={id}
              className={`h-1 flex-1 rounded-full ${
                i < index ? "bg-foreground/40" : i === index ? "bg-foreground" : "bg-border"
              }`}
            />
          ))}
        </div>
        <span className="text-sm tabular-nums text-muted">
          {index + 1}/{order.length}
        </span>
      </div>

      <h1
        ref={headingRef}
        tabIndex={-1}
        className="text-center text-5xl font-bold tracking-tight outline-none sm:text-6xl"
      >
        {word.word}
      </h1>

      {step === "ask" && (
        <div className="mt-16 flex flex-col gap-3">
          <p className="mb-1 text-center text-muted">설명할 수 있나요?</p>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => handleKnew(true)}
              className="flex-1 rounded-xl border border-border py-5 text-lg font-medium transition-colors hover:border-foreground"
            >
              안다
            </button>
            <button
              type="button"
              onClick={() => handleKnew(false)}
              className="flex-1 rounded-xl border border-border py-5 text-lg font-medium transition-colors hover:border-foreground"
            >
              모른다
            </button>
          </div>
        </div>
      )}

      {step === "explain" && <ExplainBox onSubmit={handleExplained} />}

      {step === "reveal" && knew === true && (
        <RevealPanel word={word} text={text} stats={stats} onJudge={handleJudge} />
      )}

      {step === "reveal" && knew === false && (
        <div className="mt-8 flex flex-col gap-6">
          <section>
            <h2 className="mb-2 text-sm font-semibold text-muted">실제 뜻</h2>
            <p className="rounded-xl border border-border bg-card px-4 py-3 leading-relaxed">
              {word.definition}
            </p>
          </section>
          <button
            type="button"
            onClick={goNext}
            className="w-full rounded-xl bg-foreground px-5 py-4 font-medium text-background transition-opacity hover:opacity-90"
          >
            {isLast ? "결과 보기" : "다음"}
          </button>
        </div>
      )}
    </main>
  );
}
