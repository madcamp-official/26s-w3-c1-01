import { USE_MOCK, http, mockApi } from "./client";
import type { CreateSessionRequest, CreateSessionResponse } from "../types";

export async function createQuizSession(
  req: CreateSessionRequest
): Promise<CreateSessionResponse> {
  if (USE_MOCK) return mockApi.createSession(req);
  return http<CreateSessionResponse>("/quiz-sessions", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
