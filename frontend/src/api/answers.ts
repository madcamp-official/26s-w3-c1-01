import { USE_MOCK, http, mockApi } from "./client";
import type { AnswerResult, ContentType } from "../types";

export async function submitAnswer(
  sessionId: string,
  cardId: string,
  userChoice: ContentType
): Promise<AnswerResult> {
  if (USE_MOCK) return mockApi.submitAnswer(sessionId, cardId, userChoice);
  return http<AnswerResult>(`/quiz-sessions/${sessionId}/answers`, {
    method: "POST",
    body: JSON.stringify({ card_id: cardId, user_choice: userChoice }),
  });
}
