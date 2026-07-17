// 실화 카드 생성 시 grounding용 소재 힌트 풀.
// LLM이 완전히 자유롭게 "실화"를 지어내면 환각(존재하지 않는 사건 창작) 위험이 커지므로,
// topic이 주어지지 않으면 위키피디아 등에 잘 문서화된 실제 사건 목록에서 하나를 뽑아 힌트로 준다.
const REAL_EVENT_TOPICS = [
  { category: "역사", topic: "1932년 호주 에뮤 전쟁" },
  { category: "역사", topic: "1518년 스트라스부르 무도병" },
  { category: "역사", topic: "1960년대 CIA 프로젝트 어쿠스틱 키티 (고양이 도청 프로젝트)" },
  { category: "역사", topic: "1942년 로스앤젤레스 전투 (가상의 일본군 공습으로 오인해 대공포를 발사한 사건)" },
  { category: "역사", topic: "1919년 보스턴 당밀 홍수 (당밀 저장 탱크 폭발 사고)" },
  { category: "사회", topic: "1926~1936년 토론토 '그레이트 스토크 더비' (출산 경쟁 유언장 소동)" },
  { category: "사회", topic: "1904년 세인트루이스 올림픽 마라톤에서 벌어진 이상 행동들" },
  { category: "과학", topic: "1938년 오슨 웰스의 라디오 드라마 '우주 전쟁' 방송 이후 발생한 사회적 소동" },
  { category: "과학", topic: "1989~1990년 벨기에 UFO 목격 사건(벨기에 UFO 웨이브)" },
  { category: "사회", topic: "1962년 탕가니카 웃음 전염병" },
  { category: "인물", topic: "알베르트 아인슈타인이 이스라엘 2대 대통령직을 제안받았으나 거절한 일화" },
  { category: "인물", topic: "베토벤이 청력을 거의 잃은 상태에서 교향곡 9번을 작곡하고 초연을 직접 지휘한 일화" },
  { category: "인물", topic: "아이작 뉴턴이 조폐국장(Master of the Mint)으로 일하며 위조범을 직접 수사한 일화" },
  { category: "인물", topic: "스티브 잡스가 대학 중퇴 후 청강한 캘리그래피 수업이 매킨토시 폰트에 영향을 준 일화" },
];

// category가 주어지면 같은 카테고리 소재 중에서만 고른다 (일치하는 게 없으면 전체 풀에서 고름).
// 카테고리와 무관한 소재를 잘못 뽑아 엉뚱한 카테고리에 실화 카드가 붙는 걸 방지한다.
function pickRandomTopic(category) {
  const pool = category
    ? REAL_EVENT_TOPICS.filter((t) => t.category === category)
    : REAL_EVENT_TOPICS;
  const source = pool.length > 0 ? pool : REAL_EVENT_TOPICS;
  return source[Math.floor(Math.random() * source.length)];
}

module.exports = { REAL_EVENT_TOPICS, pickRandomTopic };
