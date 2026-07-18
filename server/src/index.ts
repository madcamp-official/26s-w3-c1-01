import Fastify from "fastify";
import rateLimit from "@fastify/rate-limit";

import { migrate, pool } from "./db.js";
import { clean, insert } from "./events.js";
import { summary, wordStats } from "./stats.js";

/**
 * 백엔드. 하는 일은 셋뿐이다: 이벤트 받기, 통계 주기, 추천 단어 보관.
 *
 * CORS 설정이 없는 건 빠뜨린 게 아니다. Caddy가 같은 도메인의 `/api/*`를 여기로
 * 넘기고, 개발 중엔 Next의 rewrites가 같은 일을 한다. 즉 브라우저 입장에서
 * 이 API는 항상 동일 출처라 CORS가 발생할 일이 없다. 다른 도메인에서 부를 일이
 * 생기면 그때 붙이면 된다.
 */

const PORT = Number(process.env.PORT ?? 8080);
const ADMIN_TOKEN = process.env.ADMIN_TOKEN;

const app = Fastify({
  logger: { level: process.env.LOG_LEVEL ?? "info" },
  // Caddy 뒤에 있어서 실제 클라이언트 IP는 X-Forwarded-For에 있다. 이게 없으면
  // rate limit이 모든 사용자를 프록시 IP 하나로 묶어 서로를 막아 버린다.
  trustProxy: true,
  // 이 API가 받는 가장 큰 body가 이벤트 하나(수백 바이트)다. 기본값 1MB는
  // 이유 없이 넓다.
  bodyLimit: 8 * 1024,
});

// 공개 POST 엔드포인트라 아무나 DB를 채울 수 있다. 한 세션이 정상적으로 쏘는
// 이벤트가 20개 남짓이므로 분당 120이면 실사용자는 절대 안 걸린다.
await app.register(rateLimit, { max: 120, timeWindow: "1 minute" });

app.get("/api/health", async () => {
  await pool.query("SELECT 1");
  return { ok: true };
});

/**
 * 이벤트 수집.
 *
 * 무슨 일이 있어도 204를 준다. 이 앱은 분석이 하나도 없어도 100% 동작해야 하고,
 * 그건 프론트의 track()이 안 터진다는 뜻만이 아니라 서버가 사용자한테 실패를
 * 알릴 이유가 없다는 뜻이기도 하다. 게다가 sendBeacon은 응답을 안 본다.
 */
app.post("/api/events", async (req, reply) => {
  reply.code(204);
  const event = clean(req.body);
  if (!event) {
    req.log.debug({ body: req.body }, "버린 이벤트");
    return;
  }
  try {
    await insert(event);
  } catch (err) {
    req.log.error({ err, event: event.event }, "이벤트 저장 실패");
  }
});

app.get<{ Params: { wordId: string } }>("/api/stats/:wordId", async (req, reply) => {
  // 표본이 모자라면 null. 프론트는 null이면 문구를 숨긴다. stats.ts 주석 참고.
  reply.header("cache-control", "public, max-age=30");
  return await wordStats(req.params.wordId);
});

/**
 * 관리자 요약.
 *
 * 토큰이 없거나 틀리면 401이 아니라 404를 준다. 401은 "여기 뭔가 있다"를
 * 알려주는 것이고, 이건 공개 URL에 붙은 엔드포인트다. ADMIN_TOKEN을 설정하지
 * 않으면 엔드포인트 자체가 없는 것처럼 동작한다 — 실수로 열어 두는 것보다
 * 못 쓰는 게 낫다.
 */
app.get("/api/admin/summary", async (req, reply) => {
  if (!ADMIN_TOKEN || req.headers.authorization !== `Bearer ${ADMIN_TOKEN}`) {
    return reply.code(404).send({ error: "Not Found" });
  }
  return await summary();
});

async function shutdown(signal: string): Promise<void> {
  app.log.info({ signal }, "종료 중");
  await app.close();
  await pool.end();
  process.exit(0);
}
process.on("SIGTERM", () => void shutdown("SIGTERM"));
process.on("SIGINT", () => void shutdown("SIGINT"));

await migrate();
await app.listen({ port: PORT, host: "0.0.0.0" });
