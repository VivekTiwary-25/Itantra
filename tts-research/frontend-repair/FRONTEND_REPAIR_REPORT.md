# Track B Indic-TTS frontend repair — isolated desktop run

**Date:** 2026-09-26. **BASE_SHA:** `0e63c4581f4a1fa465ed64e17ef5ff4067e16edc`. This report covers the existing `tts/ta/indic-tts`, `tts/te/indic-tts`, and `tts/or/indic-tts` worktrees. The occupied `D:\iTantra-tts-ml` checkout is on `UI/UX_discussion`; it was read only. No branch was switched, and no app, model weight, original RUN 1 WAV, or original result row was modified.

## Root cause and exact processing stage

The original checkpoint configs use Coqui `multilingual_cleaners`, character tokens (`use_phonemes=false`), and a fixed per-language `VitsCharacters` vocabulary. The cleaner lowercases, replaces limited punctuation, removes auxiliary symbols, and collapses spaces. It does **not** verbalize numbers or normalize Odia nukta spellings. Coqui `TTSTokenizer.encode` catches `KeyError` from `char_to_id`, prints a warning, and **discards** that character; synthesis continues with shorter token IDs. Full-line before/after codepoints, normalized text, frontend output, and token IDs are retained per input in each `repair-20260926/frontend_trace.json`; `acoustic_segments.json` records the exact per-sentence token sequences entering the acoustic model after Coqui's sentence splitter.

| Language | Original RUN 1 input and actual loss | Cause | Repair entering acoustic model |
|---|---|---|---|
| Tamil | Message 9 `மருந்துக்காக ௧௨ பெட்டிகளை கொண்டு வாருங்கள்.` became `மருந்துக்காக  பெட்டிகளை கொண்டு வாருங்கள்.`; U+0BE7/U+0BE8 lost | Native digits absent from the checkpoint vocabulary; no number normalizer | `மருந்துக்காக பன்னிரண்டு பெட்டிகளை கொண்டு வாருங்கள்.`; all characters have IDs |
| Telugu | Message 9 lost U+0C67/U+0C68, leaving two spaces. Message 10 lost U+200C after `ద్` and before `ల`, producing the same model-token sequence as a conjunct | Digits absent; U+200C absent from vocabulary. The latter is a shaping control after Telugu virama, not a number/letter token | Message 9 uses `పన్నెండు`; U+200C is deliberately removed **only** between Telugu virama and following Telugu consonant, with a warning and original written text retained |
| Odia | Message 1 lost U+0B3C from `ଢ଼`; message 9 lost U+0B67/U+0B68 | Config contains U+0B5C/U+0B5D precomposed letters but not their canonically equivalent decomposed sequences; digits absent | `ଢ଼` maps to equivalent `ଢ଼`, and `୧୨` maps to `ବାର`; all resulting characters have IDs |

The exact source of the spoken number words is AI4Bharat's [MIT-licensed `indic-numtowords` 1.1.0](https://github.com/AI4Bharat/indic-numtowords), pinned under each candidate's `tools/vendor`. The model vocabularies contain every character of `பன்னிரண்டு`, `పన్నెండు`, and `ବାର`. This establishes **token-level compatibility**, not correct pronunciation or natural phrasing. Arbitrary digit runs are verbalized by that library; ambiguous leading-zero runs, unsupported script letters, unhandled nukta, and unsupported join-control contexts now raise `UnsupportedText` before inference instead of vanishing.

