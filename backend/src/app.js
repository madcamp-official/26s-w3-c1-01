const express = require("express");
const cors = require("cors");
const quizSessionsRouter = require("./routes/quizSessions");
const cardsRouter = require("./routes/cards");

const app = express();

// CORS_ORIGIN 미설정 시(개발 기본값) 모든 origin 허용. 배포 시엔
// 콤마로 구분된 허용 origin 목록을 CORS_ORIGIN에 설정할 것 (예: https://foo.com,https://bar.com)
const corsOriginEnv = process.env.CORS_ORIGIN;
const corsOrigin = !corsOriginEnv || corsOriginEnv === "*"
  ? true
  : corsOriginEnv.split(",").map((origin) => origin.trim());

app.use(cors({ origin: corsOrigin }));
app.use(express.json());

app.get("/health", (req, res) => res.json({ status: "ok" }));

app.use("/quiz-sessions", quizSessionsRouter);
app.use("/cards", cardsRouter);

app.use((req, res) => {
  res.status(404).json({ error: "not_found" });
});

module.exports = app;
