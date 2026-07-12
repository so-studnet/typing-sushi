(() => {
  "use strict";

  const COURSES = {
    beginner: { label: "Beginner", target: 10, difficulty: "easy", rate: 0.05 },
    standard: { label: "Standard", target: 30, difficulty: "medium", rate: 0.05 },
    pro: { label: "Pro", target: 50, difficulty: "hard", rate: 0.05 },
    notion: { label: "Notion", target: 40, difficulty: "notion", rate: 0.05 },
  };
  const DEFAULT_DURATION = 60;
  const SUSHI_EMOJI = ["🍣", "🍙", "🍱", "🍤", "🐟", "🍚"];

  let selectedDuration = DEFAULT_DURATION;

  const screens = {
    start: document.getElementById("screen-start"),
    game: document.getElementById("screen-game"),
    result: document.getElementById("screen-result"),
  };

  const el = {
    startError: document.getElementById("start-error"),
    timeLeft: document.getElementById("time-left"),
    earned: document.getElementById("earned"),
    targetAmount: document.getElementById("target-amount"),
    wordDisplay: document.getElementById("word-display"),
    typeInput: document.getElementById("type-input"),
    beltPlates: document.getElementById("belt-plates"),
    resultTitle: document.getElementById("result-title"),
    resEarned: document.getElementById("res-earned"),
    resTarget: document.getElementById("res-target"),
    resSpeed: document.getElementById("res-speed"),
    resAccuracy: document.getElementById("res-accuracy"),
    leaderboardForm: document.getElementById("leaderboard-form"),
    playerName: document.getElementById("player-name"),
    leaderboard: document.getElementById("leaderboard"),
    retryBtn: document.getElementById("retry-btn"),
    homeBtn: document.getElementById("home-btn"),
    endlessTag: document.getElementById("endless-tag"),
  };

  let state = null;
  let audioCtx = null;

  function getAudioCtx() {
    if (!audioCtx) {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      audioCtx = new AudioContextClass();
    }
    return audioCtx;
  }

  function playTypeSound(correct) {
    try {
      const ctx = getAudioCtx();
      if (ctx.state === "suspended") ctx.resume();

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = correct ? "square" : "sawtooth";
      osc.frequency.value = correct ? 720 : 180;
      gain.gain.value = 0.06;
      osc.connect(gain);
      gain.connect(ctx.destination);

      const now = ctx.currentTime;
      const duration = correct ? 0.05 : 0.12;
      gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
      osc.start(now);
      osc.stop(now + duration);
    } catch (err) {
      // Audio is a nice-to-have; ignore failures (e.g. unsupported browser).
    }
  }

  function showScreen(name) {
    Object.values(screens).forEach((s) => s.classList.remove("active"));
    screens[name].classList.add("active");
  }

  function renderBelt() {
    const plates = [];
    for (let i = 0; i < 16; i++) {
      plates.push(SUSHI_EMOJI[i % SUSHI_EMOJI.length]);
    }
    el.beltPlates.textContent = plates.concat(plates).join(" ");
  }

  async function fetchWords(difficulty) {
    const res = await fetch(`/api/words?difficulty=${encodeURIComponent(difficulty)}&count=60`);
    if (!res.ok) throw new Error("Failed to load words");
    return res.json();
  }

  async function startGame(courseKey) {
    const course = COURSES[courseKey];
    el.startError.textContent = "";
    let words;
    try {
      words = await fetchWords(course.difficulty);
    } catch (err) {
      el.startError.textContent = "Could not reach the kitchen (server). Please try again.";
      return;
    }
    if (!words || words.length === 0) {
      el.startError.textContent = "No words available. Please try again.";
      return;
    }

    const endless = selectedDuration === "endless";

    state = {
      course: courseKey,
      target: course.target,
      rate: course.rate,
      words,
      wordIndex: 0,
      typed: "",
      earned: 0,
      totalKeystrokes: 0,
      correctKeystrokes: 0,
      endless,
      totalSeconds: endless ? null : selectedDuration,
      secondsLeft: endless ? 0 : selectedDuration,
      startedAt: Date.now(),
      timerId: null,
      finished: false,
    };

    el.targetAmount.textContent = course.target.toFixed(0);
    el.earned.textContent = "0.00";
    el.timeLeft.textContent = endless ? "0" : String(selectedDuration);
    el.endlessTag.hidden = !endless;
    el.typeInput.value = "";

    renderBelt();
    renderWord();
    showScreen("game");
    el.typeInput.disabled = false;
    el.typeInput.focus();

    state.timerId = setInterval(tick, 1000);
  }

  function currentWord() {
    return state.words[state.wordIndex % state.words.length];
  }

  function renderWord() {
    const word = currentWord();
    const typed = state.typed;
    let html = "";
    for (let i = 0; i < word.length; i++) {
      const target = word[i];
      if (i < typed.length) {
        html += typed[i] === target
          ? `<span class="char-correct">${escapeHtml(target)}</span>`
          : `<span class="char-wrong">${escapeHtml(target)}</span>`;
      } else {
        html += `<span class="char-pending">${escapeHtml(target)}</span>`;
      }
    }
    el.wordDisplay.innerHTML = html;
  }

  function escapeHtml(ch) {
    return ch
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function tick() {
    if (state.endless) {
      state.secondsLeft += 1;
      el.timeLeft.textContent = String(state.secondsLeft);
      return;
    }
    state.secondsLeft -= 1;
    el.timeLeft.textContent = String(Math.max(0, state.secondsLeft));
    if (state.secondsLeft <= 0) {
      endGame();
    }
  }

  function onInput(e) {
    if (!state || state.finished) return;
    const value = e.target.value;
    const word = currentWord();

    if (value.length > state.typed.length) {
      // one or more new characters typed
      const newChars = value.slice(state.typed.length);
      for (const ch of newChars) {
        state.totalKeystrokes += 1;
        const idx = state.typed.length;
        const isCorrect = word[idx] === ch;
        if (isCorrect) {
          state.correctKeystrokes += 1;
          state.earned += state.rate;
        }
        playTypeSound(isCorrect);
        state.typed += ch;
      }
    } else {
      state.typed = value;
    }

    el.earned.textContent = state.earned.toFixed(2);

    if (state.typed === word) {
      state.wordIndex += 1;
      state.typed = "";
      el.typeInput.value = "";
    } else {
      el.typeInput.value = state.typed;
    }

    renderWord();
  }

  function endGame() {
    if (state.finished) return;
    state.finished = true;
    clearInterval(state.timerId);
    el.typeInput.disabled = true;

    const elapsed = state.totalSeconds;
    const speed = state.correctKeystrokes / elapsed;
    const accuracy = state.totalKeystrokes === 0
      ? 100
      : Math.round((state.correctKeystrokes / state.totalKeystrokes) * 1000) / 10;

    const success = state.earned >= state.target;
    el.resultTitle.textContent = success
      ? "🎉 Meal's on the house!"
      : "🍥 So close — try again!";
    el.resEarned.textContent = state.earned.toFixed(2);
    el.resTarget.textContent = state.target.toFixed(0);
    el.resSpeed.textContent = speed.toFixed(2);
    el.resAccuracy.textContent = accuracy.toFixed(1);

    el.playerName.value = "";
    el.leaderboard.innerHTML = "";
    loadLeaderboard();

    showScreen("result");
  }

  async function loadLeaderboard() {
    try {
      const res = await fetch("/api/leaderboard");
      if (!res.ok) throw new Error("failed");
      const data = await res.json();
      renderLeaderboard(data);
    } catch (err) {
      el.leaderboard.innerHTML = "<p>Leaderboard unavailable.</p>";
    }
  }

  function renderLeaderboard(entries) {
    if (!entries || entries.length === 0) {
      el.leaderboard.innerHTML = "<h3>🏆 Top Scores</h3><p>No scores yet — be the first!</p>";
      return;
    }
    const items = entries
      .map(
        (entry, i) =>
          `<li><span>${i + 1}. <span class="lb-name">${escapeHtml(entry.name)}</span></span><span class="lb-amount">$${Number(entry.earned).toFixed(2)}</span></li>`
      )
      .join("");
    el.leaderboard.innerHTML = `<h3>🏆 Top Scores</h3><ol>${items}</ol>`;
  }

  async function submitScore(e) {
    e.preventDefault();
    if (!state) return;
    const name = el.playerName.value.trim();
    if (!name) return;

    try {
      const res = await fetch("/api/score", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          course: state.course,
          earned: Number(state.earned.toFixed(2)),
        }),
      });
      if (!res.ok) throw new Error("failed");
      const data = await res.json();
      renderLeaderboard(data);
      el.leaderboardForm.querySelector("button").disabled = true;
    } catch (err) {
      el.leaderboard.innerHTML = "<p>Could not save score. Please try again.</p>";
    }
  }

  function resetToStart() {
    state = null;
    el.leaderboardForm.querySelector("button").disabled = false;
    showScreen("start");
  }

  function quitToStart() {
    if (state && !state.finished) {
      if (!window.confirm("Quit this round and return to the home screen?")) return;
      clearInterval(state.timerId);
    }
    el.typeInput.disabled = false;
    resetToStart();
  }

  document.querySelectorAll(".duration-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      selectedDuration = btn.dataset.duration === "endless" ? "endless" : Number(btn.dataset.duration);
      document.querySelectorAll(".duration-btn").forEach((b) => b.classList.remove("selected"));
      btn.classList.add("selected");
    });
  });
  document.querySelectorAll(".course-btn").forEach((btn) => {
    btn.addEventListener("click", () => startGame(btn.dataset.course));
  });
  el.typeInput.addEventListener("input", onInput);
  el.leaderboardForm.addEventListener("submit", submitScore);
  el.retryBtn.addEventListener("click", resetToStart);
  el.homeBtn.addEventListener("click", quitToStart);
})();
