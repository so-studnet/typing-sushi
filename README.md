# Sushi Typing 🍣

An English-language typing game inspired by *寿司打 (Sushi-da)*. Race the
60-second clock, type sushi-themed words correctly, and earn enough virtual
money to cover your chosen course price.

## Stack

- **Frontend**: plain HTML/CSS/JavaScript (no build step, no dependencies)
- **Backend**: plain Java (JDK's built-in `com.sun.net.httpserver.HttpServer`,
  no external dependencies or build tool required)

## Running

Requires a JDK (17+ recommended) on your PATH.

```bash
./run.sh
```

This compiles the backend and starts a server on `http://localhost:8080`,
which serves the frontend and the JSON API from the same origin. Open that
URL in your browser to play.

Set a different port with the `PORT` environment variable:

```bash
PORT=3000 ./run.sh
```

## How it works

1. Pick a course (Beginner $10 / Standard $30 / Pro $50) on the start screen.
2. Type the displayed sushi-related word/phrase exactly; each correct
   character earns you $0.05.
3. You have 60 seconds. If your total earnings reach the course price, the
   meal's on the house!
4. Save your score to the leaderboard, which persists across restarts in
   `backend/data/leaderboard.json`.

## API

- `GET /api/words?difficulty=easy|medium|hard&count=N` — random word list
- `GET /api/leaderboard` — top 10 scores
- `POST /api/score` — `{"name": "...", "course": "...", "earned": 12.34}`,
  returns the updated top-10 leaderboard

## Project layout

```
frontend/          static game client
  index.html
  css/style.css
  js/app.js
backend/
  src/main/java/com/typingsushi/
    Main.java        HTTP server, routing, static file serving
    WordBank.java     sushi-themed word lists by difficulty
    Leaderboard.java  in-memory + file-persisted top scores
    Json.java         tiny JSON encode/decode helpers
run.sh              compile + run
```
