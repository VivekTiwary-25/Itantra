# iTantra UI/UX workspace

Branch: `UI/UX_discussion`.

- [Agent instructions](AGENTS.md)
- [Current context and resume point](HANDOFF.md)
- [Ideal-product handoff](iTantra_Complete_Ideal_UI_UX_Design_Handoff_v1.md)
- [Correction brief](iTantra_UI_Correction_Brief.md)
- [Current six-page review guide](review/itantra_ideal_product_review_guide.pdf)
- Published website: https://itantra-app-flow-review.kotesarajveer.chatgpt.site/

## Move to another device

```sh
git clone --branch UI/UX_discussion https://github.com/VivekTiwary-25/Itantra.git
cd Itantra
```

For an existing clean clone:

```sh
git fetch origin
git switch UI/UX_discussion
git pull --ff-only
```

If the branch has not been created locally, use
`git switch --track origin/UI/UX_discussion` after fetching.

Open this UI/UX directory in the coding agent and give it this prompt:

> We are continuing the existing iTantra ideal-product interactive website on
> UI/UX_discussion. Read docs/UI-Reference/AGENTS.md and HANDOFF.md in full,
> then the ideal-product handoff and correction brief. Inspect
> current-prototype/dist before editing. Those documents govern mockup scope;
> Android implementation status is engineering context. Preserve the useful
> existing interactions. My next request is: [describe the next change].

If the agent's workspace starts here, omit the `docs/UI-Reference/` prefix.
The repository contains the needed context; the original conversation and
Windows Downloads folder are not required to continue this website.

## Run locally

From the repository root on Windows, with Python 3 installed:

```powershell
python -m http.server 4173 --bind 127.0.0.1 --directory docs/UI-Reference/current-prototype/dist
```

On macOS/Linux:

```sh
python3 -m http.server 4173 --bind 127.0.0.1 --directory docs/UI-Reference/current-prototype/dist
```

Open http://127.0.0.1:4173/ in a browser. Stop the server with Ctrl+C.
Use a local HTTP server so browser storage and navigation behave consistently.
No npm install, Android SDK, Gradle, models, or backend is needed for the site.
An existing localhost browser profile may retain review data; use Reset full
review or a fresh browser profile for a clean session.

## Review and publishing

Share the current guide with a first-time reviewer. It has fillable fields and
prints on six A4 pages. Save a filled copy and paste concrete feedback back to
the agent. The guide predates future edits: update it when flow or labels change.

Publishing changes to the existing website requires access to the Sites project
recorded in `current-prototype/.openai/hosting.json`. GitHub push alone updates
this branch. The static asset root for hosting is `dist` within
`current-prototype/`; read `HANDOFF.md` before publishing.

## Regenerate the guide

The PDF itself is included and needs no dependencies to read. The optional
generator needs Python 3 and ReportLab:

```sh
python -m pip install reportlab
python docs/UI-Reference/review/build_review_guide.py
```

Use `python3` where appropriate. The script reads the assumptions from the
current site's HTML. It uses system Arial or DejaVu fonts, then ReportLab's
bundled Vera font as a fallback. To generate a temporary review copy:

```sh
python docs/UI-Reference/review/build_review_guide.py --output /path/to/review-copy.pdf
```

Inspect all rendered pages and fillable fields before committing a regenerated
PDF. The walkthrough text must be maintained manually to match site changes.
