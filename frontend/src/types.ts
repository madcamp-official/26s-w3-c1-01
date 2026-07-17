export type ContentType = "REAL" | "FABRICATED";
export type Difficulty = "EASY" | "MEDIUM" | "HARD";

/** GET /quiz-sessions/{id}/next-card 의 card — 정답 관련 필드는 타입에 존재하지 않음(FE-09). */
export interface QuizCard {
  id: string;
  category: string;
  body: string;
  difficulty?: Difficulty;
}

export interface Progress {
  current: number;
  total: number;
}

export interface NextCardResponse {
  card: QuizCard | null;
  progress: Progress;
}

export interface Score {
  correct: number;
  total: number;
}

/** POST /quiz-sessions/{id}/answers 응답 — 정답 필드는 오직 이 타입에만 존재. */
export interface AnswerResult {
  card_id: string;
  is_correct: boolean;
  correct_type: ContentType;
  answer_title: string;
  answer_fact: string;
  reveal_comment: string;
  source_url?: string;
  score: Score;
}

export interface CreateSessionRequest {
  category?: string;
  difficulty?: Difficulty;
  count: number;
}

export interface CreateSessionResponse {
  session_id: string;
  total: number;
}

export interface ResultCard {
  card_id: string;
  user_choice: ContentType;
  correct_type: ContentType;
  is_correct: boolean;
}

export interface SessionResult {
  session_id: string;
  total: number;
  correct: number;
  cards: ResultCard[];
}
