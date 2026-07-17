import { USE_MOCK, http, mockApi } from "./client";
import type { NextCardResponse } from "../types";

export async function getNextCard(sessionId: string): Promise<NextCardResponse> {
  if (USE_MOCK) return mockApi.getNextCard(sessionId);
  return http<NextCardResponse>(`/quiz-sessions/${sessionId}/next-card`);
}
