import type { Metadata } from "next";
import "./globals.css";

// OG 이미지·공유 링크의 절대경로 계산에 쓰인다. .env의 NEXT_PUBLIC_SITE_URL이
// compose의 build arg로 들어온다.
// ⚠️ NEXT_PUBLIC_*은 빌드 시점에 번들에 박힌다. 바꿨으면 재빌드해야 한다.
const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

const title = "내가 안다고 착각한 단어";
const description =
  "익숙한 단어를 정말 설명할 수 있는지 30초 만에 확인해 보세요. 안다고 답한 사람 대부분이 틀립니다.";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: { default: title, template: `%s · ${title}` },
  description,
  openGraph: {
    title,
    description,
    type: "website",
    locale: "ko_KR",
    siteName: title,
  },
  twitter: { card: "summary_large_image", title, description },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full">
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
