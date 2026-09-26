(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const languages = [
    ["en", "English"], ["hi", "Hindi · हिन्दी"], ["gu", "Gujarati · ગુજરાતી"],
    ["mr", "Marathi · मराठी"], ["kn", "Kannada · ಕನ್ನಡ"], ["ml", "Malayalam · മലയാളം"],
    ["ta", "Tamil · தமிழ்"], ["te", "Telugu · తెలుగు"], ["or", "Odia · ଓଡ଼ିଆ"], ["bn", "Bengali · বাংলা"]
  ];
  const sampleSpeech = {
    en: "I am safe at the school shelter. Please meet me there when you can.",
    hi: "मैं स्कूल के आश्रय स्थल पर सुरक्षित हूँ।", gu: "હું શાળાના આશ્રયસ્થાને સુરક્ષિત છું.",
    mr: "मी शाळेच्या निवाऱ्यात सुरक्षित आहे.", kn: "ನಾನು ಶಾಲೆಯ ಆಶ್ರಯದಲ್ಲಿ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇನೆ.",
    ml: "ഞാൻ സ്കൂൾ അഭയകേന്ദ്രത്തിൽ സുരക്ഷിതനാണ്.", ta: "நான் பள்ளி முகாமில் பாதுகாப்பாக இருக்கிறேன்.",
    te: "నేను పాఠశాల ఆశ్రయంలో సురక్షితంగా ఉన్నాను.", or: "ମୁଁ ବିଦ୍ୟାଳୟ ଆଶ୍ରୟସ୍ଥଳରେ ସୁରକ୍ଷିତ ଅଛି।",
    bn: "আমি স্কুলের আশ্রয়কেন্দ্রে নিরাপদে আছি।"
  };
  const STORAGE_KEY = "itantra-ideal-review-v1";
  const fresh = () => ({
    ready: false,
    permissions: { nearby: false, notifications: false, microphone: false, camera: false },
    identityVersion: 1, identityLost: false,
    contacts: [], messages: [], nextId: 1,
    draft: { text: "", recipient: "", language: "en", urgent: false },
    emergency: { active: false, available: false }, readiness: "ready",
    sos: { status: "idle", role: "requester", category: "Medical", language: "en", note: "", route: "direct", trust: "unverified", responder: "", messages: [], notice: "" },
    offers: [], selectedContact: null, selectedMessage: null, selectedOffer: null,
    lastReceivedId: null
  });
  let state = fresh();
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
    if (saved && typeof saved === "object") state = {
      ...fresh(), ...saved,
      permissions: { ...fresh().permissions, ...saved.permissions },
      emergency: { ...fresh().emergency, ...saved.emergency },
      draft: { ...fresh().draft, ...saved.draft },
      sos: { ...fresh().sos, ...saved.sos }
    };
  } catch (_) {}
  let current = state.ready ? "home" : "onboarding";
  let stack = [], captureState = "idle", handsfreeState = "listening";
  let flowTimer = null, speechTimer = null, searchTimer = null, offerTimer = null;
  let dialogAction = null, dialogCancelAction = null, dialogLastFocus = null;
  const screens = Object.fromEntries($$(".screen").map(el => [el.id.replace("screen-", ""), el]));

  function save() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch (_) {}
  }
  function now() { return new Date().toISOString(); }
  function stamp(value) { return new Intl.DateTimeFormat(undefined, { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
  function languageName(code) { return languages.find(([id]) => id === code)?.[1] || code; }
  function safeText(node, text) { node.textContent = text; }
  function announce(text) { $("#sim-feedback").textContent = text; }
  function clearTimers() { clearTimeout(flowTimer); clearTimeout(speechTimer); clearTimeout(searchTimer); clearTimeout(offerTimer); }
  function populateLanguages() {
    for (const id of ["home-language", "compose-language", "sos-language"]) {
      const select = $("#" + id);
      select.replaceChildren(...languages.map(([code, name]) => {
        const option = document.createElement("option"); option.value = code; option.textContent = name; return option;
      }));
    }
  }
  function showDialog(title, body, confirmLabel, action, cancelLabel = "Cancel", cancelAction = null) {
    dialogLastFocus = document.activeElement;
    $("#dialog-title").textContent = title; $("#dialog-body").textContent = body;
    $("#dialog-confirm").textContent = confirmLabel; $("#dialog-cancel").textContent = cancelLabel;
    $("#dialog-input").hidden = true;
    dialogAction = action; dialogCancelAction = cancelAction;
    $("#dialog-scrim").hidden = false; $("#dialog-cancel").focus();
  }
  function closeDialog(confirmed) {
    $("#dialog-scrim").hidden = true;
    const action = confirmed ? dialogAction : dialogCancelAction;
    dialogAction = dialogCancelAction = null;
    if (action) action();
    if (dialogLastFocus?.isConnected) dialogLastFocus.focus();
  }
  $("#dialog-confirm").addEventListener("click", () => closeDialog(true));
  $("#dialog-cancel").addEventListener("click", () => closeDialog(false));

  function go(target, push = true) {
    if (!screens[target] || target === current) return;
    if (push) stack.push(current);
    current = target;
    Object.entries(screens).forEach(([name, el]) => {
      el.classList.toggle("is-active", name === target);
      el.classList.toggle("has-nav", ["home", "contacts", "logs", "emergency", "settings"].includes(name));
      if (name === target) { const scroll = $(".scroll-content", el); if (scroll) scroll.scrollTop = 0; }
    });
    $("#bottom-nav").hidden = !["home", "contacts", "logs", "emergency", "settings"].includes(target);
    $$("#bottom-nav button").forEach(button => button.classList.toggle("is-active", button.dataset.go === target));
    if (target === "handsfree") startHandsfree();
    render();
    try { history.pushState({ screen: target }, "", location.href); } catch (_) {}
  }
  function goRoot(target) { stack = []; go(target, false); }
  function back() {
    if (!$("#dialog-scrim").hidden) { closeDialog(false); return; }
    if (current === "compose" && state.draft.text.length) {
      showDialog("Discard this message?", "Your draft will be lost if you go back now.", "Discard", () => {
        state.draft.text = ""; state.draft.urgent = false; save(); $("#editor-input").value = "";
        stack = []; go("home", false);
      });
      return;
    }
    if (current === "sos-search") {
      showDialog("Leave this SOS search?", "The request is still active. You can return from Logs or cancel it here.", "Stay here", () => {}, "Cancel SOS", () => cancelSos());
      return;
    }
    if (current === "sos-session") {
      showDialog("Leave the SOS conversation?", "The session stays active until you end it. You can return from Logs.", "Stay here", () => {}, "Go to Logs", () => goRoot("logs"));
      return;
    }
    if (current === "handsfree") { clearTimeout(flowTimer); handsfreeState = "listening"; }
    go(stack.pop() || "home", false);
  }
  document.addEventListener("click", event => {
    const action = event.target.closest("[data-act]");
    if (action?.dataset.act === "back") { back(); return; }
    const nav = event.target.closest("[data-go]");
    if (!nav) return;
    const target = nav.dataset.go;
    if (target === "compose-blank") { openCompose(""); return; }
    if (["home", "contacts", "logs", "emergency"].includes(target) && nav.closest("#bottom-nav")) { goRoot(target); return; }
    go(target);
  });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && current !== "onboarding") { event.preventDefault(); back(); }
  });
  window.addEventListener("popstate", () => { back(); history.pushState({ screen: current }, "", location.href); });

  function permissionText(key) { return state.permissions[key] ? "Allowed" : "Allow"; }
  function readinessInfo() {
    if (!state.permissions.nearby || state.readiness === "permission") return ["Nearby permission required", "Messages remain saved, but this phone cannot discover or relay until permission is allowed.", true];
    if (state.readiness === "bluetooth") return ["Bluetooth is off", "Messages can queue on this phone. Turn Bluetooth on to discover nearby iTantra phones.", true];
    if (state.readiness === "background") return ["Background participation stopped", "Open Emergency Mode to restore participation. Queued messages remain saved.", true];
    if (state.readiness === "battery") return ["Battery critically low", "Network participation may stop soon. Keep the phone charged if possible.", true];
    if (state.readiness === "notifications" || !state.permissions.notifications) return ["Notifications limited", "Messages still work, but incoming alerts may be missed while the app is closed.", true];
    return ["Local radio ready", "Internet is not needed. Messages may wait for a route.", false];
  }
  function renderReadiness() {
    const [title, copy, blocked] = readinessInfo();
    $("#readiness-title").textContent = title; $("#readiness-copy").textContent = copy;
    $("#readiness-banner").classList.toggle("is-blocked", blocked);
    ["nearby", "notifications", "microphone", "camera"].forEach(key => {
      const value = $("#permission-" + key); value.textContent = permissionText(key);
      value.closest(".permission-row").classList.toggle("is-allowed", state.permissions[key]);
    });
    const rows = [
      ["Local identity", state.identityLost ? "New identity" : "Ready", state.identityLost ? "Contacts need a new QR exchange after reinstall." : "This installation can be recognized by trusted contacts."],
      ["Nearby radio", blocked && ["bluetooth", "permission"].includes(state.readiness) || !state.permissions.nearby ? "Action needed" : "Ready", copy],
      ["Notifications", state.permissions.notifications && state.readiness !== "notifications" ? "Ready" : "Limited", "SOS and message alerts may be missed while closed."],
      ["Voice input", state.permissions.microphone ? "Ready" : "Permission needed", "Typing remains available."],
      ["Camera for QR", state.permissions.camera ? "Ready" : "Permission needed", "Needed only to scan a trusted contact."],
      ["Emergency background work", state.emergency.active && state.readiness !== "background" ? "Active" : "Off or limited", state.readiness === "background" ? "Android stopped background participation. Restart Emergency Mode." : "A visible notification may be required when active."]
    ];
    const list = $("#readiness-list"); list.replaceChildren();
    rows.forEach(([name, status, detail]) => {
      const card = document.createElement("div"); card.className = "readiness-row" + (["Action needed", "Permission needed", "Limited", "New identity"].includes(status) ? " is-blocked" : "");
      const strong = document.createElement("strong"), stateLabel = document.createElement("span"), small = document.createElement("small");
      strong.textContent = name; stateLabel.className = "state-label"; stateLabel.textContent = status; small.textContent = detail;
      card.append(strong, stateLabel, small); list.append(card);
    });
    $("#identity-lost").hidden = !state.identityLost;
    $("#onboard-kicker").textContent = state.ready ? "PERMISSIONS" : "FIRST LAUNCH";
    $("#onboard-title").textContent = state.ready ? "Make this phone ready." : "Ready before you need it.";
    $("#finish-onboarding").textContent = state.ready ? "Return to iTantra" : "Enter iTantra";
    const recovery = $("#readiness-action");
    recovery.hidden = !["bluetooth", "background"].includes(state.readiness);
    recovery.textContent = state.readiness === "bluetooth" ? "Turn Bluetooth on (simulate)" : "Restart local participation (simulate)";
  }
  $$("[data-permission]").forEach(button => button.addEventListener("click", () => {
    state.permissions[button.dataset.permission] = !state.permissions[button.dataset.permission];
    if (button.dataset.permission === "nearby" && state.permissions.nearby && state.readiness === "permission") state.readiness = "ready";
    save(); renderReadiness();
  }));
  $("#finish-onboarding").addEventListener("click", () => {
    state.ready = true; save(); goRoot("home");
  });
  $("#readiness-action").addEventListener("click", () => {
    state.readiness = "ready"; save(); render();
  });

  function setCapture(stateName, text = "") {
    captureState = stateName; screens.home.dataset.state = stateName;
    const copy = {
      idle: ["Ready to speak", "Release to review your words before sending."],
      recording: ["Recording…", "Speak now. Release when finished."],
      transcribing: ["Transcribing…", "Preparing an editable draft."],
      failed: [text || "Couldn't transcribe", "Try again or continue as text."]
    }[stateName];
    $("#main-status").textContent = copy[0]; $("#main-hint").textContent = copy[1];
    $("#main-fail").hidden = stateName !== "failed";
    $("#home-language").disabled = stateName === "recording" || stateName === "transcribing";
  }
  function beginPtt(event) {
    if (current !== "home" || !["idle", "failed"].includes(captureState)) return;
    event.preventDefault();
    if (!state.permissions.microphone || $("#voice-result").value === "mic") { setCapture("failed", "Microphone unavailable"); return; }
    setCapture("recording"); $("#ptt").setAttribute("aria-pressed", "true");
    if (event.pointerId !== undefined) try { $("#ptt").setPointerCapture(event.pointerId); } catch (_) {}
  }
  function endPtt(event) {
    if (captureState !== "recording") return;
    event.preventDefault(); $("#ptt").setAttribute("aria-pressed", "false"); setCapture("transcribing");
    clearTimeout(flowTimer);
    flowTimer = setTimeout(() => resolveVoice("home"), 900);
  }
  function resolveVoice(source) {
    flowTimer = null; if (current !== source) return;
    const outcome = $("#voice-result").value;
    if (outcome !== "success") {
      const reason = outcome === "empty" ? "No speech detected" : outcome === "mic" ? "Microphone unavailable" : "Couldn't transcribe";
      if (source === "home") setCapture("failed", reason); else setHandsfree("failed", reason);
      return;
    }
    const selected = source === "home" ? $("#home-language").value : state.draft.language;
    state.draft.language = selected; openCompose(sampleSpeech[selected] || sampleSpeech.en);
    setCapture("idle");
  }
  const ptt = $("#ptt");
  ptt.addEventListener("pointerdown", beginPtt); ptt.addEventListener("pointerup", endPtt);
  ptt.addEventListener("pointercancel", () => { if (captureState === "recording") setCapture("idle"); });
  ptt.addEventListener("lostpointercapture", () => { if (captureState === "recording") setCapture("idle"); });
  ptt.addEventListener("contextmenu", event => event.preventDefault());
  ptt.addEventListener("keydown", event => { if ([" ", "Enter"].includes(event.key) && !event.repeat) beginPtt(event); });
  ptt.addEventListener("keyup", event => { if ([" ", "Enter"].includes(event.key)) endPtt(event); });
  $("#retry-main").addEventListener("click", () => setCapture("idle"));
  $("#home-language").addEventListener("change", event => { state.draft.language = event.target.value; save(); });

  function setHandsfree(status, label = "") {
    handsfreeState = status; screens.handsfree.dataset.state = status;
    const copy = {
      listening: ["Listening…", "Speak normally. Tap Done when finished."],
      recording: ["Recording…", "Tap Done when finished."],
      transcribing: ["Transcribing…", "Preparing an editable draft."],
      failed: [label || "Couldn't transcribe", "Try again or continue as text."]
    }[status];
    $("#hf-status").textContent = copy[0]; $("#hf-hint").textContent = copy[1];
    $("#hf-fail").hidden = status !== "failed";
    $("#hf-done").disabled = ["failed", "transcribing"].includes(status);
  }
  function startHandsfree() {
    setHandsfree(!state.permissions.microphone || $("#voice-result").value === "mic" ? "failed" : "listening", "Microphone unavailable");
  }
  $("#simulate-hf-speech").addEventListener("click", () => {
    if (current === "handsfree" && handsfreeState === "listening") setHandsfree("recording");
  });
  $("#retry-hf").addEventListener("click", startHandsfree);
  $("#hf-done").addEventListener("click", () => {
    if (current !== "handsfree" || ["failed", "transcribing"].includes(handsfreeState)) return;
    setHandsfree("transcribing"); clearTimeout(flowTimer); flowTimer = setTimeout(() => resolveVoice("handsfree"), 900);
  });

  function validContacts() { return state.contacts.filter(c => c.valid); }
  function renderRecipients() {
    const select = $("#recipient-select"); const currentValue = state.draft.recipient;
    select.replaceChildren();
    const placeholder = document.createElement("option"); placeholder.value = ""; placeholder.textContent = "Choose a trusted contact"; select.append(placeholder);
    validContacts().forEach(contact => { const option = document.createElement("option"); option.value = contact.id; option.textContent = contact.name; select.append(option); });
    select.value = validContacts().some(c => c.id === currentValue) ? currentValue : "";
    if (select.value !== currentValue) { state.draft.recipient = select.value; save(); }
  }
  function syncCompose() {
    $("#editor-input").value = state.draft.text;
    $("#compose-language").value = state.draft.language;
    $("#urgent-toggle").checked = state.draft.urgent;
    renderRecipients();
    const len = [...state.draft.text].length;
    $("#text-length").textContent = `${len} / 500`;
    $("#text-length").classList.toggle("over", len > 500);
    $("#editor-send").disabled = !state.draft.text.trim() || !state.draft.recipient || len > 500;
  }
  function openCompose(text, recipient = null) {
    if (recipient !== null) state.draft.recipient = recipient;
    state.draft.text = text;
    $("#send-error").hidden = true; save(); syncCompose(); go("compose");
  }
  $("#editor-input").addEventListener("input", event => { state.draft.text = event.target.value; save(); syncCompose(); });
  $("#recipient-select").addEventListener("change", event => { state.draft.recipient = event.target.value; save(); syncCompose(); });
  $("#compose-language").addEventListener("change", event => { state.draft.language = event.target.value; save(); syncCompose(); });
  $("#urgent-toggle").addEventListener("change", event => { state.draft.urgent = event.target.checked; save(); });
  function showSendError(message) { const el = $("#send-error"); el.textContent = message; el.hidden = false; }
  $("#editor-send").addEventListener("click", () => {
    const recipient = state.contacts.find(c => c.id === state.draft.recipient && c.valid);
    if (!recipient) { showSendError("Choose one trusted contact. Nearby device names are not recipients."); return; }
    const text = state.draft.text.trim(); if (!text) return;
    if ([...text].length > 500) { showSendError("Message is too long. Shorten it before sending; nothing was removed."); return; }
    if ($("#save-result").value !== "success") { showSendError("Could not safely save this message on your phone. Your draft is still here. Free space or try again."); return; }
    state.messages.push({ id: `m${state.nextId++}`, direction: "out", contactId: recipient.id, contactName: recipient.name, text, language: state.draft.language, urgent: state.draft.urgent, state: "queued", time: now(), read: true });
    state.draft = { text: "", recipient: "", language: state.draft.language, urgent: false };
    save(); syncCompose(); stack = []; go("home", false);
  });

  function renderContacts() {
    const list = $("#contact-list"); list.replaceChildren();
    $("#contacts-empty").hidden = state.contacts.length > 0;
    state.contacts.forEach(contact => {
      const button = document.createElement("button"); button.type = "button"; button.className = "contact-card";
      const top = document.createElement("div"); top.className = "contact-card-top";
      const name = document.createElement("strong"); name.textContent = contact.name;
      const label = document.createElement("span"); label.className = "trust-label"; label.textContent = contact.valid ? "Trusted" : "Re-exchange QR";
      const arrow = document.createElement("span"); arrow.className = "chevron"; arrow.textContent = "›";
      top.append(name, label, arrow);
      const detail = document.createElement("small"); detail.textContent = contact.valid ? "Identity confirmed by QR · reachable later" : "Identity changed or lost · sending blocked";
      button.append(top, detail); button.addEventListener("click", () => { state.selectedContact = contact.id; save(); go("contact-detail"); });
      list.append(button);
    });
    const contact = state.contacts.find(c => c.id === state.selectedContact);
    if (contact) {
      $("#contact-name").textContent = contact.name;
      $("#contact-fingerprint").textContent = `Identity check: ${contact.fingerprint}`;
      $("#message-contact").disabled = !contact.valid;
      $("#contact-detail-title").textContent = contact.name;
    }
  }
  $("#message-contact").addEventListener("click", () => openCompose("", state.selectedContact));
  $("#rename-contact").addEventListener("click", () => {
    const contact = state.contacts.find(c => c.id === state.selectedContact); if (!contact) return;
    showDialog("Rename local label", "This changes only the name on your phone. The trusted identity stays the same.", "Rename", () => {
      const name = $("#dialog-input").value.trim();
      if (name) { contact.name = name.slice(0, 40); save(); renderContacts(); }
    });
    $("#dialog-input").hidden = false;
    $("#dialog-input").value = contact.name;
    $("#dialog-input").focus();
  });
  $("#forget-contact").addEventListener("click", () => {
    const contact = state.contacts.find(c => c.id === state.selectedContact); if (!contact) return;
    showDialog("Forget this trusted contact?", "You will need to exchange QR identities again before sending new private messages to this person.", "Forget contact", () => {
      state.contacts = state.contacts.filter(c => c.id !== contact.id);
      if (state.draft.recipient === contact.id) state.draft.recipient = "";
      state.selectedContact = null; save(); go("contacts");
    });
  });
  function scanCase() {
    if (!state.permissions.camera || $("#qr-result").value === "camera") {
      $("#scan-notice").textContent = "Camera unavailable. Allow camera access in readiness, then try again."; return;
    }
    const result = $("#qr-result").value;
    if (result === "malformed") { $("#scan-notice").textContent = "This QR could not be read or validated. Ask for a current iTantra identity QR."; return; }
    if (result === "own") { $("#scan-notice").textContent = "That is your own identity. Ask the other person to show their QR."; return; }
    if (result === "duplicate") { $("#scan-notice").textContent = state.contacts.length ? "This identity is already trusted. Open it in Contacts instead of adding a duplicate." : "No contact is trusted yet. Choose New identity to review the QR flow."; return; }
    const changed = result === "changed";
    const sameName = result === "same-name";
    const existing = state.contacts.find(c => c.id === state.selectedContact) || state.contacts[0];
    if (changed && !existing) { $("#scan-notice").textContent = "There is no existing contact to re-trust. Choose New identity first."; return; }
    const shownName = changed ? existing.name : "Rahul";
    $("#candidate-name").textContent = shownName;
    $("#candidate-fingerprint").textContent = `Identity check: ${changed ? "C81F · 20B7" : sameName ? "A40D · 996C" : "62AB · 7D91"}`;
    $("#candidate-warning").textContent = changed ? `This differs from ${shownName}'s trusted identity. Do not replace it based on the name. Compare the new check with that person before re-trusting.` : sameName ? "A trusted contact already uses this name, but this is a different identity. Confirm the person and choose a distinct local name." : "The name is only a hint. Compare this check with the other person's phone before adding them.";
    $("#candidate-local-name").value = sameName ? "Rahul (new)" : shownName;
    $("#contact-confirm-check").checked = false; $("#add-contact").disabled = true;
    state.pendingCandidate = { fingerprint: changed ? "C81F · 20B7" : sameName ? "A40D · 996C" : "62AB · 7D91", changed, sameName, replaceContactId: changed ? existing.id : null };
    go("confirm-contact");
  }
  $("#scan-qr").addEventListener("click", scanCase);
  $("#contact-confirm-check").addEventListener("change", event => { $("#add-contact").disabled = !event.target.checked; });
  $("#add-contact").addEventListener("click", () => {
    if (!$("#contact-confirm-check").checked || !state.pendingCandidate) return;
    const name = $("#candidate-local-name").value.trim();
    if (!name) { $("#candidate-local-name").focus(); return; }
    const candidate = state.pendingCandidate;
    if (candidate.changed) {
      showDialog("Replace this trusted identity?", "Only continue if you compared the new identity with this person. Old messages remain in Logs; new messages use the new identity.", "Re-trust contact", () => addCandidate(name, candidate));
    } else addCandidate(name, candidate);
  });
  function addCandidate(name, candidate) {
    if (candidate.changed) state.contacts = state.contacts.filter(c => c.id !== candidate.replaceContactId);
    const duplicate = state.contacts.find(c => c.fingerprint === candidate.fingerprint);
    if (duplicate) { $("#scan-notice").textContent = "This identity is already trusted."; go("contacts"); return; }
    const contact = { id: `c${state.nextId++}`, name: name.slice(0, 40), fingerprint: candidate.fingerprint, valid: true, added: now() };
    state.contacts.push(contact); state.pendingCandidate = null; state.selectedContact = contact.id; save(); stack = []; go("contacts", false);
  }

  const explanations = {
    queued: ["Queued", "Stored on this phone. No relay has confirmed carrying a copy yet. The recipient need not be nearby."],
    relayed: ["Relayed", "Another phone carries an encrypted copy. The intended recipient has not confirmed receiving it."],
    delivered: ["Delivered", "The trusted recipient confirmed delivery. This does not mean they opened or read it."],
    expired: ["Expired", "Its allowed lifetime ended without confirmed delivery. The app does not know why."],
    unknown: ["Unknown", "State continuity was lost. iTantra cannot safely claim whether the recipient received it."]
  };
  function unreadCount() { return state.messages.filter(m => m.direction === "in" && !m.read).length; }
  function renderLogs() {
    const list = $("#log-list"); list.replaceChildren();
    const sorted = [...state.messages].sort((a, b) => new Date(b.time) - new Date(a.time) || String(b.id).localeCompare(String(a.id)));
    $("#logs-empty").hidden = sorted.length > 0;
    sorted.forEach(message => {
      const card = document.createElement("button"); card.type = "button";
      card.className = "log-card" + (message.direction === "in" && !message.read ? " is-unread" : "") + (message.urgent ? " is-urgent" : "");
      const head = document.createElement("div"); head.className = "log-card-top";
      const title = document.createElement("strong"); title.textContent = message.direction === "in" ? `↓ Received · ${message.contactName}` : `↑ To ${message.contactName}`;
      const arrow = document.createElement("span"); arrow.className = "chevron"; arrow.textContent = "›"; head.append(title, arrow);
      const text = document.createElement("p"); text.textContent = message.text;
      const meta = document.createElement("small"); meta.textContent = `${stamp(message.time)} · ${message.urgent ? "Urgent · " : ""}${languageName(message.language)}`;
      card.append(head, text, meta);
      if (message.direction === "out") { const status = document.createElement("span"); status.className = "state"; status.textContent = explanations[message.state]?.[0] || "Unknown"; card.append(status); }
      card.addEventListener("click", () => { state.selectedMessage = message.id; if (message.direction === "in") message.read = true; save(); go("detail"); });
      list.append(card);
    });
    const badge = $("#unread-badge"); badge.hidden = unreadCount() === 0; badge.textContent = String(unreadCount());
    const waiting = state.messages.filter(m => m.direction === "out" && ["queued", "relayed"].includes(m.state)).length;
    $("#home-summary").textContent = waiting ? `${waiting} outgoing message${waiting === 1 ? "" : "s"} still moving through the network.` : "No outgoing messages waiting.";
    const message = state.messages.find(m => m.id === state.selectedMessage);
    if (message) {
      $("#detail-direction").textContent = message.direction === "in" ? "↓ Received" : "↑ Sent";
      $("#detail-time").textContent = stamp(message.time);
      $("#detail-person").textContent = message.contactName;
      $("#detail-body").textContent = message.text;
      $("#detail-language").textContent = `${languageName(message.language)}${message.urgent ? " · Urgent message" : ""}`;
      const explanation = $("#detail-state");
      if (message.direction === "out") { explanation.hidden = false; explanation.replaceChildren(); const strong = document.createElement("strong"), p = document.createElement("p"); [strong.textContent, p.textContent] = explanations[message.state] || explanations.unknown; explanation.append(strong, p); }
      else explanation.hidden = true;
      $("#tts-controls").hidden = message.direction !== "in";
      if (message.direction === "in") $("#tts-status").textContent = "Text is saved even if speech is unavailable.";
    }
  }
  function latestOutgoing() { return [...state.messages].reverse().find(m => m.direction === "out"); }
  $$("[data-message-state]").forEach(button => button.addEventListener("click", () => {
    const message = latestOutgoing(); if (!message) { announce("Send a trusted message first. It will enter Logs as Queued."); return; }
    const target = button.dataset.messageState;
    if (message.state === "delivered" && target !== "delivered") { announce("This message already has a recipient-confirmed delivery receipt."); return; }
    if (target === "relayed" && message.state !== "queued") { announce("A relay can accept a currently Queued message."); return; }
    if (target === "delivered" && !["queued", "relayed", "unknown"].includes(message.state)) { announce("That message is no longer waiting for delivery."); return; }
    if (target === "delivered" && !state.contacts.some(c => c.id === message.contactId && c.valid)) { announce("Recipient identity is no longer trusted. Delivery cannot be asserted from an untrusted claim."); return; }
    if (["expired", "unknown"].includes(target) && !["queued", "relayed"].includes(message.state)) { announce("Choose a message that is still Queued or Relayed."); return; }
    message.state = target; save(); renderLogs(); announce(`Latest outgoing message is now ${explanations[target][0]}.`);
  }));
  function receiveTrusted(duplicate = false) {
    if (duplicate && state.lastReceivedId) { renderLogs(); announce("Duplicate copy ignored. No second row, unread count, or speech event was created."); return; }
    const text = $("#incoming-text").value.trim(); if (!text) return;
    const contact = validContacts()[0];
    if (!contact) { announce("Add a trusted contact by QR before simulating a private incoming message."); return; }
    const message = { id: `m${state.nextId++}`, direction: "in", contactId: contact.id, contactName: contact.name, text, language: state.draft.language, urgent: false, time: now(), read: false };
    state.messages.push(message); state.lastReceivedId = message.id; save(); renderLogs(); announce("Trusted message stored once. Open its row in Logs to mark it read or simulate speech playback.");
  }
  $("#simulate-receive").addEventListener("click", () => receiveTrusted(false));
  $("#simulate-duplicate").addEventListener("click", () => receiveTrusted(true));
  $("#tts-play").addEventListener("click", () => {
    if ($("#tts-fail").checked) { $("#tts-status").textContent = "Speech unavailable. The full text is still saved here."; return; }
    $("#tts-status").textContent = "Playing aloud in the message's language… (simulated)";
    $("#tts-stop").hidden = false; clearTimeout(speechTimer);
    speechTimer = setTimeout(() => { $("#tts-status").textContent = "Playback finished. Replay if needed."; $("#tts-stop").hidden = true; }, 2700);
  });
  $("#tts-stop").addEventListener("click", () => { clearTimeout(speechTimer); $("#tts-status").textContent = "Playback stopped. Text remains available."; $("#tts-stop").hidden = true; });

  function emergencyCanRun() { return state.permissions.nearby && !["bluetooth", "permission", "background"].includes(state.readiness); }
  function renderEmergency() {
    const active = state.emergency.active && emergencyCanRun();
    const box = $("#emergency-status"); box.classList.toggle("is-active", active); box.classList.toggle("is-blocked", state.emergency.active && !active);
    $("#emergency-status-title").textContent = active ? "Active · local participation on" : state.emergency.active ? "Participation interrupted" : "Off";
    $("#emergency-status-copy").textContent = active ? "Looking for nearby iTantra phones. Background operation may show a system notification." : state.emergency.active ? "The system stopped or blocked local participation. Your messages remain saved." : "Turn on to take part in local messaging and SOS discovery.";
    $("#emergency-toggle").textContent = state.emergency.active ? "Stop Emergency Mode" : "Start Emergency Mode";
    $("#available-toggle").checked = state.emergency.available;
    $("#available-toggle").disabled = !active;
    const warning = $("#emergency-warning"); warning.hidden = !state.emergency.active || active;
    if (!warning.hidden) warning.textContent = readinessInfo()[1];
    const list = $("#offer-list"); list.replaceChildren();
    const pending = state.offers.filter(o => o.status === "pending");
    $("#offers-empty").hidden = pending.length > 0;
    pending.forEach(offer => {
      const card = document.createElement("button"); card.className = "offer-card"; card.type = "button";
      const label = document.createElement("span"); label.className = "offer-tag"; label.textContent = "SOS REQUEST";
      const title = document.createElement("strong"); title.textContent = `${offer.category} · ${languageName(offer.language)}`;
      const note = document.createElement("small"); note.textContent = `${offer.route === "relayed" ? "Through relay" : "Nearby radio"} · Identity not verified`;
      card.append(label, title, note); card.addEventListener("click", () => { state.selectedOffer = offer.id; save(); go("offer"); });
      list.append(card);
    });
    const selected = state.offers.find(o => o.id === state.selectedOffer);
    if (selected) {
      $("#offer-category").textContent = selected.category;
      $("#offer-language").textContent = languageName(selected.language);
      $("#offer-age").textContent = selected.status === "pending" ? "Just now" : "No longer available";
      $("#offer-signal").textContent = selected.route === "relayed" ? "Relayed · distance unknown" : "Medium · rough only";
      $("#accept-offer").disabled = selected.status !== "pending" || selected.busy || ["searching", "session"].includes(state.sos.status);
      $("#offer-route").textContent = selected.status === "claimed" ? "Another responder connected first. This request is already answered; no second session was started." : selected.status === "expired" ? "This offer expired before you accepted. You can still help with a new request." : selected.busy ? "You are already handling an SOS. Finish or cancel it before accepting another request." : "The first confirmed responder becomes active. Later accepts are told the request is already answered.";
    }
  }
  $("#emergency-toggle").addEventListener("click", () => {
    if (!state.emergency.active) {
      if (!emergencyCanRun()) { go("settings"); return; }
      state.emergency.active = true; save(); renderEmergency(); return;
    }
    const warn = state.sos.status === "session" || state.sos.status === "searching";
    showDialog("Stop Emergency Mode?", warn ? "An active SOS interaction may be interrupted. Queued trusted messages remain stored on this phone." : "This phone will stop background participation. Queued messages remain stored on this phone.", "Stop mode", () => {
      state.emergency.active = false; state.emergency.available = false;
      if (warn) state.sos.notice = "Emergency Mode stopped. SOS connection interrupted.";
      save(); render();
    });
  });
  $("#available-toggle").addEventListener("change", event => {
    if (!state.emergency.active || !emergencyCanRun()) { event.target.checked = false; return; }
    state.emergency.available = event.target.checked; save(); renderEmergency();
  });

  function renderSos() {
    $("#sos-category").value = state.sos.category;
    $("#sos-language").value = state.sos.language;
    $("#sos-note").value = state.sos.note;
    const searching = state.sos.status === "searching";
    $("#search-state").textContent = searching ? state.sos.route === "relayed" ? "Expanding the request…" : "Looking for nearby people…" : state.sos.status === "expired" ? "SOS request expired" : state.sos.status === "cancelled" ? "SOS search cancelled" : "No search active";
    $("#search-copy").textContent = searching ? state.sos.route === "relayed" ? "Trying farther relays. Replies may arrive later." : "No one has responded yet. Your request is still active." : state.sos.status === "expired" ? "No responder was confirmed before the request ended. You can start another SOS." : state.sos.status === "cancelled" ? "Your request was cancelled. You can start again if needed." : "Start a new SOS if you need help.";
    $("#search-direct").classList.toggle("is-current", state.sos.route === "direct" && searching);
    $("#search-relay").classList.toggle("is-current", state.sos.route === "relayed" && searching);
    $("#cancel-sos").textContent = searching ? "Cancel SOS" : "Back to home";
    $("#session-other").textContent = state.sos.role === "requester" ? `Responder: ${state.sos.responder || "Connected"}` : "Person requesting help";
    $("#session-route").textContent = state.sos.route === "relayed" ? "Relayed emergency connection · replies may be delayed" : "Nearby live connection";
    $("#session-trust").textContent = state.sos.trust === "verified" ? "Nearby device verified · role and intentions not proven" : "Encrypted connection · Identity not verified";
    $("#verification-panel").hidden = state.sos.trust === "verified";
    $("#verify-match").disabled = state.sos.route === "relayed";
    const msgs = $("#sos-messages"); msgs.replaceChildren();
    if (state.sos.notice) { const note = document.createElement("div"); note.className = "notice-card"; note.textContent = state.sos.notice; msgs.append(note); }
    state.sos.messages.forEach(m => { const bubble = document.createElement("div"); bubble.className = "sos-bubble" + (m.mine ? " mine" : ""); bubble.textContent = m.text; const detail = document.createElement("small"); detail.textContent = `${m.mine ? "You" : "Other person"} · ${m.status || "text saved"}`; bubble.append(detail); msgs.append(bubble); });
    $("#sos-tts-controls").hidden = !state.sos.messages.some(m => !m.mine);
    $("#send-sos-reply").disabled = state.sos.status !== "session" || !$("#sos-reply").value.trim();
  }
  function startSos() {
    if (state.sos.status === "session") { showDialog("An SOS session is already active", "End the current conversation before starting another request.", "Return to session", () => go("sos-session")); return; }
    const begin = () => {
      if (!emergencyCanRun()) { go("settings"); return; }
      state.emergency.active = true;
      state.sos = { status: "searching", role: "requester", category: $("#sos-category").value, language: $("#sos-language").value, note: $("#sos-note").value.trim(), route: "direct", trust: "unverified", responder: "", messages: [], notice: "" };
      save(); go("sos-search");
      clearTimeout(searchTimer); searchTimer = setTimeout(() => { if (state.sos.status === "searching" && state.sos.route === "direct") { state.sos.route = "relayed"; state.sos.notice = "No nearby answer yet. Search widened through relays."; save(); renderSos(); } }, 7000);
    };
    if (!state.emergency.active) showDialog("Start Emergency Mode and SOS?", "Emergency Mode enables local participation while iTantra looks for someone to respond. Before acceptance, only limited request details are shared.", "Start SOS", begin);
    else begin();
  }
  $("#start-sos").addEventListener("click", startSos);
  $("#sos-category").addEventListener("change", event => { state.sos.category = event.target.value; save(); });
  $("#sos-language").addEventListener("change", event => { state.sos.language = event.target.value; save(); });
  $("#sos-note").addEventListener("input", event => { state.sos.note = event.target.value; save(); });
  function cancelSos() {
    if (state.sos.status === "searching") {
      clearTimeout(searchTimer); state.sos.status = "cancelled"; state.sos.notice = "SOS search cancelled by requester."; save(); goRoot("home");
    } else goRoot("home");
  }
  $("#cancel-sos").addEventListener("click", () => {
    if (state.sos.status !== "searching") { goRoot("home"); return; }
    showDialog("Cancel this SOS?", "People who already received the request will be told it was cancelled when the network reaches them.", "Cancel SOS", cancelSos);
  });
  function acceptSos(route, race = false) {
    if (state.sos.status !== "searching") return;
    clearTimeout(searchTimer);
    state.sos.status = "session"; state.sos.route = route; state.sos.role = "requester"; state.sos.trust = "unverified";
    state.sos.responder = "Responder A";
    state.sos.notice = race ? "Responder A connected first. Responder B's later acceptance was told this request is already answered." : route === "relayed" ? "Responder accepted through relays. Messages may arrive intermittently." : "A nearby responder accepted. The connection is encrypted, but identity is not verified.";
    if (state.sos.note) state.sos.messages.push({ text: state.sos.note, mine: true, status: "request note" });
    save(); go("sos-session");
  }
  $$("[data-sos-event]").forEach(button => button.addEventListener("click", () => {
    const event = button.dataset.sosEvent;
    if (event === "expand" && state.sos.status === "searching") { clearTimeout(searchTimer); state.sos.route = "relayed"; state.sos.notice = "No answer yet. Trying farther relays."; save(); renderSos(); }
    if (event === "accept-direct") acceptSos("direct");
    if (event === "accept-relayed") acceptSos("relayed");
    if (event === "race") acceptSos("direct", true);
    if (event === "expire") {
      if (state.sos.status === "searching") { clearTimeout(searchTimer); state.sos.status = "expired"; state.sos.notice = "The request expired without confirmed acceptance."; save(); renderSos(); }
      else { const offer = state.offers.find(o => o.status === "pending"); if (offer) { offer.status = "expired"; save(); renderEmergency(); if (current === "offer") go("emergency"); } }
    }
    if (event === "interrupt" && state.sos.status === "session") { state.sos.notice = "Connection interrupted. Text remains here; relayed replies may arrive later."; save(); renderSos(); }
    if (event === "timeout" && state.sos.status === "session") { state.sos.status = "ended"; state.sos.notice = "SOS session ended after inactivity. The text remains in this session record."; save(); renderSos(); }
  }));
  function simulateOffer(busy = false) {
    if (!state.emergency.active || !state.emergency.available || !emergencyCanRun()) { announce("Turn on Emergency Mode and Available to help before simulating an incoming SOS offer."); return; }
    const offer = { id: `o${state.nextId++}`, status: "pending", category: "Medical", language: "kn", route: "direct", busy: busy || ["searching", "session"].includes(state.sos.status), time: now() };
    state.offers.unshift(offer); state.selectedOffer = offer.id; save(); renderEmergency(); go("offer");
    clearTimeout(offerTimer); offerTimer = setTimeout(() => { if (offer.status === "pending") { offer.status = "expired"; save(); renderEmergency(); if (current === "offer" && state.selectedOffer === offer.id) go("emergency"); } }, 30000);
  }
  $("#simulate-offer").addEventListener("click", () => simulateOffer(false));
  $("#simulate-busy-offer").addEventListener("click", () => simulateOffer(true));
  $("#simulate-late-offer").addEventListener("click", () => {
    const offer = state.offers.find(o => o.status === "pending");
    if (!offer) { announce("Create an incoming SOS offer first."); return; }
    offer.status = "claimed"; state.selectedOffer = offer.id; save(); renderEmergency(); go("offer");
    announce("Another responder connected first. This phone cannot start a second active session.");
  });
  $("#simulate-sos-reply").addEventListener("click", () => {
    if (state.sos.status !== "session") { announce("Connect an SOS session before receiving a reply."); return; }
    state.sos.messages.push({ text: "I can respond. Tell me where you are and what help you need.", mine: false, status: state.sos.route === "relayed" ? "arrived after relay delay" : "received" });
    save(); renderSos(); announce("An SOS reply arrived as durable text. You can play it aloud from the conversation.");
  });
  $("#decline-offer").addEventListener("click", () => {
    const offer = state.offers.find(o => o.id === state.selectedOffer); if (!offer) return;
    offer.status = "declined"; save(); go("emergency");
  });
  $("#accept-offer").addEventListener("click", () => {
    const offer = state.offers.find(o => o.id === state.selectedOffer);
    if (!offer || offer.status !== "pending" || offer.busy || ["searching", "session"].includes(state.sos.status)) return;
    clearTimeout(offerTimer); offer.status = "accepted";
    state.sos = { status: "session", role: "responder", category: offer.category, language: offer.language, note: "", route: offer.route, trust: "unverified", responder: "", messages: [], notice: "You accepted this SOS. The encrypted session does not verify the person's identity." };
    save(); go("sos-session");
  });
  $("#verify-match").addEventListener("click", () => {
    if (state.sos.status !== "session" || state.sos.route !== "direct") return;
    showDialog("Did both codes match?", "Only mark the nearby device verified after comparing the code with the person in front of you.", "Codes matched", () => { state.sos.trust = "verified"; state.sos.notice = "Nearby device verified for this session. Their role or intentions are not proven."; save(); renderSos(); });
  });
  $("#verify-skip").addEventListener("click", () => { state.sos.notice = "Verification skipped. The connection stays encrypted; identity remains unverified."; save(); renderSos(); });
  $("#verify-mismatch").addEventListener("click", () => { state.sos.trust = "unverified"; state.sos.notice = "Codes did not match. Do not treat this person as verified. End the session if you cannot resolve it."; save(); renderSos(); });
  $("#sos-reply").addEventListener("input", renderSos);
  $("#send-sos-reply").addEventListener("click", () => {
    const input = $("#sos-reply"), text = input.value.trim(); if (!text || state.sos.status !== "session") return;
    state.sos.messages.push({ text, mine: true, status: state.sos.route === "relayed" ? "queued for relay" : "sent in encrypted session" });
    input.value = ""; save(); renderSos();
  });
  $("#sos-voice").addEventListener("click", () => {
    if (!state.permissions.microphone || $("#voice-result").value !== "success") { state.sos.notice = "Voice input unavailable. Type your reply instead."; save(); renderSos(); return; }
    $("#sos-reply").value = "Please tell me where you are and what help you need."; renderSos();
  });
  $("#simulate-sos-speech").addEventListener("click", () => {
    if (current === "sos-session") { $("#sos-reply").value = "I can help. Please describe your situation."; renderSos(); }
  });
  $("#sos-tts-play").addEventListener("click", () => {
    if ($("#tts-fail").checked) { $("#sos-tts-status").textContent = "Speech unavailable. The SOS reply remains readable as text."; return; }
    $("#sos-tts-status").textContent = `Playing reply aloud in ${languageName(state.sos.language)}… (simulated)`;
    clearTimeout(speechTimer); speechTimer = setTimeout(() => { $("#sos-tts-status").textContent = "Playback finished. The text remains here."; }, 2600);
  });
  $("#end-sos").addEventListener("click", () => {
    showDialog("End this SOS session?", "The conversation will stop. Messages already shown remain in the session record.", "End session", () => { state.sos.status = "ended"; state.sos.notice = "Session ended by you."; save(); goRoot("logs"); });
  });

  function simulateReinstall() {
    showDialog("Simulate identity loss?", "A new identity will replace this installation's identity. Existing contacts need a fresh QR exchange. Message history remains visible in this mockup.", "Create new identity", () => {
      state.identityVersion += 1; state.identityLost = true;
      state.contacts.forEach(c => { c.valid = false; }); state.draft.recipient = "";
      save(); render(); go("settings");
    });
  }
  $("#simulate-reinstall").addEventListener("click", simulateReinstall);
  $("#apply-readiness").addEventListener("click", () => {
    const value = $("#readiness-case").value;
    if (value === "reinstall") { simulateReinstall(); return; }
    if (value === "restart") {
      state.readiness = "ready"; save(); stack = []; go("home"); return;
    }
    state.readiness = value;
    if (value === "ready") { state.permissions.nearby = true; state.permissions.notifications = true; }
    if (value === "permission") state.permissions.nearby = false;
    if (value === "notifications") state.permissions.notifications = false;
    save(); render();
  });

  function render() {
    renderReadiness(); renderRecipients(); renderContacts(); renderLogs(); renderEmergency(); renderSos();
    $("#my-fingerprint").textContent = `Identity check: ${state.identityVersion === 1 ? "8F2C · 91A4" : "D034 · 7E12"}`;
    $("#home-language").value = state.draft.language;
    syncCompose();
  }
  $("#reset-review").addEventListener("click", () => {
    showDialog("Reset this review?", "This clears simulated contacts, messages, SOS activity, and readiness choices in this browser.", "Reset", () => {
      clearTimers(); state = fresh(); save(); stack = []; current = "home"; go("onboarding", false); setCapture("idle"); render();
    });
  });
  populateLanguages();
  $("#clock").textContent = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date());
  Object.entries(screens).forEach(([name, el]) => { el.classList.toggle("is-active", name === current); el.classList.toggle("has-nav", ["home", "contacts", "logs", "emergency", "settings"].includes(name)); });
  $("#bottom-nav").hidden = !["home", "contacts", "logs", "emergency", "settings"].includes(current);
  $$("#bottom-nav button").forEach(button => button.classList.toggle("is-active", button.dataset.go === current));
  setCapture("idle"); setHandsfree("listening"); render();
})();
