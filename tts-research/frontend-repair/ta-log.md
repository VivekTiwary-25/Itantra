# Tamil Indic-TTS frontend repair log — 2026-09-26

- Worktree `D:\iTantra-tts-ta-indic`, branch `tts/ta/indic-tts`, unchanged `BASE_SHA` `0e63c4581f4a1fa465ed64e17ef5ff4067e16edc`.
- RUN 1 message 9 native digits U+0BE7/U+0BE8 were silently discarded by Coqui's fixed character tokenizer. The new strict frontend uses AI4Bharat `indic-numtowords` 1.1.0 (MIT) to pass `பன்னிரண்டு` to the unchanged acoustic model. Full-line IDs are in `local-recordings/ta-indic/repair-20260926/frontend_trace.json`; exact per-sentence acoustic IDs are in `acoustic_segments.json` beside it.
- Five real-vocabulary regression tests passed. The unchanged ten-line input SHA-256 is `d715279212c0b121c2a709907c1efbb00498010b0112f18d481626c3c518b0a4`. Ten new non-silent WAVs are `local-recordings/ta-indic/repair-20260926/01.wav`–`10.wav`. Generation total 16.744 s; audio total 48.005 s. All original RUN 1 WAV hashes still match.
- **QUALITY UNVERIFIED — listener needed. PENDING PHONE.** Native speaker must approve draft text and a blind listener must check spoken number and all intended meanings. Checkpoint license terms remain UNKNOWN and variable-length acoustic ONNX/Android inference remains unproved.
- Aggregate report and per-message CSV: `D:\iTantra-tts-ta-indic\FRONTEND_REPAIR_REPORT.md` and `FRONTEND_REPAIR_RESULTS.csv`. The occupied UI/UX worktree containing original Track B logs was not edited.
