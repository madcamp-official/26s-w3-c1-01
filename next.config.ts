import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 런타임 이미지에 node_modules 전체를 넣지 않기 위해. 도커 빌드가 이걸 전제한다.
  output: "standalone",

  /**
   * 개발용 프록시.
   *
   * 브라우저에서 API는 항상 동일 출처(`/api/*`)여야 한다 — 그래야 CORS도,
   * https 페이지에서 http API를 부르는 혼합 콘텐츠 차단도 없다. 운영에선 Caddy가
   * `/api/*`를 먼저 가로채서 여기까지 오지 않고, `next dev`로 띄웠을 때만 이
   * rewrite가 백엔드로 넘긴다.
   */
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
