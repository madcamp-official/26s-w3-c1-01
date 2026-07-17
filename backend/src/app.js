const express = require("express");
const cors = require("cors");
const quizSessionsRouter = require("./routes/quizSessions");
const cardsRouter = require("./routes/cards");

const app = express();

app.use(cors());
app.use(express.json());

app.get("/health", (req, res) => res.json({ status: "ok" }));

app.use("/quiz-sessions", quizSessionsRouter);
app.use("/cards", cardsRouter);

app.use((req, res) => {
  res.status(404).json({ error: "not_found" });
});

module.exports = app;
