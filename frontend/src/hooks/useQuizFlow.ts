import { useCallback, useEffect, useState } from "react";
import { getNextCard } from "../api/nextCard";
import { submitAnswer } from "../api/answers";
import type { AnswerResult, ContentType, Progress, QuizCard, Score } from "../types";

export interface UseQuizFlowResult {
  card: QuizCard | null;
  progress: Progress | null;
  reveal: AnswerResult | null;
  score: Score;
  history: AnswerResult[];
  isLoadingCard: boolean;
  isSubmitting: boolean;
  isFinished: boolean;
  error: string | null;
  submitChoice: (choice: ContentType) => Promise<void>;
  goToNextCard: () => Promise<void>;
}

/**
 * 퀴즈 진행 상태 머신. "지문 로드 상태"(card)와 "정답 공개 상태"(reveal)를
 * 별도 state로 분리해, next-card 응답에 없는 정답 필드가 우연히도 렌더링될 수 없게 한다(FE-09).
 */
export function useQuizFlow(sessionId: string): UseQuizFlowResult {
  const [card, setCard] = useState<QuizCard | null>(null);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [reveal, setReveal] = useState<AnswerResult | null>(null);
  const [score, setScore] = useState<Score>({ correct: 0, total: 0 });
  const [history, setHistory] = useState<AnswerResult[]>([]);
  const [isLoadingCard, setIsLoadingCard] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isFinished, setIsFinished] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadNextCard = useCallback(async () => {
    setIsLoadingCard(true);
    setError(null);
    try {
      const res = await getNextCard(sessionId);
      setProgress(res.progress);
      setCard(res.card);
      if (res.card === null) setIsFinished(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "지문을 불러오지 못했습니다.");
    } finally {
      setIsLoadingCard(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadNextCard();
  }, [loadNextCard]);

  const submitChoice = useCallback(
    async (choice: ContentType) => {
      if (!card || isSubmitting) return;
      setIsSubmitting(true);
      setError(null);
      try {
        const res = await submitAnswer(sessionId, card.id, choice);
        setReveal(res);
        setScore(res.score);
        setHistory((prev) => [...prev, res]);
      } catch (e) {
        setError(e instanceof Error ? e.message : "제출에 실패했습니다.");
      } finally {
        setIsSubmitting(false);
      }
    },
    [sessionId, card, isSubmitting]
  );

  const goToNextCard = useCallback(async () => {
    setReveal(null);
    setCard(null);
    await loadNextCard();
  }, [loadNextCard]);

  return {
    card,
    progress,
    reveal,
    score,
    history,
    isLoadingCard,
    isSubmitting,
    isFinished,
    error,
    submitChoice,
    goToNextCard,
  };
}
