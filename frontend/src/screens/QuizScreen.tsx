import { useEffect } from "react";
import { useQuizFlow } from "../hooks/useQuizFlow";
import { CardBody } from "../components/CardBody";
import { ChoiceButtons } from "../components/ChoiceButtons";
import { RevealPanel } from "../components/RevealPanel";
import { ScoreProgress } from "../components/ScoreProgress";
import type { AnswerResult } from "../types";

interface Props {
  sessionId: string;
  onFinished: (history: AnswerResult[]) => void;
}

/** FE-01,02,03,04,05,06,09 조율. */
export function QuizScreen({ sessionId, onFinished }: Props) {
  const flow = useQuizFlow(sessionId);

  useEffect(() => {
    if (flow.isFinished) onFinished(flow.history);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flow.isFinished]);

  if (flow.isFinished) return null;

  return (
    <div className="mx-auto max-w-md p-6">
      <ScoreProgress progress={flow.progress} score={flow.score} />

      {flow.error && <p className="mb-3 text-sm text-red-500">{flow.error}</p>}

      {flow.isLoadingCard && !flow.card && (
        <p className="text-center text-gray-400">지문을 불러오는 중...</p>
      )}

      {flow.card && !flow.reveal && (
        <>
          <CardBody card={flow.card} />
          <ChoiceButtons disabled={flow.isSubmitting} onChoose={flow.submitChoice} />
        </>
      )}

      {flow.card && flow.reveal && (
        <>
          <CardBody card={flow.card} />
          <RevealPanel reveal={flow.reveal} onNext={flow.goToNextCard} />
        </>
      )}
    </div>
  );
}
