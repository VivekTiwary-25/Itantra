(() => {
  const app = document.querySelector(".app");
  const pttButton = document.querySelector("#ptt-button");
  const pttMode = document.querySelector("#ptt-mode");
  const handsfreeMode = document.querySelector("#handsfree-mode");
  const activeEyebrow = document.querySelector("#active-eyebrow");
  const activeTitle = document.querySelector("#active-title");
  const activeHint = document.querySelector("#active-hint");
  const typedForm = document.querySelector("#typed-form");
  const typedMessage = document.querySelector("#typed-message");
  const messageList = document.querySelector("#message-list");
  const messageCount = document.querySelector(".message-count");
  const toast = document.querySelector("#toast");

  let mode = "ptt";
  let isHolding = false;
  let sendingTimer = null;
  let toastTimer = null;

  const vibrate = (pattern) => {
    if ("vibrate" in navigator) navigator.vibrate(pattern);
  };

  const setState = (state) => {
    app.dataset.state = state;
  };

  const setMode = (nextMode) => {
    if (mode === nextMode) return;
    clearTimeout(sendingTimer);
    isHolding = false;
    pttButton.classList.remove("is-held");
    mode = nextMode;
    app.dataset.mode = mode;

    const handsfree = mode === "handsfree";
    pttMode.classList.toggle("is-selected", !handsfree);
    handsfreeMode.classList.toggle("is-selected", handsfree);
    pttMode.setAttribute("aria-pressed", String(!handsfree));
    handsfreeMode.setAttribute("aria-pressed", String(handsfree));

    if (handsfree) {
      activeEyebrow.textContent = "Hands-free mode";
      activeTitle.textContent = "Listening…";
      activeHint.textContent = "Speak normally";
      setState("listening");
      vibrate(26);
    } else {
      setState("idle");
      vibrate(12);
    }
  };

  const beginTalking = (event) => {
    if (mode !== "ptt" || isHolding) return;
    event.preventDefault();
    isHolding = true;
    pttButton.classList.add("is-held");
    activeEyebrow.textContent = "Voice channel open";
    activeTitle.textContent = "Listening…";
    activeHint.textContent = "Speak now · release when finished";
    setState("listening");
    vibrate(30);

    if (event.pointerId !== undefined) {
      try {
        pttButton.setPointerCapture(event.pointerId);
      } catch (_) {
        // Pointer capture is optional; document-level release still works.
      }
    }
  };

  const finishTalking = (event) => {
    if (mode !== "ptt" || !isHolding) return;
    if (event) event.preventDefault();
    isHolding = false;
    pttButton.classList.remove("is-held");
    setState("sending");
    vibrate(12);

    clearTimeout(sendingTimer);
    sendingTimer = window.setTimeout(() => {
      setState("idle");
    }, 720);
  };

  const showToast = (text) => {
    toast.textContent = text;
    toast.classList.add("is-visible");
    clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove("is-visible"), 1600);
  };

  const formatTime = () =>
    new Intl.DateTimeFormat(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(new Date());

  const addTypedMessage = (text) => {
    const article = document.createElement("article");
    article.className = "message message-you";

    const meta = document.createElement("div");
    meta.className = "message-meta";
    const sender = document.createElement("span");
    sender.textContent = "You";
    const time = document.createElement("time");
    time.dateTime = new Date().toISOString();
    time.textContent = formatTime();
    meta.append(sender, time);

    const copy = document.createElement("p");
    copy.textContent = text;
    const delivery = document.createElement("span");
    delivery.className = "delivery";
    delivery.textContent = "Delivered";

    article.append(meta, copy, delivery);
    messageList.append(article);
    messageList.scrollTo({ top: messageList.scrollHeight, behavior: "smooth" });

    const total = messageList.querySelectorAll(".message").length;
    messageCount.textContent = `${total} ${total === 1 ? "message" : "messages"}`;
  };

  pttMode.addEventListener("click", () => setMode("ptt"));
  handsfreeMode.addEventListener("click", () => setMode("handsfree"));

  pttButton.addEventListener("pointerdown", beginTalking);
  pttButton.addEventListener("pointerup", finishTalking);
  pttButton.addEventListener("pointercancel", finishTalking);
  pttButton.addEventListener("lostpointercapture", finishTalking);
  pttButton.addEventListener("contextmenu", (event) => event.preventDefault());

  pttButton.addEventListener("keydown", (event) => {
    if ((event.key === " " || event.key === "Enter") && !event.repeat) beginTalking(event);
  });
  pttButton.addEventListener("keyup", (event) => {
    if (event.key === " " || event.key === "Enter") finishTalking(event);
  });

  typedForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const message = typedMessage.value.trim();
    if (!message) {
      showToast("Type a message first");
      typedMessage.focus();
      return;
    }
    addTypedMessage(message);
    typedMessage.value = "";
    vibrate(12);
    showToast("Message sent");
  });

  app.dataset.mode = mode;
})();
