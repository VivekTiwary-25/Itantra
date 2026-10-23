(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const screens = {
    main: $("#screen-main"),
    hf: $("#screen-hf"),
    editor: $("#screen-editor"),
    logs: $("#screen-logs"),
    detail: $("#screen-detail"),
  };

  const ptt = $("#ptt");
  const mainStatus = $("#main-status");
  const mainHint = $("#main-hint");
  const mainFail = $("#main-fail");
  const hfStatus = $("#hf-status");
  const hfHint = $("#hf-hint");
  const hfFail = $("#hf-fail");
  const hfDone = $("#hf-done");
  const editorInput = $("#editor-input");
  const editorSend = $("#editor-send");
  const discardDialog = $("#discard-dialog");
  const badge = $("#unread-badge");
  const logList = $("#log-list");
  const simFail = $("#sim-fail");

  const TRANSCRIPTS = [
    "We are eight people on the second floor of the school. Water is above the ground floor and still rising.",
    "Need a boat near the east bridge, two elderly people cannot walk.",
    "Medical kit needed at the temple, one person has a broken leg.",
  ];
  let transcriptIndex = 0;

  let current = "main";
  let messages = [];
  let nextId = 1;
  let timers = [];

  const later = (fn, ms) => timers.push(setTimeout(fn, ms));
  const clearTimers = () => {
    timers.forEach(clearTimeout);
    timers = [];
  };
  const vibrate = (ms) => {
    if ("vibrate" in navigator) navigator.vibrate(ms);
  };

  const fmtTime = (d) =>
    new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", hour12: false }).format(d);
  const fmtDate = (d) =>
    new Intl.DateTimeFormat(undefined, { weekday: "short", day: "numeric", month: "short", year: "numeric" }).format(d);

  /* ---------- shared message list: the single source of truth ---------- */
  const seed = () => {
    const at = (h, m) => new Date(2026, 8, 25, h, m);
    const msg = (dir, text, h, m, read) => ({ id: nextId++, dir, text, ts: at(h, m), read });
    messages = [
      msg("in", "Signal weak here", 10, 30, true),
      msg("out", "Requesting confirmation", 10, 33, true),
      msg("out", "Team moving now", 10, 35, true),
      msg("in", "Reached the location", 10, 38, true),
      msg("out", "All clear at base", 10, 40, true),
      msg(
        "in",
        "Water rising near the school. Around forty people on the upper floor, two need medical attention. Roads on the east side are cut off.",
        10, 42, false
      ),
      msg("in", "Six people at the temple", 10, 43, false),
      msg("in", "Need medical supplies", 10, 44, false),
    ];
  };

  // Unread badge = unread incoming messages, never messages.length.
  const unreadCount = () => messages.filter((m) => m.dir === "in" && !m.read).length;

  const renderBadge = () => {
    const n = unreadCount();
    badge.hidden = n === 0;
    badge.textContent = String(n);
  };

  /* ---------- navigation ---------- */
  const go = (name) => {
    if (name === current) return;
    if (current === "hf") stopHandsFree();
    if (current === "main") resetMain();
    current = name;
    Object.entries(screens).forEach(([key, el]) => el.classList.toggle("is-active", key === name));
    if (name === "main") renderBadge();
    if (name === "logs") renderLogs();
    if (name === "hf") startHandsFree();
  };

  const openEditor = (text) => {
    editorInput.value = text;
    syncSend();
    go("editor");
    if (!text) later(() => editorInput.focus({ preventScroll: true }), 320);
  };

  /* ---------- 1. Main: push-to-talk ---------- */
  const setMain = (state) => {
    screens.main.dataset.state = state;
    const copy = {
      idle: ["Idle", ""],
      recording: ["Recording…", "Speak now · release when finished"],
      transcribing: ["Transcribing…", "Preparing your message"],
      failed: ["Couldn't transcribe", "Try again, or type it instead"],
    }[state];
    mainStatus.textContent = copy[0];
    mainHint.textContent = copy[1];
    mainFail.hidden = state !== "failed";
  };
  const resetMain = () => {
    clearTimers();
    setMain("idle");
  };

  const beginTalking = (event) => {
    const state = screens.main.dataset.state;
    if (state !== "idle" && state !== "failed") return;
    event.preventDefault();
    setMain("recording");
    vibrate(30);
    if (event.pointerId !== undefined) {
      try {
        ptt.setPointerCapture(event.pointerId);
      } catch (_) {
        /* optional */
      }
    }
  };

  const finishTalking = (event) => {
    if (screens.main.dataset.state !== "recording") return;
    if (event) event.preventDefault();
    vibrate(12);
    setMain("transcribing");
    later(() => resolveTranscript("main"), 1300);
  };

  ptt.addEventListener("pointerdown", beginTalking);
  ptt.addEventListener("pointerup", finishTalking);
  ptt.addEventListener("pointercancel", finishTalking);
  ptt.addEventListener("lostpointercapture", finishTalking);
  ptt.addEventListener("contextmenu", (e) => e.preventDefault());
  ptt.addEventListener("keydown", (e) => {
    if ((e.key === " " || e.key === "Enter") && !e.repeat) beginTalking(e);
  });
  ptt.addEventListener("keyup", (e) => {
    if (e.key === " " || e.key === "Enter") finishTalking(e);
  });

  // A transcript is a draft for the editor; it is never appended to the message list.
  const resolveTranscript = (from) => {
    if (simFail.checked) {
      if (from === "main") setMain("failed");
      else setHf("failed");
      return;
    }
    const text = TRANSCRIPTS[transcriptIndex++ % TRANSCRIPTS.length];
    openEditor(text);
  };

  /* ---------- 2. Hands-free ---------- */
  const setHf = (state) => {
    screens.hf.dataset.state = state;
    const copy = {
      listening: ["Listening…", "Speak normally"],
      recording: ["Recording…", "We'll stop when you pause"],
      transcribing: ["Transcribing…", "Preparing your message"],
      failed: ["Couldn't transcribe", "Try again, or type it instead"],
    }[state];
    hfStatus.textContent = copy[0];
    hfHint.textContent = copy[1];
    hfFail.hidden = state !== "failed";
    hfDone.disabled = state === "transcribing";
  };

  const startHandsFree = () => {
    clearTimers();
    setHf("listening");
    later(() => setHf("recording"), 2200);
  };
  const stopHandsFree = () => clearTimers();

  const finishHandsFree = () => {
    if (screens.hf.dataset.state === "transcribing") return;
    clearTimers();
    setHf("transcribing");
    later(() => resolveTranscript("hf"), 1400);
  };

  /* ---------- 3. Editor ---------- */
  const syncSend = () => {
    editorSend.disabled = editorInput.value.trim() === "";
  };
  editorInput.addEventListener("input", syncSend);

  const editorBack = () => {
    if (editorInput.value.trim()) discardDialog.hidden = false;
    else go("main");
  };

  const sendMessage = () => {
    const text = editorInput.value.trim();
    if (!text) return;
    // Outgoing messages are already read, so they never touch the unread badge.
    messages.push({ id: nextId++, dir: "out", text, ts: new Date(), read: true });
    editorInput.value = "";
    vibrate(12);
    go("main");
  };

  /* ---------- 4. Logs ---------- */
  const ARROW_IN = '<svg viewBox="0 0 24 24"><path d="M12 5v14m-6-6 6 6 6-6"></path></svg>';
  const ARROW_OUT = '<svg viewBox="0 0 24 24"><path d="M12 19V5m-6 6 6-6 6 6"></path></svg>';
  const CHEVRON = '<svg class="chev" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6"></path></svg>';

  const dirLabel = (m) => (m.dir === "in" ? `${ARROW_IN}Received` : `${ARROW_OUT}Sent`);

  const renderLogs = () => {
    logList.replaceChildren();
    [...messages]
      .sort((a, b) => b.ts - a.ts)
      .forEach((m) => {
        const card = document.createElement("button");
        card.type = "button";
        card.className = "log-card" + (m.dir === "in" && !m.read ? " is-unread" : "");
        card.innerHTML =
          `<div><div class="log-meta"><time></time><span class="dir ${m.dir}">${dirLabel(m)}</span></div>` +
          `<p class="log-preview"></p></div>${CHEVRON}`;
        $("time", card).textContent = fmtTime(m.ts);
        $(".log-preview", card).textContent = m.text;
        card.addEventListener("click", () => openDetail(m.id));
        logList.append(card);
      });
  };

  /* ---------- 5. Detail ---------- */
  const openDetail = (id) => {
    const m = messages.find((x) => x.id === id);
    if (!m) return;
    // Only opening the individual message marks it read; opening Logs does not.
    m.read = true;
    $("#detail-time").textContent = fmtTime(m.ts);
    $("#detail-date").textContent = fmtDate(m.ts);
    const dir = $("#detail-dir");
    dir.className = `dir ${m.dir}`;
    dir.innerHTML = dirLabel(m);
    $("#detail-body").textContent = m.text;
    go("detail");
  };

  /* ---------- wiring ---------- */
  const actions = {
    "hf-back": () => go("main"),
    "hf-done": finishHandsFree,
    "retry-main": () => setMain("idle"),
    "retry-hf": startHandsFree,
    "continue-text": () => openEditor(""),
    "editor-back": editorBack,
    "editor-send": sendMessage,
    "discard-cancel": () => (discardDialog.hidden = true),
    "discard-confirm": () => {
      discardDialog.hidden = true;
      editorInput.value = "";
      go("main");
    },
  };

  document.addEventListener("click", (event) => {
    const actEl = event.target.closest("[data-act]");
    if (actEl && actions[actEl.dataset.act]) {
      actions[actEl.dataset.act]();
      return;
    }
    const goEl = event.target.closest("[data-go]");
    if (!goEl) return;
    const target = goEl.dataset.go;
    if (target === "editor-blank") {
      discardDialog.hidden = true;
      openEditor("");
    } else if (target === "detail") {
      const pick =
        [...messages].sort((a, b) => b.ts - a.ts).find((m) => m.dir === "in" && !m.read) ||
        [...messages].sort((a, b) => b.ts - a.ts)[0];
      openDetail(pick.id);
    } else {
      go(target);
    }
  });

  $("#reset").addEventListener("click", () => {
    seed();
    renderBadge();
    if (current === "logs") renderLogs();
  });

  seed();
  renderBadge();
})();
