/**
 * ⚠️ 경고: 이 파일의 모든 수치는 지어낸 것이다. 실제 사용자 데이터가 아니다.
 *
 * 백엔드가 아직 없어서 "안다고 답한 사람 중 34%만 설명해냈습니다" 같은 사회적
 * 증거를 채우기 위한 임시 placeholder다. 이 상태로 공개 배포하면 실제 사용자에게
 * 허위 통계를 보여주게 된다.
 *
 * ── 공개 배포 전에 반드시 셋 중 하나를 할 것 ───────────────────────────
 *   1. 실제 백엔드를 붙이고 STATS_ARE_PLACEHOLDER를 false로 내린다  (권장)
 *   2. 소규모 파일럿으로 진짜 숫자를 받아 이 파일을 덮어쓴다
 *   3. UI에서 통계 영역을 제거한다
 *
 * STATS_ARE_PLACEHOLDER가 true인 동안에는 UI에 경고 문구가 강제 노출된다.
 * 문구를 지우려면 플래그를 내려야 하고, 플래그를 내리려면 진짜 데이터가 있어야
 * 한다. 실수로 거짓말한 채 배포되는 걸 막기 위한 장치다.
 *
 * 연결 방법은 docs/PROJECT_NOTES.md 참고.
 */

export const STATS_ARE_PLACEHOLDER = true;

export type WordStats = {
  /** 이 단어를 시도한 총 인원 */
  totalAnswers: number;
  /** "안다"고 답한 사람 중 스스로 설명해냈다고 판정한 비율 (0~1) */
  confidentCorrectRate: number;
};

export const SEED_STATS: Record<string, WordStats> = {
  democracy: { totalAnswers: 1284, confidentCorrectRate: 0.34 },
  inertia: { totalAnswers: 1102, confidentCorrectRate: 0.28 },
  evolution: { totalAnswers: 1041, confidentCorrectRate: 0.39 },
  inflation: { totalAnswers: 977, confidentCorrectRate: 0.41 },
  algorithm: { totalAnswers: 933, confidentCorrectRate: 0.33 },
};
