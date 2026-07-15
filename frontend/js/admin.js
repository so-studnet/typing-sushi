(() => {
  "use strict";

  const el = {
    loginScreen: document.getElementById("screen-login"),
    adminScreen: document.getElementById("screen-admin"),
    loginForm: document.getElementById("login-form"),
    passwordInput: document.getElementById("admin-password"),
    loginError: document.getElementById("login-error"),
    tabs: document.getElementById("difficulty-tabs"),
    addWordForm: document.getElementById("add-word-form"),
    resetWords: document.getElementById("reset-words"),
    newWord: document.getElementById("new-word"),
    wordCount: document.getElementById("word-count"),
    wordList: document.getElementById("word-list"),
    lbRefresh: document.getElementById("lb-refresh"),
    lbBody: document.getElementById("lb-body"),
    lbEmpty: document.getElementById("lb-empty"),
    alRefresh: document.getElementById("al-refresh"),
    alBody: document.getElementById("al-body"),
    alEmpty: document.getElementById("al-empty"),
    alCount: document.getElementById("al-count"),
    adminError: document.getElementById("admin-error"),
    logoutBtn: document.getElementById("logout-btn"),
  };

  let password = sessionStorage.getItem("adminPassword") || null;
  let difficulty = "easy";

  function api(path, options = {}) {
    const headers = Object.assign(
      { "X-Admin-Password": password },
      options.headers || {}
    );
    return fetch(path, Object.assign({}, options, { headers }));
  }

  async function errorOf(res) {
    const data = await res.json().catch(() => ({}));
    return data.error || `Request failed (${res.status}).`;
  }

  function showAdmin() {
    el.loginScreen.classList.remove("active");
    el.adminScreen.classList.add("active");
  }

  function showLogin(message) {
    sessionStorage.removeItem("adminPassword");
    password = null;
    el.adminScreen.classList.remove("active");
    el.loginScreen.classList.add("active");
    el.loginError.textContent = message || "";
  }

  async function login(candidate) {
    password = candidate;
    const res = await api(`/api/admin/words?difficulty=${difficulty}`);
    if (!res.ok) {
      showLogin(await errorOf(res));
      return;
    }
    sessionStorage.setItem("adminPassword", candidate);
    showAdmin();
    renderWords(await res.json());
    loadLeaderboard();
    loadAccessLog();
  }

  function renderWords(entries) {
    const enabled = entries.filter((e) => e.enabled).length;
    el.wordCount.textContent = `${entries.length} entries (${enabled} enabled)`;
    el.wordList.textContent = "";
    for (const entry of entries) {
      const li = document.createElement("li");

      const label = document.createElement("label");
      label.className = "word-toggle";
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.checked = entry.enabled;
      checkbox.title = entry.enabled
        ? "Enabled — uncheck to stop quizzing this word"
        : "Disabled — check to quiz this word again";
      checkbox.addEventListener("change", () => setWordEnabled(entry.word, checkbox.checked));
      const text = document.createElement("span");
      text.textContent = entry.word;
      if (!entry.enabled) text.className = "word-off";
      label.append(checkbox, text);

      const del = document.createElement("button");
      del.type = "button";
      del.className = "word-delete";
      del.textContent = "✕";
      del.title = "Remove this word";
      del.addEventListener("click", () => removeWord(entry.word));

      li.append(label, del);
      el.wordList.append(li);
    }
  }

  async function setWordEnabled(word, enabled) {
    el.adminError.textContent = "";
    try {
      const res = await api("/api/admin/words", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ difficulty, word, enabled }),
      });
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      renderWords(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not update the word.";
      loadWords(); // re-sync the checkboxes after a rejected toggle
    }
  }

  async function loadWords() {
    el.adminError.textContent = "";
    try {
      const res = await api(`/api/admin/words?difficulty=${difficulty}`);
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      renderWords(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not load words.";
    }
  }

  async function addWord(word) {
    el.adminError.textContent = "";
    try {
      const res = await api("/api/admin/words", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ difficulty, word }),
      });
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      el.newWord.value = "";
      renderWords(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not add the word.";
    }
  }

  async function removeWord(word) {
    if (!window.confirm(`Remove "${word}" from the ${difficulty} list?`)) return;
    el.adminError.textContent = "";
    try {
      const res = await api(
        `/api/admin/words?difficulty=${difficulty}&word=${encodeURIComponent(word)}`,
        { method: "DELETE" }
      );
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      renderWords(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not remove the word.";
    }
  }

  async function resetWords() {
    if (!window.confirm(
      `Discard the saved "${difficulty}" list and reload the original file defaults?`
    )) return;
    el.adminError.textContent = "";
    try {
      const res = await api("/api/admin/words/reset", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ difficulty }),
      });
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      renderWords(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not reset the list.";
    }
  }

  async function loadLeaderboard() {
    el.adminError.textContent = "";
    try {
      const res = await api("/api/admin/leaderboard");
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      renderLeaderboard(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not load the leaderboard.";
    }
  }

  function renderLeaderboard(entries) {
    el.lbBody.textContent = "";
    el.lbEmpty.hidden = entries.length > 0;
    entries.forEach((entry, i) => {
      const tr = document.createElement("tr");
      const cells = [
        String(i + 1),
        entry.name,
        entry.course,
        `$${Number(entry.earned).toFixed(2)}`,
        entry.recordedAt ? new Date(entry.recordedAt).toLocaleString() : "—",
      ];
      for (const value of cells) {
        const td = document.createElement("td");
        td.textContent = value;
        tr.append(td);
      }
      el.lbBody.append(tr);
    });
  }

  async function loadAccessLog() {
    el.adminError.textContent = "";
    try {
      const res = await api("/api/admin/accesslog");
      if (res.status === 401) return showLogin("Please log in again.");
      if (!res.ok) throw new Error(await errorOf(res));
      renderAccessLog(await res.json());
    } catch (err) {
      el.adminError.textContent = err.message || "Could not load the access log.";
    }
  }

  function renderAccessLog(entries) {
    el.alBody.textContent = "";
    el.alEmpty.hidden = entries.length > 0;
    el.alCount.textContent = String(entries.length);
    for (const entry of entries) {
      const tr = document.createElement("tr");

      const time = document.createElement("td");
      time.textContent = entry.time ? new Date(entry.time).toLocaleString() : "—";

      const ip = document.createElement("td");
      ip.textContent = entry.ip || "—";

      const ua = document.createElement("td");
      const raw = entry.userAgent || "—";
      ua.textContent = raw.length > 70 ? raw.slice(0, 70) + "…" : raw;
      ua.title = raw;

      tr.append(time, ip, ua);
      el.alBody.append(tr);
    }
  }

  el.loginForm.addEventListener("submit", (e) => {
    e.preventDefault();
    el.loginError.textContent = "";
    login(el.passwordInput.value);
  });

  el.tabs.querySelectorAll(".duration-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      difficulty = btn.dataset.difficulty;
      el.tabs.querySelectorAll(".duration-btn").forEach((b) => b.classList.remove("selected"));
      btn.classList.add("selected");
      loadWords();
    });
  });

  el.addWordForm.addEventListener("submit", (e) => {
    e.preventDefault();
    const word = el.newWord.value.trim();
    if (word) addWord(word);
  });

  el.resetWords.addEventListener("click", resetWords);
  el.lbRefresh.addEventListener("click", loadLeaderboard);
  el.alRefresh.addEventListener("click", loadAccessLog);
  el.logoutBtn.addEventListener("click", () => showLogin());

  if (password) login(password);
})();
