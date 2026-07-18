/**
 * 통계 조회.
 *
 * 이 파일 하나가 서버와 UI 사이의 전부다. 서버/클라이언트 양쪽에서 호출된다:
 *   - 랜딩(`app/page.tsx`)  → 서버에서, 빌드/ISR 시점에
 *   - 정답 공개(`WordPlay`) → 브라우저에서, 렌더 후에
 *
 * ⚠️ `null`은 "통계 없음"이지 에러가 아니다. 서버는 표본이 30명이 안 되면
 * 일부러 null을 준다(`server/src/stats.ts` 참고). UI는 null이면 문구를 통째로
 * 숨기고, 그게 정상 동작이다. 공개 직후엔 계속 null이 나오는 게 맞다.
 */

export type WordStats = {
  /** "안다"고 답한 사람 중 스스로 맞았다고 판정한 비율 (0~1) */
  confidentCorrectRate: number;
};

/**
 * 브라우저에서는 항상 상대 경로(= 동일 출처)다. Caddy가, 개발 중엔 Next의
 * rewrites가 `/api/*`를 백엔드로 넘긴다.
 *
 * 서버 렌더 중에는 상대 경로를 쓸 수 없어서 컨테이너 내부 주소가 필요하다.
 * `typeof window`로 갈라내는 이유: `process.env`를 클라이언트 번들에서 어떻게
 * 처리하느냐가 번들러마다 달라서, 아예 클라이언트 코드가 그 줄에 닿지 않게 한다.
 */
function baseUrl(): string {
  if (typeof window !== "undefined") return "";
  return process.env.API_INTERNAL_URL ?? "http://localhost:8080";
}

export async function getWordStats(wordId: string): Promise<WordStats | null> {
  try {
    const res = await fetch(`${baseUrl()}/api/stats/${encodeURIComponent(wordId)}`, {
      // 랜딩을 정적으로 유지하면서 수치만 5분마다 갱신한다(ISR). 브라우저에서는
      // 이 옵션이 무시되고 그냥 fetch가 된다.
      next: { revalidate: 300 },
    });
    if (!res.ok) return null;
    return (await res.json()) as WordStats | null;
  } catch {
    // 도커 빌드 중엔 API가 아직 없고, 운영 중에 API가 죽을 수도 있다.
    // 통계는 없어도 되는 정보다 — 그것 때문에 화면이 안 뜨는 게 훨씬 나쁘다.
    return null;
  }
}
