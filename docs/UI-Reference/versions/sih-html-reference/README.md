# SIH HTML reference prototype

This folder contains the self-contained `SIH.html` prototype supplied as an
alternate visual and interaction reference for iTantra.

Open `SIH.html` directly in a browser. The page includes its CSS and
JavaScript inline and does not require a build step or external assets.

This is a reference version. The canonical editable review website remains in
`docs/UI-Reference/current-prototype/dist/`.

The reference includes:

- Push-to-Talk, Hands-free, and typed input converging on one editable draft;
- all ten supported languages, selected from the home-screen message-language dropdown;
- simulated QR identity exchange, contact management, recovery, and QR failure
  cases;
- persistent trusted messages, Logs, unread state, receive-side speech, and
  Queued / Relayed / Delivered / Expired / Unknown delivery states;
- first-launch permissions, readiness failures, restart, and identity loss;
- a combined Emergency Mode for trusted Push-to-Talk/Hands-free messages,
  requester SOS, location/listening actions, opt-in availability, and incoming
  SOS offers; and
- an external Review simulator for triggering success, failure, and recovery
  states.
