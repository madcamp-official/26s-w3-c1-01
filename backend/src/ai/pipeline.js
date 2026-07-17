const { chatJSON } = require("./openaiClient");
const prompts = require("./prompts");
const { pickRandomTopic } = require("./topicPool");

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD"];
const MAX_REAL_ATTEMPTS = 2;

async function generateRealCard({ category, topic, feedbackNote }) {
  const data = await chatJSON(prompts.realCardPrompt({ category, topic, feedbackNote }));
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

// AI-02 + AI-06: 검증 실패 시 반려 사유를 다음 생성 시도에 피드백으로 넘겨
// 최대 MAX_REAL_ATTEMPTS번까지 다시 시도한다 (환각으로 인한 REJECTED 비율을 낮추기 위함).
async function generateVerifiedRealCard({ category, topic }) {
  let feedbackNote;
  let real;
  let verification;

  for (let attempt = 1; attempt <= MAX_REAL_ATTEMPTS; attempt += 1) {
    real = await generateRealCard({ category, topic, feedbackNote });
    verification = await verifyRealCard(real);
    if (verification.verified) break;
    feedbackNote = verification.note;
  }

  real.status = verification.verified ? "VERIFIED" : "REJECTED";
  real.verification_note = verification.note;
  return real;
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
  // AI-01: topic이 없으면 문서화가 잘 된 실제 사건 풀에서 소재를 뽑아 grounding을 강화한다.
  const resolvedTopic = topic || pickRandomTopic().topic;

  const real = await generateVerifiedRealCard({ category, topic: resolvedTopic });
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
