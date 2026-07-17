import { useState } from "react";
import { StartScreen } from "./screens/StartScreen";
import { QuizScreen } from "./screens/QuizScreen";
import { ResultScreen } from "./screens/ResultScreen";
import type { AnswerResult } from "./types";

type Screen =
  | { name: "start" }
  | { name: "quiz"; sessionId: string }
  | { name: "result"; sessionId: string; history: AnswerResult[] };

export default function App() {
  const [screen, setScreen] = useState<Screen>({ name: "start" });

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      {screen.name === "start" && (
        <StartScreen
          onStart={(session) => setScreen({ name: "quiz", sessionId: session.session_id })}
        />
      )}
      {screen.name === "quiz" && (
        <QuizScreen
          sessionId={screen.sessionId}
          onFinished={(history) =>
            setScreen({ name: "result", sessionId: screen.sessionId, history })
          }
        />
      )}
      {screen.name === "result" && (
        <ResultScreen
          sessionId={screen.sessionId}
          history={screen.history}
          onRestart={() => setScreen({ name: "start" })}
        />
      )}
    </div>
  );
}
