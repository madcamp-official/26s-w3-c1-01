"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

import { WORD_IDS, isCorrect, type ConfidenceLevel, type Word } from "@/data/words";
import {
  ensureSession,
  getServerSessionSnapshot,
  getSessionSnapshot,
  recordAnswer,
  resetSession,
  subscribeSession,
} from "@/lib/session";
import { getWordStats, toPercentages, type WordStats } from "@/lib/stats";
import { buildWordShareText } from "@/lib/quiz";
import { track } from "@/lib/analytics";

import ConfidencePicker from "./ConfidencePicker";
import ChoiceList from "./ChoiceList";
import ResultPanel from "./ResultPanel";
import ShareButton from "./ShareButton";

/**
 * 단어 하나의 3단계 흐름: 자기평가 → 보기 선택 → 결과.
 *
 * 라우트 이동 없이 클라이언트 상태로만 단계를 넘긴다. 제출 후 로딩이 걸리면
 * 착각이 드러나는 순간의 긴장이 풀려버리므로, 이 구간에 네트워크를 두지 않았다.
 */

type Step = "confidence" | "choices" | "result";

export default function WordPlay({ word }: { word: Word }) {
  const router = useRouter();

  const [step, setStep] = useState<Step>("confidence");
  const [confidence, setConfidence] = useState<ConfidenceLevel | null>(null);
  const [choiceId, setChoiceId] = useState<string | null>(null);
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

  // 단계가 바뀌면 새 제목으로 포커스를 옮긴다. 스크린리더 사용자가 화면이
  // 바뀐 걸 알 수 있어야 하고, 키보드 사용자의 탭 순서도 위로 돌아와야 한다.
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    headingRef.current?.focus();
  }, [step]);

  const index = order.indexOf(word.id);
  const position = index >= 0 ? index + 1 : 1;
  const total = order.length;
  const isLast = index === total - 1;

  function handleConfidence(v: ConfidenceLevel) {
    setConfidence(v);
    track("confidence_selected", { wordId: word.id, confidence: v });
    setStep("choices");
  }

  function handleSubmit() {
    if (!choiceId || !confidence) return;
    const correct = isCorrect(word, choiceId);
    recordAnswer({ wordId: word.id, confidence, choiceId, correct, at: Date.now() });
    track("answer_submitted", { wordId: word.id, choiceId, confidence, correct });
    setStep("result");
    track("result_view", { wordId: word.id, correct });
  }

  function handleNext() {
    const nextId = order[index + 1];
    track("next_word_click", { wordId: word.id, ...(nextId ? { nextWordId: nextId } : {}) });
    router.push(nextId ? `/w/${nextId}` : "/result");
  }

  function handleRetry() {
    track("retry_click", { wordId: word.id });
    resetSession();
    router.push("/");
  }

  const shareUrl = typeof window !== "undefined" ? `${window.location.origin}/w/${word.id}` : "";

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col px-5 py-8 sm:py-12">
      {/* 진행 표시 */}
      <div className="mb-8 flex items-center gap-3">
        <Link href="/" className="text-sm text-muted hover:text-foreground">
          ← 처음
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
          {position} / {total}
        </span>
      </div>

      <h1
        ref={headingRef}
        tabIndex={-1}
        className="mb-2 text-3xl font-bold tracking-tight outline-none sm:text-4xl"
      >
        {word.word}
      </h1>

      <p className="mb-8 text-sm text-muted">
        {step === "confidence"
          ? "먼저 스스로 평가해 보세요"
          : step === "choices"
            ? word.question
            : "결과"}
      </p>

      {step === "confidence" && (
        <ConfidencePicker word={word.word} value={confidence} onChange={handleConfidence} />
      )}

      {step === "choices" && (
        <>
          <ChoiceList choices={word.choices} value={choiceId} onChange={setChoiceId} />
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!choiceId}
            className="mt-6 w-full rounded-xl bg-foreground px-5 py-3.5 font-medium text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
          >
            정답 확인
          </button>
        </>
      )}

      {step === "result" && confidence && choiceId && (
        <>
          <div className="mb-8">
            <ChoiceList
              choices={word.choices}
              value={choiceId}
              revealed
              answerId={word.answerId}
              percentages={stats ? toPercentages(stats) : undefined}
            />
          </div>

          <ResultPanel
            word={word}
            confidence={confidence}
            correct={isCorrect(word, choiceId)}
            pickedChoiceId={choiceId}
            stats={stats}
          />

          <div className="mt-10 flex flex-col gap-2.5">
            <button
              type="button"
              onClick={handleNext}
              className="w-full rounded-xl bg-foreground px-5 py-3.5 font-medium text-background transition-opacity hover:opacity-90"
            >
              {isLast ? "결과 보기" : "다음 단어"}
            </button>
            <ShareButton
              text={buildWordShareText(word.word, shareUrl)}
              label="이 단어 공유하기"
              kind="word"
              wordId={word.id}
              variant="secondary"
            />
            <button
              type="button"
              onClick={handleRetry}
              className="w-full rounded-xl px-5 py-3 text-sm text-muted transition-colors hover:text-foreground"
            >
              처음부터 다시 하기
            </button>
          </div>
        </>
      )}
    </main>
  );
}
