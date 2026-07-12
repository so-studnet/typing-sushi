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

## Editing the word list

The words/phrases quizzed in the game live in plain text files under
`backend/wordbank/` (`easy.txt`, `medium.txt`, `hard.txt`) — one entry per
line. Lines starting with `#` and blank lines are ignored, so you can use
them for comments/organization.

To add, remove, or rebalance the quizzed content, just edit these files
directly and restart the server (`./run.sh`) — no Java changes or
recompilation needed. If a file is ever missing or empty, the server falls
back to a small built-in word list and logs a warning, rather than failing
to start.

## AI explanations (optional)

Click "💡 Explain this" during a round to open a modal with an English
explanation of the current word/sentence, powered by the Google Gemini API.
The round pauses (timer stops, typing disabled) while the modal is open.

This requires a free Gemini API key from
[Google AI Studio](https://aistudio.google.com/apikey), set as an
environment variable before starting the server:

```bash
GEMINI_API_KEY=your-key-here ./run.sh
```

Without it, the button still works but shows "AI explanations are not
configured on this server." instead of failing. The model defaults to
`gemini-2.0-flash`; override it with `GEMINI_MODEL` if needed. The API key
is only ever used server-side and is never sent to the browser.

## API

- `GET /api/words?difficulty=easy|medium|hard|notion&count=N` — random word list
- `GET /api/leaderboard` — top 10 scores
- `POST /api/score` — `{"name": "...", "course": "...", "earned": 12.34}`,
  returns the updated top-10 leaderboard
- `POST /api/explain` — `{"sentence": "..."}`, returns
  `{"explanation": "..."}` (requires `GEMINI_API_KEY`, see above)

## Project layout

```
frontend/          static game client
  index.html
  css/style.css
  js/app.js
backend/
  src/main/java/com/typingsushi/
    Main.java        HTTP server, routing, static file serving
    WordBank.java     loads word lists (below) by difficulty
    Leaderboard.java  in-memory + file-persisted top scores
    Json.java         tiny JSON encode/decode helpers
  wordbank/          editable word/phrase lists (easy.txt, medium.txt, hard.txt)
run.sh              compile + run
```
