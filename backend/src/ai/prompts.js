function realCardPrompt({ category, topic, feedbackNote }) {
  return {
    system:
      "당신은 실제 있었던 역사/사회/과학 사건을 흥미로운 짧은 글로 각색하는 어시스턴트입니다. " +
      "가장 중요한 규칙: 사건, 인물명, 연도, 세부 전개를 절대 지어내지 마세요. " +
      "위키백과 등에 이미 문서화되어 있고 당신이 100% 확신하는, 매우 유명한 실제 사건만 사용하세요. " +
      "소재 힌트가 주어지면 반드시 그 사건을 소재로 쓰고, 힌트가 없으면 당신이 가장 확신하는 유명한 실화를 고르세요. " +
      "확신이 서지 않는 세부사항(정확한 날짜, URL 등)은 지어내지 말고 비워두거나 뭉뚱그려 서술하세요. " +
      "결과는 JSON으로만 응답하세요.\n\n" +
      "예시 (이 정도로 유명하고 검증 가능한 사건이어야 함): " +
      "1932년 호주 정부가 밀농사를 망치는 에뮤 떼를 막으려 기관총 부대를 투입했지만 실패하고 철수한 '에뮤 전쟁'.",
    user: `카테고리: ${category}${topic ? `\n소재 힌트: ${topic}` : ""}${
      feedbackNote
        ? `\n\n[이전 시도 반려 사유] ${feedbackNote}\n위 사유로 반려되었으니, 이번에는 다른 사건을 고르거나 훨씬 더 확실히 검증 가능한 사실만 사용하세요.`
        : ""
    }

아래 필드를 가진 JSON을 생성하세요.
- body: 3~5문장의 지문. 사건의 배경/전개/결말을 흥미롭게 서술하되, 사건의 정식 명칭이나 정답을 바로 알 수 있는 단서(고유명사 등)는 지문에 직접 노출하지 말 것
- answer_title: 사건의 정식 명칭 (연도 포함)
- answer_fact: 정답 공개 시 보여줄 사실 요약 1~2문장
- reveal_comment: 정답 공개 후 덧붙일 짧고 흥미로운 코멘트 1문장
- source_url: 정확한 URL을 100% 확신할 때만 적고, 조금이라도 불확실하면 반드시 null (URL을 지어내는 것은 절대 금지)

{"body": "...", "answer_title": "...", "answer_fact": "...", "reveal_comment": "...", "source_url": "..."}`,
  };
}

function verifyRealPrompt(card) {
  return {
    system:
      "당신은 엄격한 사실 검증가입니다. 주어진 사건명·지문·근거가 실제 역사적 사실에 정확히 부합하는지 검증하세요. " +
      "조금이라도 지어낸 것으로 의심되거나 확신할 수 없으면 반드시 verified: false로 판정하세요 " +
      "(불확실할 때는 통과시키지 말고 반려하는 쪽으로 판단). " +
      "결과는 JSON으로만 응답하세요.",
    user: `사건명: ${card.answer_title}
지문: ${card.body}
근거: ${card.answer_fact}
출처 URL: ${card.source_url ?? "(없음)"}

{"verified": true 또는 false, "note": "반려라면 구체적으로 어떤 부분이 사실과 다르거나 확인 불가능한지 한 줄로"}`,
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
