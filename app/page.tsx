import { FEATURED_WORD_ID, WORDS, getWord } from "@/data/words";
import { getWordStats, STATS_ARE_PLACEHOLDER } from "@/lib/stats";
import StartButton from "@/components/StartButton";

/**
 * 랜딩 = 소개 + 시작 화면.
 *
 * 긴 소개 페이지를 두지 않는다. 훅 한 문장과 버튼 하나로 끝내는 게 이탈을 막는
 * 유일한 방법이고, 이 프로젝트의 지표가 그 이탈률이다.
 */

export default async function Home() {
  const word = getWord(FEATURED_WORD_ID);
  if (!word) throw new Error(`FEATURED_WORD_ID(${FEATURED_WORD_ID})에 해당하는 단어가 없습니다.`);

  const stats = await getWordStats(word.id);
  const rate = stats ? Math.round(stats.confidentCorrectRate * 100) : null;

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center px-5 py-12">
      <p className="text-center text-sm text-muted">내가 안다고 착각한 단어</p>

      <h1 className="mt-10 text-center text-5xl font-bold tracking-tight sm:text-6xl">
        {word.word}
      </h1>

      <p className="mt-6 text-center text-xl leading-relaxed">설명할 수 있나요?</p>

      {rate !== null && (
        <p className="mt-3 text-center text-muted">
          안다고 답한 사람 중 <strong className="font-semibold text-foreground">{rate}%</strong>만
          설명해냈습니다
        </p>
      )}

      <div className="mt-14">
        <StartButton startWordId={word.id} />
        <p className="mt-3 text-center text-sm text-muted">{WORDS.length}단어 · 30초</p>
      </div>

      {STATS_ARE_PLACEHOLDER && (
        <p className="mt-12 text-center text-xs text-muted">⚠️ 위 수치는 아직 예시입니다</p>
      )}
    </main>
  );
}