[Unicode's Odia normalization chart](https://www.unicode.org/charts/normalization/chart_Oriya.html) identifies the precomposed/decomposed nukta pairs. [Unicode's Indic FAQ](https://www.unicode.org/faq/indic.html) says U+200C after virama requests explicit halant rendering. For the observed Telugu message 10, dropping that shaping instruction changes the **written rendering request**. Its base consonants and virama remain in the model token sequence. The original and repaired message 10 WAV hashes are identical; this checkpoint therefore made the same audio. Whether that pronunciation matches the writer's intent still needs a native listener. Other U+200C positions produce an error.

## Exact code changes

- `tools/indic_frontend_repair.py` in each candidate: language-specific digit verbalization, restricted Telugu U+200C handling, Unicode-equivalent Odia nukta mapping, and a strict post-cleaner vocabulary check.
- `tools/indic_tts_repaired_probe.py` in each candidate: reads the **unchanged** ten RUN 1 lines; records original and repaired codepoints/text/token IDs; refuses a nonempty output directory; synthesizes into a new directory.
- `tools/test_indic_frontend_repair.py` in each candidate: five real-vocabulary regression tests covering native and ASCII `12`, mixed-script letters, leading zeros, all ten original lines, and language-specific joiner/nukta handling.
- `tools/trace_acoustic_segments.py` in each candidate: reproduces Coqui's sentence splitting and records the actual per-segment acoustic token IDs in `acoustic_segments.json`.
- The old `tools/indic_tts_desktop_probe.py` and original Coqui frontend remain available for comparison. Checkpoint weights and configs were not edited.

## Desktop inference and retained audio

Each candidate passed **5/5** frontend regression checks. Each generated **10/10 new WAVs**, with zero frontend errors, from exactly the original ten draft lines. `FRONTEND_REPAIR_RESULTS.csv` has all 30 rows, including original input, before/after frontend output, discarded codepoints, WAV path/hash, generation time, duration, warnings, and remaining gates. `frontend_trace.json` holds full-line token IDs; `acoustic_segments.json` holds the exact per-segment IDs. All 30 WAVs were checked as non-silent mono 16-bit PCM at 22,050 Hz with durations matching their traces. Original 30 WAV SHA-256 values and input file hashes still match RUN 1.

| Language | New WAVs and trace | Total generation time / audio duration across ten | Repaired case evidence |
|---|---|---|---|
| Tamil | `D:\iTantra-tts-ta-indic\local-recordings\ta-indic\repair-20260926\01.wav`–`10.wav` | 16.744 s / 48.005 s | #9 duration 3.878 → 4.505 s; new hash differs |
| Telugu | `D:\iTantra-tts-te-indic\local-recordings\te-indic\repair-20260926\01.wav`–`10.wav` | 13.747 s / 41.771 s | #9 duration 3.275 → 4.296 s; #10 WAV hash unchanged because token IDs were unchanged |
| Odia | `D:\iTantra-tts-or-indic\local-recordings\or-indic\repair-20260926\01.wav`–`10.wav` | 13.559 s / 40.191 s | #1 duration 4.668 → 4.796 s; #9 duration 2.764 → 3.008 s |

Original RUN 1 timings remain in `D:\iTantra-tts-ml\DESKTOP_RESULTS.csv` and original WAVs remain beside each new `repair-20260926` directory. The separate trace-only diagnostics are under `repair-trace-20260926`; run logs are `repair-run-20260926.log` in each original WAV folder.

## Verification status and remaining blockers

The repair is **technically verified at the desktop frontend/tokenizer and WAV-generation level** for these exact draft inputs. It is not an acoustic quality finding. **Every new WAV is QUALITY UNVERIFIED — listener needed.** The draft input sentences still need native-speaker approval. A blind native listener must check all ten WAVs per language, especially the spoken `12`, Odia `ଢ଼` in #1, and the intended Telugu written/pronounced form in #10. The number-word library output also needs review in each sentence's grammatical context.

Indic-TTS checkpoint redistribution terms remain **UNKNOWN** despite the code's MIT license. The two-stage FastPitch/HiFi-GAN stack still lacks a working variable-length acoustic ONNX/Android inference path; prior RUN 1 exports failed on a second text length. No candidate was wired into the Android app. All on-phone playback, RTF, memory, receive-path, and English/Hindi regression tests remain **PENDING PHONE**. Do not mark any language as working from these WAVs. The RUN 2 phone checklist still applies after listener, license, and runtime gates clear.

The original `OG.md`, `TTS_LOG.md`, and `RUN2_CHECKLIST.md` are in the occupied `UI/UX_discussion` worktree. To obey the explicit worktree isolation rule, this report and the per-candidate `tts-research/FRONTEND_REPAIR_LOG.md` files record the Track B continuation instead of editing that checkout.
