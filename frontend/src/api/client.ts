import mockCards from "../mock/cards.json";
import type {
  AnswerResult,
  ContentType,
  CreateSessionRequest,
  CreateSessionResponse,
  Difficulty,
  NextCardResponse,
  QuizCard,
  ResultCard,
  SessionResult,
} from "../types";

export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== "false";
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
/** Mock 모드에서 로딩 UX를 확인하기 위한 인위적 지연. */
const MOCK_DELAY_MS = 300;

export async function http<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    throw new Error(`API error ${res.status}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), MOCK_DELAY_MS));
}

interface MockCard {
  id: string;
  category: string;
  body: string;
  difficulty: Difficulty;
  type: ContentType;
  answer_title: string;
  answer_fact: string;
  reveal_comment: string;
  source_url?: string;
}

interface MockSession {
  cardIds: string[];
  index: number;
  results: ResultCard[];
}

const mockSessions = new Map<string, MockSession>();
const allMockCards = mockCards as MockCard[];

function shuffle<T>(items: T[]): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

export const mockApi = {
  async createSession(req: CreateSessionRequest): Promise<CreateSessionResponse> {
    const filtered = allMockCards.filter(
      (c) =>
        (!req.category || c.category === req.category) &&
        (!req.difficulty || c.difficulty === req.difficulty)
    );
    const pool = filtered.length > 0 ? filtered : allMockCards;
    const selected = shuffle(pool).slice(0, req.count);
    const session_id = `sess_mock_${Math.random().toString(36).slice(2, 10)}`;
    mockSessions.set(session_id, { cardIds: selected.map((c) => c.id), index: 0, results: [] });
    return delay({ session_id, total: selected.length });
  },

  async getNextCard(sessionId: string): Promise<NextCardResponse> {
    const session = mockSessions.get(sessionId);
    if (!session) throw new Error(`Unknown mock session: ${sessionId}`);
    const total = session.cardIds.length;
    if (session.index >= total) {
      return delay({ card: null, progress: { current: total, total } });
    }
    const cardId = session.cardIds[session.index];
    const full = allMockCards.find((c) => c.id === cardId)!;
    const card: QuizCard = {
      id: full.id,
      category: full.category,
      body: full.body,
      difficulty: full.difficulty,
    };
    return delay({ card, progress: { current: session.index + 1, total } });
  },

  async submitAnswer(
    sessionId: string,
    cardId: string,
    userChoice: ContentType
  ): Promise<AnswerResult> {
    const session = mockSessions.get(sessionId);
    if (!session) throw new Error(`Unknown mock session: ${sessionId}`);
    const full = allMockCards.find((c) => c.id === cardId)!;
    const is_correct = full.type === userChoice;
    session.results.push({
      card_id: cardId,
      user_choice: userChoice,
      correct_type: full.type,
      is_correct,
    });
    session.index += 1;
    const correct = session.results.filter((r) => r.is_correct).length;
    return delay({
      card_id: cardId,
      is_correct,
      correct_type: full.type,
      answer_title: full.answer_title,
      answer_fact: full.answer_fact,
      reveal_comment: full.reveal_comment,
      source_url: full.source_url,
      score: { correct, total: session.results.length },
    });
  },

  async getResult(sessionId: string): Promise<SessionResult> {
    const session = mockSessions.get(sessionId);
    if (!session) throw new Error(`Unknown mock session: ${sessionId}`);
    const correct = session.results.filter((r) => r.is_correct).length;
    return delay({
      session_id: sessionId,
      total: session.cardIds.length,
      correct,
      cards: session.results,
    });
  },
};
