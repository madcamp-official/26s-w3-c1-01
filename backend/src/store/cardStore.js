const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const SEED_PATH = path.join(__dirname, "..", "data", "cards.seed.json");

const seed = JSON.parse(fs.readFileSync(SEED_PATH, "utf-8"));
let cards = seed.map((card) => ({ ...card }));

function toPublicCard(card) {
  return {
    id: card.id,
    category: card.category,
    body: card.body,
    difficulty: card.difficulty ?? null,
  };
}

function listCards({ category, difficulty, status } = {}) {
  return cards.filter(
    (card) =>
      (!category || card.category === category) &&
      (!difficulty || card.difficulty === difficulty) &&
      (!status || card.status === status)
  );
}

function getCardById(id) {
  return cards.find((card) => card.id === id) ?? null;
}

function createCard(data) {
  const card = {
    id: `card_${crypto.randomUUID()}`,
    category: data.category,
    body: data.body,
    type: data.type,
    answer_title: data.answer_title,
    answer_fact: data.answer_fact,
    reveal_comment: data.reveal_comment,
    source_url: data.source_url ?? null,
    difficulty: data.difficulty ?? null,
    status: data.status ?? "DRAFT",
    verification_note: data.verification_note ?? null,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
  };
  cards.push(card);
  return card;
}

function updateCardStatus(id, status) {
  const card = getCardById(id);
  if (!card) return null;
  card.status = status;
  card.updated_at = new Date().toISOString();
  return card;
}

module.exports = {
  toPublicCard,
  listCards,
  getCardById,
  createCard,
  updateCardStatus,
};
