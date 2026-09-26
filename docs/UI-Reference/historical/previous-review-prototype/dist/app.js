(() => {
  const $ = (s, root = document) => root.querySelector(s);
  const screens = { main: $("#screen-main"), hf: $("#screen-hf"), editor: $("#screen-editor"), logs: $("#screen-logs"), detail: $("#screen-detail") };
  const ptt = $("#ptt"), editor = $("#editor-input"), send = $("#editor-send"), dialog = $("#discard-dialog");
  const badge = $("#unread-badge"), list = $("#log-list"), language = $("#language-select");
  const voiceResult = $("#voice-result"), sendResult = $("#send-result");
  const speechButton = $("#simulate-speech"), ackButton = $("#ack-pending");
  const key = "itantra-review-v1";
  const samples = {
    en: ["We are eight people on the second floor of the school. Water is above the ground floor and still rising.",
         "Need a boat near the east bridge. Two elderly people cannot walk.",
         "Medical kit needed at the temple. One person has a broken leg."],
    hi: ["हम स्कूल की दूसरी मंज़िल पर हैं। पानी बढ़ रहा है।"],
    gu: ["અમને શાળા પાસે મદદની જરૂર છે."],
    mr: ["आम्हाला शाळेजवळ मदतीची गरज आहे."],
    ta: ["பள்ளிக்கு அருகில் எங்களுக்கு உதவி தேவை."],
    te: ["పాఠశాల దగ్గర మాకు సహాయం కావాలి."],
    or: ["ବିଦ୍ୟାଳୟ ପାଖରେ ଆମକୁ ସାହାଯ୍ୟ ଦରକାର।"],
    bn: ["স্কুলের কাছে আমাদের সাহায্য দরকার।"]
  };
  let current = "main", mainState = "idle", hfState = "listening", selectedId = null;
  let messages = [], nextId = 1, transcriptIndex = 0, flowTimer = null, sendTimer = null;
  let options = { hf: "event", history: "sample", ack: "manual" };
  const ackTimers = new Map();

  function time(d) { return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", hour12: false }).format(d); }
  function date(d) { return new Intl.DateTimeFormat(undefined, { day: "2-digit", month: "short", year: "numeric" }).format(d); }
  function ordered() { return messages.slice().sort((a, b) => new Date(b.ts) - new Date(a.ts) || String(b.id).localeCompare(String(a.id))); }
  function save() {
    try { sessionStorage.setItem(key, JSON.stringify({ messages, nextId, draft: editor.value, language: language.value, options })); } catch (_) {}
  }
  function seed() {
    const today = new Date();
    return [42, 43, 44].map((minute, i) => {
      const at = new Date(today); at.setHours(10, minute, 0, 0);
      return { id: "seed-" + i, dir: "in",
        text: ["Water rising near the school", "Six people at the temple", "Need medical supplies"][i],
        ts: at.toISOString(), read: false, lang: "en" };
    });
  }
  function restore() {
    try {
      const prior = JSON.parse(sessionStorage.getItem(key) || "null");
      if (prior && Array.isArray(prior.messages)) {
        messages = prior.messages; nextId = Number(prior.nextId) || 1;
        editor.value = prior.draft || ""; language.value = prior.language || "en";
        options = Object.assign(options, prior.options || {});
        return;
      }
    } catch (_) {}
    messages = seed();
  }
  function clearFlow() { clearTimeout(flowTimer); flowTimer = null; }
  function unread() { return messages.filter(m => m.dir === "in" && !m.read).length; }
  function renderBadge() { const n = unread(); badge.hidden = !n; badge.textContent = String(n); }
  function renderControls() {
    speechButton.disabled = current !== "hf" || hfState !== "listening" || options.hf !== "event";
    ackButton.disabled = !messages.some(m => m.dir === "out" && m.delivery === "awaiting");
  }
  function direction(m) {
    return m.dir === "in" ? "↓ Received" : "↑ Sent" + (m.delivery ? " · " + (m.delivery === "delivered" ? "Delivered" : "Awaiting ACK") : "");
  }
  function renderLogs() {
    const all = ordered(); list.replaceChildren();
    list.hidden = all.length === 0; $("#logs-empty").hidden = all.length !== 0;
    all.forEach(m => {
      const card = document.createElement("button");
      card.type = "button"; card.className = "log-card" + (m.dir === "in" && !m.read ? " is-unread" : "");
      const body = document.createElement("div"), meta = document.createElement("div");
      const stamp = document.createElement("time"), dir = document.createElement("span"), preview = document.createElement("p");
      meta.className = "log-meta"; stamp.dateTime = m.ts; stamp.textContent = time(new Date(m.ts));
      dir.className = "dir " + m.dir; dir.textContent = direction(m);
      preview.className = "log-preview"; preview.textContent = m.text;
      meta.append(stamp, dir); body.append(meta, preview); card.append(body);
      card.insertAdjacentHTML("beforeend", '<svg class="chev" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6"></path></svg>');
      card.addEventListener("click", () => openDetail(m.id));
      list.append(card);
    });
  }
  function renderDetail() {
    const m = messages.find(x => x.id === selectedId); if (!m) return;
    $("#detail-time").textContent = time(new Date(m.ts));
    $("#detail-date").textContent = date(new Date(m.ts));
    const dir = $("#detail-dir"); dir.className = "dir " + m.dir; dir.textContent = direction(m);
    $("#detail-body").textContent = m.text;
  }
  function openDetail(id) {
    const m = messages.find(x => x.id === id); if (!m) return;
    if (m.dir === "in") m.read = true;
    selectedId = id; save(); renderBadge(); renderDetail(); go("detail");
  }
  function setMain(state, label) {
    mainState = state; screens.main.dataset.state = state;
    const copy = {
      idle: ["Idle", ""], recording: ["Recording…", "Speak now · release when finished"],
      transcribing: ["Transcribing…", "Preparing your message"],
      failed: [label || "Couldn't transcribe", "Try again, or type it instead"]
    }[state];
    $("#main-status").textContent = copy[0]; $("#main-hint").textContent = copy[1];
    $("#main-fail").hidden = state !== "failed";
    language.disabled = state === "recording" || state === "transcribing";
  }
  function setHf(state, label) {
    hfState = state; screens.hf.dataset.state = state;
    const copy = {
      listening: ["Listening…", "Speak normally"],
      recording: ["Recording…", "Tap Done when finished"],
      transcribing: ["Transcribing…", "Preparing your message"],
      failed: [label || "Couldn't transcribe", "Try again, or type it instead"]
    }[state];
    $("#hf-status").textContent = copy[0]; $("#hf-hint").textContent = copy[1];
    $("#hf-fail").hidden = state !== "failed";
    $("#hf-done").disabled = state === "failed" || state === "transcribing";
    renderControls();
  }
  function startHf() {
    clearFlow();
    if (voiceResult.value === "mic") setHf("failed", "Microphone unavailable");
    else setHf(options.hf === "immediate" ? "recording" : "listening");
  }
  function go(target) {
    if (current === target) return;
    if (current === "hf") clearFlow();
    if (current === "main") { clearFlow(); setMain("idle"); }
    current = target;
    Object.entries(screens).forEach(([name, node]) => node.classList.toggle("is-active", name === target));
    if (target === "hf") startHf();
    if (target === "logs") renderLogs();
    if (target === "main") renderBadge();
    if (target === "detail") renderDetail();
    renderControls();
    history.pushState({ screen: target }, "", location.href);
  }
  function syncSend() { send.disabled = !editor.value.trim() || !!sendTimer; save(); }
  function openEditor(text) {
    clearFlow(); editor.value = text; $("#send-error").hidden = true;
    syncSend(); go("editor");
  }
  function resolveVoice(source, outcome) {
    flowTimer = null;
    if (current !== source) return;
    if (outcome !== "success") {
      const reason = outcome === "empty" ? "No speech detected" :
        outcome === "mic" ? "Microphone unavailable" : "Couldn't transcribe";
      if (source === "main") setMain("failed", reason); else setHf("failed", reason);
      return;
    }
    const sample = samples[language.value] || samples.en;
    openEditor(sample[transcriptIndex++ % sample.length]);
  }
  function beginPtt(e) {
    if (current !== "main" || (mainState !== "idle" && mainState !== "failed")) return;
    e.preventDefault();
    if (voiceResult.value === "mic") { setMain("failed", "Microphone unavailable"); return; }
    setMain("recording"); if (navigator.vibrate) navigator.vibrate(30);
    if (e.pointerId !== undefined) { try { ptt.setPointerCapture(e.pointerId); } catch (_) {} }
  }
  function releasePtt(e) {
    if (mainState !== "recording") return;
    e.preventDefault(); if (navigator.vibrate) navigator.vibrate(12);
    setMain("transcribing"); clearFlow();
    const outcome = voiceResult.value;
    flowTimer = setTimeout(() => resolveVoice("main", outcome), 1100);
  }
  function finishHf() {
    if (current !== "hf" || hfState === "failed" || hfState === "transcribing") return;
    setHf("transcribing"); clearFlow();
    const outcome = voiceResult.value;
    flowTimer = setTimeout(() => resolveVoice("hf", outcome), 1100);
  }
  function cancelAcks() { ackTimers.forEach(clearTimeout); ackTimers.clear(); }
  function acknowledge(id) {
    const m = messages.find(x => x.id === id && x.dir === "out" && x.delivery === "awaiting");
    if (!m) return;
    clearTimeout(ackTimers.get(id)); ackTimers.delete(id);
    m.delivery = "delivered"; save(); renderControls();
    if (current === "logs") renderLogs();
    if (current === "detail" && selectedId === id) renderDetail();
  }
  function scheduleAck(id) {
    if (options.ack === "auto") ackTimers.set(id, setTimeout(() => acknowledge(id), 2200));
  }
  function sendMessage() {
    const text = editor.value.trim(); if (!text || sendTimer) return;
    const outcome = sendResult.value;
    $("#send-error").hidden = true; send.textContent = "SENDING…"; send.disabled = true;
    sendTimer = setTimeout(() => {
      sendTimer = null; send.textContent = "Send";
      if (outcome !== "success") {
        const error = $("#send-error");
        error.textContent = outcome === "disconnected"
          ? "Not connected. Connect to another phone and try again. Your draft is still here."
          : "Could not send. Your draft is still here. Try again.";
        error.hidden = false; syncSend(); return;
      }
      const id = nextId++;
      messages.push({ id, dir: "out", text, ts: new Date().toISOString(), read: true, lang: language.value, delivery: "awaiting" });
      editor.value = ""; syncSend(); save();
      if (navigator.vibrate) navigator.vibrate(12);
      go("main"); scheduleAck(id);
    }, 850);
  }
  function back() {
    if (!dialog.hidden) { dialog.hidden = true; return; }
    if (current === "editor") {
      if (editor.value.length) dialog.hidden = false;
      else { editor.value = ""; save(); go("main"); }
    } else if (current === "detail") go("logs");
    else if (current === "logs" || current === "hf") go("main");
  }
  const actions = {
    "hf-back": back, "hf-done": finishHf, "editor-back": back, "editor-send": sendMessage,
    "retry-main": () => setMain("idle"), "retry-hf": startHf,
    "continue-text": () => openEditor(""),
    "discard-cancel": () => { dialog.hidden = true; },
    "discard-confirm": () => { clearTimeout(sendTimer); sendTimer = null; send.textContent = "Send"; dialog.hidden = true; editor.value = ""; save(); go("main"); }
  };
  document.addEventListener("click", e => {
    const action = e.target.closest("[data-act]");
    if (action && actions[action.dataset.act]) { actions[action.dataset.act](); return; }
    const nav = e.target.closest("[data-go]"); if (!nav) return;
    if (nav.dataset.go === "editor-blank") openEditor("");
    else go(nav.dataset.go);
  });
  ptt.addEventListener("pointerdown", beginPtt);
  ptt.addEventListener("pointerup", releasePtt);
  ptt.addEventListener("pointercancel", () => { if (mainState === "recording") setMain("idle"); });
  ptt.addEventListener("lostpointercapture", () => { if (mainState === "recording") setMain("idle"); });
  ptt.addEventListener("contextmenu", e => e.preventDefault());
  ptt.addEventListener("keydown", e => { if ((e.key === " " || e.key === "Enter") && !e.repeat) beginPtt(e); });
  ptt.addEventListener("keyup", e => { if (e.key === " " || e.key === "Enter") releasePtt(e); });
  editor.addEventListener("input", syncSend);
  language.addEventListener("change", save);
  speechButton.addEventListener("click", () => { if (current === "hf" && hfState === "listening") setHf("recording"); });
  ackButton.addEventListener("click", () => {
    const m = ordered().find(x => x.dir === "out" && x.delivery === "awaiting");
    if (m) acknowledge(m.id);
  });
  function receiveMessage(text) {
    text = String(text || "").trim();
    if (!text) return false;
    messages.push({ id: nextId++, dir: "in", text, ts: new Date().toISOString(), read: false, lang: "en" });
    save(); renderBadge(); if (current === "logs") renderLogs();
    return true;
  }
  $("#receive-message").addEventListener("click", () => {
    if (!receiveMessage($("#incoming-text").value)) $("#incoming-text").focus();
  });
  $("#reset").addEventListener("click", () => {
    clearFlow(); clearTimeout(sendTimer); sendTimer = null; cancelAcks();
    messages = options.history === "sample" ? seed() : [];
    nextId = 1; transcriptIndex = 0; editor.value = ""; language.value = "en";
    dialog.hidden = true; $("#send-error").hidden = true; send.textContent = "Send";
    syncSend(); save(); if (current !== "main") go("main");
    setMain("idle"); renderBadge(); renderControls();
  });
  document.querySelectorAll('input[name="hf-mode"]').forEach(r => r.addEventListener("change", () => {
    options.hf = r.value; if (current === "hf") startHf(); save();
  }));
  document.querySelectorAll('input[name="history-mode"]').forEach(r => r.addEventListener("change", () => {
    options.history = r.value;
    clearFlow(); clearTimeout(sendTimer); sendTimer = null; cancelAcks();
    messages = r.value === "sample" ? seed() : [];
    nextId = 1; transcriptIndex = 0; editor.value = ""; language.value = "en";
    dialog.hidden = true; $("#send-error").hidden = true; send.textContent = "Send";
    syncSend(); save(); if (current !== "main") go("main");
    setMain("idle"); renderBadge(); renderControls();
  }));
  document.querySelectorAll('input[name="ack-mode"]').forEach(r => r.addEventListener("change", () => {
    options.ack = r.value; cancelAcks();
    if (r.value === "auto") messages.filter(m => m.dir === "out" && m.delivery === "awaiting").forEach(m => scheduleAck(m.id));
    save();
  }));
  document.addEventListener("keydown", e => {
    if (e.key === "Escape" || (e.key === "Backspace" && !["TEXTAREA", "INPUT", "SELECT"].includes(document.activeElement?.tagName))) {
      e.preventDefault(); back();
    }
  });
  history.pushState({ screen: "main" }, "", location.href);
  window.addEventListener("popstate", () => { back(); history.pushState({ screen: current }, "", location.href); });

  restore();
  Object.entries(options).forEach(([name, value]) => {
    const group = name === "hf" ? "hf-mode" : name === "history" ? "history-mode" : "ack-mode";
    const r = document.querySelector('input[name="' + group + '"][value="' + value + '"]');
    if (r) r.checked = true;
  });
  $(".statusbar span").textContent = time(new Date());
  setMain("idle"); setHf("listening"); renderBadge(); renderControls(); syncSend(); save();
  if (options.ack === "auto") messages.filter(m => m.dir === "out" && m.delivery === "awaiting").forEach(m => scheduleAck(m.id));
  if (document.modelContext?.registerTool) {
    document.modelContext.registerTool({
      name: "inspect_itantra_review",
      description: "Read the current screen, confirmed messages, unread count, draft, and simulator choices in this local iTantra review.",
      inputSchema: { type: "object", properties: {} },
      execute: () => ({ screen: current, unreadCount: unread(), draft: editor.value, messages: ordered(), options }),
      annotations: { readOnlyHint: true, untrustedContentHint: true }
    });
    document.modelContext.registerTool({
      name: "simulate_itantra_receive",
      description: "Add a simulated incoming message to the same history shown by Main and Logs.",
      inputSchema: { type: "object", properties: { text: { type: "string", description: "Incoming message text" } }, required: ["text"] },
      execute: ({ text }) => ({ added: receiveMessage(text), unreadCount: unread() })
    });
  }
})();
