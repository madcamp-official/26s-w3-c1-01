/**
 * 단어 데이터 — 이 앱의 단일 진실 공급원(single source of truth).
 *
 * `keywords`는 사용자가 쓴 설명에 들어 있으면 초록색 체크가 켜지는 단어다.
 * **이건 채점이 아니라 힌트다.** 최종 판정은 여전히 사용자가 한다.
 * (키워드 매칭으로 맞다/틀리다를 단정하면, "국민이 주인인 나라"라고 잘 쓴 사람이
 *  '주권'이 없다고 틀렸다는 소리를 듣게 되고, 그 순간 사용자는 자기가 틀렸다고
 *  느끼는 게 아니라 앱이 고장났다고 느낀다. docs/PROJECT_NOTES.md 3.2 참고)
 *
 * 그래도 오탐은 최대한 줄여야 하므로 `match`에 표현 변형을 넉넉히 넣는다.
 * 반대로 너무 넓은 말("법", "것")은 넣지 말 것 — 아무 답에나 체크가 켜진다.
 *
 * 단어를 고를 때:
 *  1. **누구나 안다고 답할 만큼 익숙한데, 막상 설명하면 막히는 단어**여야 한다.
 *     아무도 모르는 단어는 전부 "모른다"가 나와서 착각이 발생하지 않는다.
 *  2. `definition`의 사실 정확성은 반드시 사람이 검토한다.
 *     틀린 정답을 공개하는 것이 이 제품의 가장 빠른 실패 경로다.
 *  3. `id`는 URL에 그대로 노출되므로 바꾸면 기존 공유 링크가 깨진다.
 */

export type Keyword = {
  /** 칩에 표시할 짧은 말 */
  label: string;
  /** 이 중 하나라도 답에 들어 있으면 체크된다 */
  match: string[];
};

export type Word = {
  /** URL slug. 변경 시 기존 공유 링크가 깨진다. */
  id: string;
  word: string;
  /** 정답. 제출 후 사용자의 설명과 나란히 보여준다. 한 문장으로 짧게. */
  definition: string;
  /** 3개 유지. 늘리면 읽을거리가 되고 자기판정이 무뎌진다. */
  keywords: Keyword[];
};

export const WORDS: Word[] = [
  {
    id: "democracy",
    word: "민주주의",
    definition:
      "국민이 주권을 가지고 스스로 권력을 행사하는 정치 체제. 다수결과 선거는 그 수단일 뿐이고, 법치와 기본권 보장이 함께 작동해야 성립한다.",
    keywords: [
      { label: "주권", match: ["주권", "주인", "권력은 국민"] },
      { label: "법치", match: ["법치", "법의 지배", "헌법"] },
      { label: "기본권", match: ["기본권", "인권", "소수자", "자유 보장"] },
    ],
  },
  {
    id: "inertia",
    word: "관성",
    definition:
      "힘이 작용하지 않으면 물체가 지금의 운동 상태를 그대로 유지하는 성질. 힘이 아니라 성질이며, 정지한 물체에도 똑같이 적용된다.",
    keywords: [
      { label: "힘이 아닌 성질", match: ["성질", "힘이 아니"] },
      { label: "외부 힘이 없을 때", match: ["외부", "외력", "힘이 작용하지", "힘을 안 주", "힘이 없"] },
      { label: "상태 유지", match: ["유지", "그대로", "계속"] },
    ],
  },
  {
    id: "evolution",
    word: "진화",
    definition:
      "세대를 거치며 집단 안의 유전자 구성 비율이 변하는 현상. 방향도 목적도 없으며, 개체가 아니라 집단에서 일어난다.",
    keywords: [
      { label: "집단", match: ["집단", "개체군", "무리", "종 전체"] },
      { label: "유전자", match: ["유전자", "유전", "dna", "형질"] },
      { label: "세대", match: ["세대", "대를 거", "대대로"] },
    ],
  },
  {
    id: "inflation",
    word: "인플레이션",
    definition:
      "화폐 가치가 떨어져 전반적인 물가가 지속적으로 오르는 현상. '비싼 상태'가 아니라 '오르는 중'이라는 변화율이다.",
    keywords: [
      { label: "물가", match: ["물가", "가격", "값"] },
      { label: "지속적 상승", match: ["지속", "계속", "오르", "상승", "올라"] },
      { label: "화폐 가치 하락", match: ["화폐", "돈의 가치", "돈 가치", "가치가 떨어", "가치 하락"] },
    ],
  },
  {
    id: "algorithm",
    word: "알고리즘",
    definition:
      "문제를 풀기 위한, 유한한 단계로 이루어진 명확한 절차. 컴퓨터도 AI도 필요 없다. 라면 조리법도 알고리즘이다.",
    keywords: [
      { label: "절차·단계", match: ["절차", "단계", "순서", "과정"] },
      { label: "문제 해결", match: ["문제", "해결", "풀"] },
      { label: "유한함", match: ["유한", "끝", "종료", "정해진"] },
    ],
  },
];

export const WORD_IDS = WORDS.map((w) => w.id);

/** 랜딩에서 대표로 보여줄 단어. 세션의 첫 단어이기도 하다. */
export const FEATURED_WORD_ID = "democracy";

export function getWord(id: string): Word | undefined {
  return WORDS.find((w) => w.id === id);
}

/**
 * 각 키워드가 답에 들어 있는지. 순서는 `keywords`와 같다.
 * 다시 강조하지만 이 결과는 **힌트**이지 채점이 아니다.
 */
export function matchKeywords(text: string, keywords: Keyword[]): boolean[] {
  const t = text.toLowerCase();
  return keywords.map((k) => k.match.some((m) => t.includes(m.toLowerCase())));
}
