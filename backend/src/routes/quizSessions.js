const express = require("express");
const cardStore = require("../store/cardStore");
const sessionStore = require("../store/sessionStore");

const router = express.Router();

const DEFAULT_COUNT = 10;

router.post("/", (req, res) => {
  const { category, difficulty, count } = req.body ?? {};

  const candidates = cardStore.listCards({
    category,
    difficulty,
    status: "PUBLISHED",
  });

  if (candidates.length === 0) {
    return res.status(422).json({ error: "no_cards_available" });
  }

  const take = Math.min(count ?? DEFAULT_COUNT, candidates.length);
  const cardIds = candidates.map((card) => card.id).slice(0, take);
  const session = sessionStore.createSession(cardIds);

  res.status(201).json({ session_id: session.id, total: session.cardIds.length });
});

router.get("/:sessionId/next-card", (req, res) => {
  const session = sessionStore.getSession(req.params.sessionId);
  if (!session) return res.status(404).json({ error: "session_not_found" });

  const total = session.cardIds.length;
  const nextIndex = session.answers.length;

  if (nextIndex >= total) {
    return res.json({ card: null, progress: { current: total, total } });
  }

  const card = cardStore.getCardById(session.cardIds[nextIndex]);
  res.json({
    card: cardStore.toPublicCard(card),
    progress: { current: nextIndex + 1, total },
  });
});

router.post("/:sessionId/answers", (req, res) => {
  const session = sessionStore.getSession(req.params.sessionId);
  if (!session) return res.status(404).json({ error: "session_not_found" });

  const { card_id, user_choice } = req.body ?? {};
  const total = session.cardIds.length;
  const nextIndex = session.answers.length;
  const expectedCardId = session.cardIds[nextIndex];

  if (!expectedCardId || card_id !== expectedCardId) {
    return res.status(409).json({ error: "unexpected_card_id", expected: expectedCardId ?? null });
  }
  if (user_choice !== "REAL" && user_choice !== "FABRICATED") {
    return res.status(400).json({ error: "invalid_user_choice" });
  }

  const card = cardStore.getCardById(card_id);
  const isCorrect = user_choice === card.type;

  sessionStore.recordAnswer(session, {
    card_id,
    user_choice,
    correct_type: card.type,
    is_correct: isCorrect,
    answered_at: new Date().toISOString(),
  });

  const correctCount = session.answers.filter((a) => a.is_correct).length;

  res.json({
    card_id,
    is_correct: isCorrect,
    correct_type: card.type,
    answer_title: card.answer_title,
    answer_fact: card.answer_fact,
    reveal_comment: card.reveal_comment,
    source_url: card.source_url ?? null,
    score: { correct: correctCount, total: session.answers.length },
  });
});

router.get("/:sessionId/result", (req, res) => {
  const session = sessionStore.getSession(req.params.sessionId);
  if (!session) return res.status(404).json({ error: "session_not_found" });

  const correct = session.answers.filter((a) => a.is_correct).length;

  res.json({
    session_id: session.id,
    total: session.cardIds.length,
    correct,
    cards: session.answers.map((a) => ({
      card_id: a.card_id,
      user_choice: a.user_choice,
      correct_type: a.correct_type,
      is_correct: a.is_correct,
    })),
  });
});

module.exports = router;
