/**
 * 랜딩에서 첫 단어로 답을 넘긴다.
 *
 * 랜딩이 이미 "민주주의, 설명할 수 있나요? [안다][모른다]"를 물었으므로, 첫 단어
 * 화면에서 똑같은 질문을 또 하면 안 된다. 그래서 랜딩의 답을 여기 담아 두고
 * WordPlay가 꺼내 쓴다.
 *
 * 왜 모듈 변수인가:
 *  - sessionStorage에 두면 렌더 중에 읽을 수 없다(SSR 하이드레이션이 깨진다).
 *    effect에서 setState로 끌어오면 React 19 린트 룰에 걸린다.
 *  - URL 쿼리(`?k=1`)에 두면 라우트가 동적이 되어 전 페이지 정적 생성이 깨지고,
 *    링크를 공유하면 답이 미리 정해진 채로 열린다.
 *  - 랜딩 → 단어는 클라이언트 내비게이션(페이지 리로드 없음)이라 모듈 변수가
 *    그대로 살아 있다. 렌더 중에 읽어도 순수하고, 새로고침하면 자연히 사라진다.
 *
 * 서버에서는 set이 절대 호출되지 않으므로(클라이언트 핸들러 전용) 요청 간에
 * 값이 새는 일은 없다.
 */

let pending: { wordId: string; knew: boolean } | null = null;

export function setPendingAnswer(wordId: string, knew: boolean): void {
  pending = { wordId, knew };
}

/**
 * 해당 단어로 넘어온 답이 있으면 반환. **소비하지 않으므로 렌더 중 호출해도 안전하다.**
 * wordId로 키를 걸어 두어서, 다음 단어로 넘어가면 자동으로 null이 된다.
 */
export function peekPendingAnswer(wordId: string): boolean | null {
  return pending && pending.wordId === wordId ? pending.knew : null;
}
