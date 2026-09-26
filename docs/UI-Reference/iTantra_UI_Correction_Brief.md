# iTantra UI Correction Brief

## Source precedence

1. **HIGHEST PRIORITY:** `iTantra_Complete_Ideal_UI_UX_Design_Handoff_v1.md`
   - This defines the complete product the mockup must support.
   - Assume the complete ideal iTantra exists.
   - Its capability set and edge cases are mandatory.

2. **CURRENT PROTOTYPE FILES / ZIP**
   - Preserve useful visual polish and interaction work.
   - Modify and extend this prototype rather than restarting from zero.

3. **OLDER UI/UX HANDOFF + OLD PROTOTYPE**
   - Visual / interaction-history reference only.
   - NOT authoritative for product scope.
   - Its five-screen map, eight-language selector, old ACK model, and current-app constraints must NOT override the ideal-product handoff.

4. **SIH MAIN HANDOFF**
   - Engineering/project context only.
   - Do not use current implementation status to reduce mockup scope.

## Required correction

The current prototype successfully covers the old core flow:
voice / hands-free / text -> editable draft -> send -> logs.

Keep that work.

But extend/restructure the mockup so it can represent the **complete ideal iTantra product**, including at minimum:

- all 10 languages
- local iTantra identity
- QR trusted-contact exchange
- trusted contact management
- explicit recipient selection
- recipient may be offline / not nearby
- Queued / Relayed / Delivered / Expired / Unknown
- delayed store-carry-forward delivery
- Emergency Mode
- Available to help
- SOS requester flow
- SOS responder flow
- accept / decline / ignore / timeout
- encrypted but identity-not-verified state
- nearby-device verification
- direct vs relayed SOS
- multiple-responder race handling
- SOS cancellation / expiry
- first-launch readiness / permissions
- Bluetooth / background-service failure states
- reinstall / identity-loss / QR re-exchange
- urgent-message semantics
- receive-side TTS states/failure

## Hard rule

Do not preserve the old five-screen structure if it prevents full coverage.

Do not remove working visual quality unnecessarily.

The final design should feel simple, but **no capability or edge case in the ideal-product handoff may require a structural redesign later**.
