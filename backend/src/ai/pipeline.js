const { chatJSON } = require("./openaiClient");
const prompts = require("./prompts");

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD"];

async function generateRealCard({ category, topic }) {
  const data = await chatJSON(prompts.realCardPrompt({ category, topic }));
  return {
    category,
    body: data.body,
    type: "REAL",
    answer_title: data.answer_title,
    answer_fact: data.answer_fact,
    reveal_comment: data.reveal_comment,
    source_url: data.source_url ?? null,
  };
}

async function verifyRealCard(card) {
  const data = await chatJSON(prompts.verifyRealPrompt(card));
  return { verified: data.verified === true, note: data.note ?? "" };
}

async function generateFabricatedCard({ category, avoidTitle }) {
  const data = await chatJSON(prompts.fabricatedCardPrompt({ category, avoidTitle }));
  return {
    category,
    body: data.body,
    type: "FABRICATED",
    answer_title: "AI가 만든 가상 사건 (실존 사건 아님)",
    answer_fact: data.answer_fact,
    reveal_comment: data.reveal_comment,
    source_url: null,
  };
}

async function verifyFabricatedCard(card) {
  const data = await chatJSON(prompts.verifyFabricatedPrompt(card));
  return {
    plausible: data.plausible === true,
    reusesRealNames: data.reuses_real_names === true,
    note: data.note ?? "",
  };
}

async function tagDifficulty(card) {
  const data = await chatJSON(prompts.difficultyPrompt(card));
  return DIFFICULTIES.includes(data.difficulty) ? data.difficulty : "MEDIUM";
}

// AI-01~AI-09: 소재 수집(카테고리/토픽 입력) -> 실화 생성/검증 -> AI창작 생성/검증 -> 난이도 태깅
async function runPipeline({ category, topic }) {
  const real = await generateRealCard({ category, topic });
  const realVerification = await verifyRealCard(real);
  real.status = realVerification.verified ? "VERIFIED" : "REJECTED";
  real.verification_note = realVerification.note;
  real.difficulty = await tagDifficulty(real);

  const fabricated = await generateFabricatedCard({ category, avoidTitle: real.answer_title });
  const fabVerification = await verifyFabricatedCard(fabricated);
  fabricated.status =
    fabVerification.plausible && !fabVerification.reusesRealNames ? "VERIFIED" : "REJECTED";
  fabricated.verification_note = fabVerification.note;
  fabricated.difficulty = await tagDifficulty(fabricated);

  return { real, fabricated };
}

module.exports = { runPipeline };
