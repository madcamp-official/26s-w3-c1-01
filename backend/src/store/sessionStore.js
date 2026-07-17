const crypto = require("crypto");

const sessions = new Map();

function shuffle(array) {
  const result = [...array];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

function createSession(cardIds) {
  const sessionId = `sess_${crypto.randomUUID()}`;
  const session = {
    id: sessionId,
    cardIds: shuffle(cardIds),
    answers: [],
  };
  sessions.set(sessionId, session);
  return session;
}

function getSession(id) {
  return sessions.get(id) ?? null;
}

function recordAnswer(session, answer) {
  session.answers.push(answer);
}

module.exports = { createSession, getSession, recordAnswer };
