const express = require("express");
const cardStore = require("../store/cardStore");
const pipeline = require("../ai/pipeline");

const router = express.Router();

const VALID_STATUSES = ["DRAFT", "VERIFIED", "PUBLISHED", "REJECTED"];
const VALID_TYPES = ["REAL", "FABRICATED"];

router.get("/", (req, res) => {
  const { category, difficulty, status } = req.query;
  res.json(cardStore.listCards({ category, difficulty, status }));
});

router.post("/", (req, res) => {
  const { category, body, type, answer_title, answer_fact, reveal_comment } = req.body ?? {};

  if (!category || !body || !VALID_TYPES.includes(type) || !answer_title || !answer_fact || !reveal_comment) {
    return res.status(400).json({ error: "missing_or_invalid_fields" });
  }

  const card = cardStore.createCard(req.body);
  res.status(201).json(card);
});

// AI 파이프라인 트리거 (BE-06): 실화 카드 1개 + AI창작 카드 1개를 생성/검증해 저장소에 적재
router.post("/generate", async (req, res) => {
  const { category, topic } = req.body ?? {};
  if (!category) {
    return res.status(400).json({ error: "category_required" });
  }

  try {
    const { real, fabricated } = await pipeline.runPipeline({ category, topic });
    const realCard = cardStore.createCard(real);
    const fabricatedCard = cardStore.createCard(fabricated);
    res.status(201).json({ cards: [realCard, fabricatedCard] });
  } catch (err) {
    res.status(502).json({ error: "ai_pipeline_failed", message: err.message });
  }
});

router.patch("/:id/status", (req, res) => {
  const { status } = req.body ?? {};
  if (!VALID_STATUSES.includes(status)) {
    return res.status(400).json({ error: "invalid_status" });
  }

  const card = cardStore.updateCardStatus(req.params.id, status);
  if (!card) return res.status(404).json({ error: "card_not_found" });

  res.json(card);
});

module.exports = router;
