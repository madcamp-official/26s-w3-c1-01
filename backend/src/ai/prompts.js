function realCardPrompt({ category, topic }) {
  return {
    system:
      "당신은 실제 있었던 역사/사회/과학 사건을 흥미로운 짧은 글로 각색하는 어시스턴트입니다. " +
      "반드시 실제 사실만 사용하고 허구를 추가하지 마세요. 결과는 JSON으로만 응답하세요.",
    user: `카테고리: ${category}${topic ? `\n소재 힌트: ${topic}` : ""}

아래 필드를 가진 JSON을 생성하세요.
- body: 3~5문장의 지문. 사건의 배경/전개/결말을 흥미롭게 서술하되, 사건의 정식 명칭이나 정답을 바로 알 수 있는 단서(고유명사 등)는 지문에 직접 노출하지 말 것
- answer_title: 사건의 정식 명칭 (연도 포함)
- answer_fact: 정답 공개 시 보여줄 사실 요약 1~2문장
- reveal_comment: 정답 공개 후 덧붙일 짧고 흥미로운 코멘트 1문장
- source_url: 신뢰 가능한 출처 URL (모르면 null)

{"body": "...", "answer_title": "...", "answer_fact": "...", "reveal_comment": "...", "source_url": "..."}`,
  };
}

function verifyRealPrompt(card) {
  return {
    system:
      "당신은 엄격한 사실 검증가입니다. 주어진 사건 서술이 실제 역사적 사실에 부합하는지 검증하세요. " +
      "결과는 JSON으로만 응답하세요.",
    user: `사건명: ${card.answer_title}
지문: ${card.body}
근거: ${card.answer_fact}

{"verified": true 또는 false, "note": "검증 결과에 대한 한 줄 코멘트"}`,
  };
}

function fabricatedCardPrompt({ category, avoidTitle }) {
  return {
    system:
      "당신은 '있을 법하지만 실제로는 없었던' 가상 사건을 만드는 창작 어시스턴트입니다. " +
      "실존 인물명, 실제 사건명, 실제 연도·고유명사를 그대로 재사용하지 마세요. " +
      "결과는 JSON으로만 응답하세요.",
    user: `카테고리: ${category}
(참고: 아래 실화 소재와 겹치지 않는 새로운 가상 사건을 만드세요 — "${avoidTitle}")

아래 필드를 가진 JSON을 생성하세요.
- body: 실화처럼 보이는 3~5문장의 가상 사건 서술 (실존 고유명사·연도 재사용 금지)
- answer_fact: 이것이 왜 창작인지, 무엇을 모티프로 했는지 1~2문장
- reveal_comment: 정답 공개 후 덧붙일 짧고 재치있는 코멘트 1문장

{"body": "...", "answer_fact": "...", "reveal_comment": "..."}`,
  };
}

function verifyFabricatedPrompt(card) {
  return {
    system:
      "당신은 창작 콘텐츠 검수자입니다. 주어진 가상 사건 지문이 (1) 실존 인물/사건의 고유명사나 연도를 " +
      "재사용하지 않았는지, (2) 시대적으로 개연성이 있는지 검증하세요. 결과는 JSON으로만 응답하세요.",
    user: `가상 사건 지문: ${card.body}

{"plausible": true 또는 false, "reuses_real_names": true 또는 false, "note": "검증 결과에 대한 한 줄 코멘트"}`,
  };
}

function difficultyPrompt(card) {
  return {
    system:
      "당신은 퀴즈 난이도 평가자입니다. 주어진 지문만 보고 실화인지 AI 창작인지 맞히기가 " +
      "얼마나 어려운지 평가하세요. 결과는 JSON으로만 응답하세요.",
    user: `지문: ${card.body}

{"difficulty": "EASY", "MEDIUM", "HARD" 중 하나}`,
  };
}

module.exports = {
  realCardPrompt,
  verifyRealPrompt,
  fabricatedCardPrompt,
  verifyFabricatedPrompt,
  difficultyPrompt,
};
