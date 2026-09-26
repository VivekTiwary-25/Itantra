# AGENTS.md - iTantra UI/UX prototype

These instructions cover this directory, its website, design references, and
review artifacts. Read `HANDOFF.md` in full, then inspect the existing files.

## Scope and source priority

1. `iTantra_Complete_Ideal_UI_UX_Design_Handoff_v1.md` defines authoritative
   product scope, capabilities, trust distinctions, flows, and edge cases.
2. `iTantra_UI_Correction_Brief.md` supplies authoritative correction direction.
3. `current-prototype/` is the existing interactive artifact. Preserve useful
   visual polish and interactions while modifying it to satisfy the sources.
4. Earlier handoffs, `historical/`, and `itantra-ui-reference/` provide compatible
   visual/history reference only. Their five-screen structure, eight-language
   selector, and ACK model do not control this mockup.
5. `engineering-context/SIH_MAIN_HANDOFF.txt`, root `TASK.md`, lane documents,
   and Android status are engineering context for this design exercise. Do not
   reduce ideal-product scope to currently implemented Android features.

The user's explicit correction establishes this priority even where copied
historical documents call themselves authoritative. For native Android changes,
return to the root engineering instructions and active native task.

## Working rules

- Work on `UI/UX_discussion` unless the user requests another branch.
- Extend the existing static website; do not restart from scratch.
- `current-prototype/dist/` is the canonical editable website on this branch.
- Keep the phone frame and clearly separate simulation controls from app UI.
- Preserve voice-first PTT, Hands-free, the shared editable draft, explicit Send,
  Logs, and the calm dark field-tool aesthetic.
- Maintain shared state across screens and honest delivery/trust distinctions.
- Raise source contradictions with the user; do not silently rewrite product rules.
- Record undecided behavior in the site's My assumptions panel and this handoff.
- Keep simulations obvious and easy to trigger. Do not introduce real radio,
  microphone, camera, speech, or production services during mockup work without
  an explicit request.
- Keep guides consistent with the actual site. Copy the site's assumptions
  verbatim when updating the checklist.
- Historical artifacts are frozen references. Update the current artifact and
  current guide rather than accidentally editing an older prototype.
- Preserve `.openai/hosting.json` and its existing Sites project identity.
  Do not create a replacement hosted site or change its access without direction.
- GitHub push does not deploy the website. Publishing requires the existing
  Sites project/account and its source/version deployment workflow.
- Commit/push only when requested. Stage specific UI/UX paths and reviewed
  routing changes; avoid sweeping in research, models, or temporary files.

## Verification

There is no frontend build step: plain HTML, CSS, and JavaScript are served from
`current-prototype/dist/`. See `README.md` for local server commands.

After behavior changes, run `node --check current-prototype/dist/app.js` from
this directory if Node is available, then exercise the affected flow in a
browser and inspect desktop/phone widths. A syntax check is not interaction
verification. Use `HANDOFF.md` for the current smoke-test sequence and known
limitations. Use the PDF skill's render-and-inspect workflow for guide edits
when that skill is available.

Website-only and handoff changes do not require an Android Gradle build or
physical Android acceptance. Do not claim that browser simulation verifies
radio, encryption, STT, TTS, or the native application.
