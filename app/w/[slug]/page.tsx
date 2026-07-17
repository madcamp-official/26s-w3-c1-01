import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { WORDS, getWord } from "@/data/words";
import WordPlay from "@/components/WordPlay";

/** 단어 5개뿐이므로 전부 정적으로 미리 만든다. */
export function generateStaticParams() {
  return WORDS.map((w) => ({ slug: w.id }));
}

/**
 * 공유 링크의 미리보기가 이 함수에서 나온다. 카톡·슬랙에 링크를 붙였을 때
 * 단어가 보이느냐 "Create Next App"이 보이느냐가 클릭률을 가른다.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const word = getWord(slug);
  if (!word) return {};

  const title = `"${word.word}"를 정확히 설명할 수 있나요?`;
  return {
    title: word.word,
    description: title,
    openGraph: { title, description: "안다고 답한 사람 대부분이 틀립니다." },
  };
}

export default async function WordPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const word = getWord(slug);
  if (!word) notFound();

  return <WordPlay word={word} />;
}
