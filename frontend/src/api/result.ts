import { USE_MOCK, http, mockApi } from "./client";
import type { SessionResult } from "../types";

export async function getResult(sessionId: string): Promise<SessionResult> {
  if (USE_MOCK) return mockApi.getResult(sessionId);
  return http<SessionResult>(`/quiz-sessions/${sessionId}/result`);
}
