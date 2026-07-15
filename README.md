# Sushi Typing 🍣

An English-language typing game inspired by *寿司打 (Sushi-da)*. Race the
60-second clock, type sushi-themed words correctly, and earn enough virtual
money to cover your chosen course price.

## Stack

- **Frontend**: plain HTML/CSS/JavaScript (no build step, no dependencies)
- **Backend**: plain Java (JDK's built-in `com.sun.net.httpserver.HttpServer`,
  no external dependencies or build tool required)

## Running

Requires a JDK (17+ recommended).

**macOS/Linux, or Git Bash on Windows:**

```bash
./run.sh
```

**Windows (cmd or double-click), if the JDK isn't on your PATH:**

Set `JAVA_HOME` to your JDK folder once:

```cmd
setx JAVA_HOME "C:\path\to\your\jdk"
```

Close and reopen the terminal, then run (or just double-click) `run.bat`.

---

Either script compiles the backend and starts a server on
`http://localhost:8080`, which serves the frontend and the JSON API from the
same origin. Open that URL in your browser to play.

Set a different port with the `PORT` environment variable:

```bash
PORT=3000 ./run.sh
```

```cmd
set "PORT=3000" && run.bat
```

## How it works

1. Pick a course (Beginner $10 / Standard $30 / Pro $50 / Notion $40 /
   Notion AI $45) on the start screen.
2. Type the displayed sushi-related word/phrase exactly; each correct
   character earns you $0.05.
3. You have 60 seconds. If your total earnings reach the course price, the
   meal's on the house!
4. Save your score to the leaderboard. Locally it persists in
   `backend/data/leaderboard.json`; on a host without a persistent disk it
   can persist in a Firebase Realtime Database instead (see
   "Persistent leaderboard" below).

## Editing the word list

The words/phrases quizzed in the game live in plain text files under
`backend/wordbank/` — one entry per line. Lines starting with `#` and blank
lines are ignored, so you can use them for comments/organization.

- `easy.txt`, `medium.txt`, `hard.txt` — the Beginner/Standard/Pro courses
- `notion.txt` — the Notion course: example sentences imported from the
  Notion "未知英単語帳" database's 例文 column (a one-time snapshot, not
  synced)
- `notion-words.txt` — seed words for the Notion AI course, imported from
  the same database's 単語 column. These are not quizzed directly; see
  "Notion AI course" below.

To add, remove, or rebalance the quizzed content, just edit these files
directly and restart the server (`./run.sh`) — no Java changes or
recompilation needed. If a file is ever missing or empty, the server falls
back to a small built-in word list and logs a warning, rather than failing
to start.

## AI explanations (optional)

Click "💡 Explain this" during a round to open a modal with an English
explanation of the current word/sentence, powered by the Groq API. The round
pauses (timer stops, typing disabled) while the modal is open.

