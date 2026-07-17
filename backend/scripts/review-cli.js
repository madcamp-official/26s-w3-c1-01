#!/usr/bin/env node
// 카드 검수용 CLI. 실행 중인 백엔드 서버(HTTP API)를 통해 카드 상태를 조회/전환한다.
// (저장소가 인메모리라 서버 프로세스 밖에서 직접 접근할 방법이 없기 때문)
//
// 사용법:
//   node scripts/review-cli.js list [STATUS]        예) node scripts/review-cli.js list DRAFT
//   node scripts/review-cli.js publish <cardId>
//   node scripts/review-cli.js reject <cardId>

const BASE_URL = process.env.BACKEND_URL || "http://localhost:3001";

async function listCards(status) {
  const url = new URL("/cards", BASE_URL);
  if (status) url.searchParams.set("status", status);

  const res = await fetch(url);
  if (!res.ok) throw new Error(`GET /cards failed: ${res.status}`);
  const cards = await res.json();

  if (cards.length === 0) {
    console.log(status ? `[${status}] 카드가 없습니다.` : "카드가 없습니다.");
    return;
  }

  for (const card of cards) {
    console.log(`\n[${card.status}] ${card.id} (${card.type}, ${card.category}, ${card.difficulty ?? "-"})`);
    console.log(`  지문: ${card.body}`);
    console.log(`  정답: ${card.answer_title}`);
    console.log(`  근거: ${card.answer_fact}`);
    if (card.source_url) console.log(`  출처: ${card.source_url}`);
    if (card.verification_note) console.log(`  검증노트: ${card.verification_note}`);
  }
  console.log(`\n총 ${cards.length}개`);
}

async function setStatus(cardId, status) {
  if (!cardId) throw new Error("cardId가 필요합니다.");
  const res = await fetch(new URL(`/cards/${cardId}/status`, BASE_URL), {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`PATCH /cards/${cardId}/status failed: ${res.status} ${body}`);
  }
  const card = await res.json();
  console.log(`OK: ${card.id} -> ${card.status}`);
}

async function main() {
  const [command, arg] = process.argv.slice(2);

  switch (command) {
    case "list":
      await listCards(arg);
      break;
    case "publish":
      await setStatus(arg, "PUBLISHED");
      break;
    case "reject":
      await setStatus(arg, "REJECTED");
      break;
    default:
      console.log(
        "사용법:\n" +
          "  node scripts/review-cli.js list [DRAFT|VERIFIED|PUBLISHED|REJECTED]\n" +
          "  node scripts/review-cli.js publish <cardId>\n" +
          "  node scripts/review-cli.js reject <cardId>"
      );
      process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error("오류:", err.message);
  process.exitCode = 1;
});
