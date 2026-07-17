/**
 * ⚠️ 경고: 이 파일의 모든 수치는 지어낸 것이다. 실제 사용자 데이터가 아니다.
 *
 * 백엔드가 아직 없어서 결과 화면의 "다른 사람들은 이렇게 답했어요"를 채우기
 * 위한 임시 placeholder다. 이 상태로 공개 배포하면 실제 사용자에게 허위 수치를
 * 보여주게 된다.
 *
 * ── 공개 배포 전에 반드시 셋 중 하나를 할 것 ───────────────────────────
 *   1. 실제 백엔드를 붙이고 STATS_ARE_PLACEHOLDER를 false로 내린다  (권장)
 *   2. 소규모 파일럿으로 진짜 숫자를 받아 이 파일을 덮어쓴다
 *   3. UI에서 통계 영역을 제거한다
 *
 * STATS_ARE_PLACEHOLDER가 true인 동안에는 UI에 "예시 수치" 배지가 강제로
 * 노출된다. 배지를 지우려면 플래그를 내려야 하고, 플래그를 내리려면 진짜
 * 데이터가 있어야 한다. 실수로 거짓말한 채 배포되는 걸 막기 위한 장치다.
 *
 * 연결 방법은 docs/PROJECT_NOTES.md 참고.
 */

export const STATS_ARE_PLACEHOLDER = true;

export type WordStats = {
  /** 이 단어에 답한 총 인원 */
  totalAnswers: number;
  /** choiceId → 선택한 인원 수 */
  choiceCounts: Record<string, number>;
  /** "안다"(high·mid)고 답한 사람 중 정답을 고른 비율 (0~1) */
  confidentCorrectRate: number;
};

export const SEED_STATS: Record<string, WordStats> = {
  democracy: {
    totalAnswers: 1284,
    choiceCounts: { a: 552, b: 437, c: 208, d: 87 },
    confidentCorrectRate: 0.34,
  },
  inertia: {
    totalAnswers: 1102,
    choiceCounts: { a: 601, b: 314, c: 121, d: 66 },
    confidentCorrectRate: 0.28,
  },
  evolution: {
    totalAnswers: 1041,
    choiceCounts: { a: 268, b: 402, c: 249, d: 122 },
    confidentCorrectRate: 0.39,
  },
  inflation: {
    totalAnswers: 977,
    choiceCounts: { a: 371, b: 388, c: 118, d: 100 },
    confidentCorrectRate: 0.41,
  },
  algorithm: {
    totalAnswers: 933,
    choiceCounts: { a: 449, b: 305, c: 122, d: 57 },
    confidentCorrectRate: 0.33,
  },
};