This requires a free Groq API key from
[console.groq.com](https://console.groq.com/keys) -- no credit card
required -- set as an environment variable before starting the server:

```bash
GROQ_API_KEY=your-key-here ./run.sh
```

```cmd
set "GROQ_API_KEY=your-key-here" && run.bat
```

Without it, the button still works but shows "AI explanations are not
configured on this server." instead of failing. The model defaults to
`llama-3.3-70b-versatile`; override it with `GROQ_MODEL` if needed (see
[console.groq.com/docs/models](https://console.groq.com/docs/models) for
currently available model IDs). The API key is only ever used server-side
and is never sent to the browser.

## Notion AI course (optional)

The 🤖 **Notion AI** course quizzes freshly generated sentences instead of a
fixed list. Each time a round starts, the server picks a random sample of
seed words from `backend/wordbank/notion-words.txt` (imported from the
Notion "未知英単語帳" database's 単語 column) and asks the Groq API to write
one TOEIC-style example sentence per seed word — so every round practices
the same vocabulary in new sentences.

It uses the same `GROQ_API_KEY` / `GROQ_MODEL` environment variables as the
AI explanations above. When the key is not set, or the API call fails or
returns something unusable, the course silently falls back to the static
Notion course sentences (`notion.txt`), so it always works. Generation adds
a few seconds before the round starts; the start screen shows a
"Generating fresh TOEIC-style sentences…" note while it waits.

The seed words can also be edited at runtime from the admin page's
**Notion AI** tab (in-memory only, like the other lists).

## Admin page (optional)

Open `/admin.html` (e.g. `https://your-app.onrender.com/admin.html`) to
manage the running server from the browser:

- **Word lists**: view, add, remove, and enable/disable words per course at
  runtime. Unchecking a word keeps it in the list but stops it being
  quizzed, so it can be brought back later without retyping it. These edits
  apply to the live server immediately but are in-memory only — a restart
  or redeploy reloads the original `backend/wordbank/*.txt` files with
  everything enabled. For permanent changes, edit those files instead.
- **Leaderboard**: view the stored top scores, including when each one was
  recorded.
- **Access log**: view recent visits to the game's top page (up to 200,
  newest first) with time, IP address, and browser. Stored the same way as
  the leaderboard (Firebase when configured, local file otherwise), so it
  survives restarts.

Access requires the `ADMIN_PASSWORD` environment variable to be set on the
server (on Render: the service's **Environment** tab); the page asks for
that password and sends it with every admin request. Without the variable,
the admin API responds "not configured" and the page is effectively
disabled. The password is compared in constant time and never stored in
the repo.

## Deploying so others can play (Render)

Running the game locally means every player needs their own machine set up
(and, for AI explanations, their own `GROQ_API_KEY`). To let other people
play from just a URL, deploy one shared server instead -- the API key then
lives only on that server, never on players' machines or in this repo.

This repo includes a `Dockerfile` for that: it compiles the backend and
bundles the frontend/wordbank into a container that listens on the `PORT`
environment variable (matching how most hosts, including Render, work).

[Render](https://render.com) is recommended: its free "Web Service" plan
needs no credit card. Steps:

1. Sign up at render.com (GitHub login is fine).
2. **New +** -> **Web Service** -> connect this GitHub repository.
3. Render should detect the `Dockerfile` automatically. If asked, set:
   - **Environment**: Docker
   - **Instance type**: Free
4. Under **Environment Variables**, add `GROQ_API_KEY` with your key (and
   optionally `GROQ_MODEL` and `ADMIN_PASSWORD`, see "Admin page" above).
   This is the only place the keys need to exist.
5. Click **Create Web Service**. The first build takes a few minutes; Render
   gives you a public URL (`https://your-app.onrender.com`) when it's done.
6. Share that URL -- anyone can open it and play, no install required.

Notes:
- The free plan spins the service down after inactivity, so the first
  request after a while takes a few extra seconds to wake it back up.
- The free plan's disk isn't persistent, so the local
  `backend/data/leaderboard.json` resets on restarts/redeploys. Configure
  the Firebase-backed leaderboard (next section) to keep scores.
- Pushing to this repo's default branch will auto-redeploy if you enable
  Render's auto-deploy option.

## Persistent leaderboard (optional, Firebase)

Render's free plan wipes the container filesystem on every restart, so the
file-backed leaderboard resets whenever the service spins down. To keep
scores across restarts, the server can store the leaderboard in a
[Firebase Realtime Database](https://firebase.google.com/) instead — its
free "Spark" plan needs no credit card, and the server talks to it over
plain REST (no SDK, keeping this project dependency-free).

Setup:

1. At [console.firebase.google.com](https://console.firebase.google.com),
   create a project, then create a **Realtime Database**
   (Database と Storage -> Realtime Database).
2. Note the database URL shown at the top of the Data tab, e.g.
   `https://your-project-default-rtdb.asia-southeast1.firebasedatabase.app`.
3. In the database's **Rules** tab, deny direct public access — the server
   authenticates as an admin, so it is unaffected by these rules:

   ```json
   {
     "rules": {
       ".read": false,
       ".write": false
     }
   }
   ```

4. In **Project settings -> Service accounts**, click **Generate new
   private key** to download the service account key JSON.
5. Set two environment variables on the server (on Render: the service's
   **Environment** tab):
   - `FIREBASE_DB_URL` — the database URL from step 2
   - `FIREBASE_SERVICE_ACCOUNT` — the entire key-file JSON from step 4,
     pasted as the value (it fits on one line)

When both variables are set, the server logs
`Leaderboard persistence: Firebase Realtime Database` at startup and reads/
writes the top-10 list at `/leaderboard` in the database (the admin page's
access log is stored at `/accessLog` the same way). When they are not set
(e.g. local development), both fall back to local files under
`backend/data/` exactly as before. The service account key is only ever
used server-side; never commit it to the repo.

## API

- `GET /api/words?difficulty=easy|medium|hard|notion|notion-ai&count=N` —
  random word list; `notion-ai` generates fresh TOEIC-style sentences via
  the Groq API (falls back to the `notion` pool without `GROQ_API_KEY`)
- `GET /api/leaderboard` — top 10 scores
- `POST /api/score` — `{"name": "...", "course": "...", "earned": 12.34}`,
  returns the updated top-10 leaderboard
- `POST /api/explain` — `{"sentence": "..."}`, returns
  `{"explanation": "..."}` (requires `GROQ_API_KEY`, see above)

Admin endpoints (all require the `X-Admin-Password` header matching the
`ADMIN_PASSWORD` environment variable, see "Admin page" above):

- `GET /api/admin/words?difficulty=easy` — full word list for a difficulty,
  as `[{"word": "...", "enabled": true}, ...]`
- `POST /api/admin/words` — `{"difficulty": "...", "word": "..."}`, adds a
  word at runtime, returns the updated list
- `PUT /api/admin/words` — `{"difficulty": "...", "word": "...",
  "enabled": false}`, enables/disables a word at runtime (disabled words
  stay listed but are not quizzed), returns the updated list
- `DELETE /api/admin/words?difficulty=easy&word=...` — removes a word at
  runtime, returns the updated list
- `GET /api/admin/leaderboard` — top scores including `recordedAt`
- `GET /api/admin/accesslog` — recent top-page visits (time, IP, browser)

## Project layout

```
frontend/          static game client
  index.html
  admin.html       admin page (word lists + leaderboard, needs ADMIN_PASSWORD)
  css/style.css
  js/app.js
  js/admin.js
backend/
  src/main/java/com/typingsushi/
    Main.java        HTTP server, routing, static file serving
    WordBank.java     loads word lists (below) by difficulty
    AiSentences.java  Groq-generated TOEIC-style sentences for the Notion AI course
    Leaderboard.java  in-memory top scores, persisted to file or Firebase
    AccessLog.java    recent top-page visits, persisted to file or Firebase
    FirebaseStore.java Firebase Realtime Database REST client (optional persistence)
    Json.java         tiny JSON encode/decode helpers
  wordbank/          editable word/phrase lists (easy/medium/hard/notion.txt,
                     notion-words.txt = Notion AI seed words)
run.sh              compile + run (macOS/Linux/Git Bash)
run.bat             compile + run (Windows cmd, uses JAVA_HOME)
Dockerfile          container build for hosting (e.g. Render), see "Deploying" above
```
