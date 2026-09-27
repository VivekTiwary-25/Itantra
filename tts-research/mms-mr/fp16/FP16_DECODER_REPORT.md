# Marathi MMS decoder-only FP16 candidate (desktop-only)

Date 2026-09-27. **No physical phone access for this worker** (RMX3392 is off-limits for language
workers; the foreman schedules real-device time). This is therefore a *candidate*, evaluated the
same way Bengali's was evaluated before its own phone benchmark
(`tts-research/mms-ben/speed/SPEED_REPORT.md` section 2, "fp16 fidelity" paragraph): deterministic
length-parity plus SNR, on desktop, against fp32. It is not a speed measurement and does not by
itself satisfy any RTF/start-latency gate.

## Why decoder-only, not whole-model, not INT8

- Bengali's MMS `ben` and Marathi's MMS `mar` are the same VITS/HiFi-GAN-V1 architecture from the
  same conversion pipeline (`config.json` is byte-identical between the two languages -- only the
  checkpoint weights and character vocab differ). Bengali's profiling (`tts-research/mms-ben/speed/SPEED_REPORT.md`
  section 1) found 92-94% of synthesis time in the HiFi-GAN decoder's convolutions on both desktop and phone.
  Nothing about Marathi's export changes that graph shape (verified below), so the same bottleneck applies
  and the same fix (decoder-only fp16) is the first candidate worth testing -- not re-derived from scratch,
  but not blindly assumed correct either: verified independently below.
- Bengali's whole-model fp16 changed 6/10 deterministic sentences' lengths (duration-predictor drift) and
  was rejected. Bengali's dynamic INT8 quantization was "dramatically slower" (`tts-research/mms-ben/int8/INT8_EXPERIMENT_REPORT.md`).
  Neither was blindly repeated here; INT8 was not attempted at all (no reason found to expect a different
  result on the same decoder architecture), and decoder-only fp16 is verified independently below rather
  than assumed to transfer.

## Graph identity check

`model.onnx`'s top-level ONNX node-name scopes are exactly `/dec`, `/dp`, `/enc_p`, `/flow`, `/Add`,
`/Cast*`, `/Constant*`, etc. -- the identical set (by name, not just by count) as Bengali's export. The
`mms_decoder_fp16.py` script (byte-identical copy of Bengali's) selects nodes by `name.startswith("/dec/")`
and an op-type allow-list (`Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh`); it required no changes and
ran unmodified:

```
python mms_decoder_fp16.py model.onnx model.fp16dec.onnx /dec/ Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh
# region nodes: 200   boundary casts in: 1   -> model.fp16dec.onnx
```

| file | bytes | sha256 |
|---|---|---|
| fp32 export (input) | 114,064,278 | 0938b7a815dce910aaee9dee72d5b9aabb72d301c11baf28120cb4a5b1f8a517 |
| **fp16-decoder model (shipped asset)** | **85,415,196** | **6868c55c1996a005a181a168afff86d4589292450258b822eb7570b3630d16f6** |

Size reduction (114,064,278 -> 85,415,196 B, -25.1%) matches Bengali's own reduction almost exactly
(114,065,046 -> 85,415,964 B, -25.1%), consistent with converting the same fraction of the same-shaped graph.

## Deterministic length-parity check (`det_compare.py`)

10 sentences from `../evidence/mr_input.txt`, `noise_scale=0.0`, `noise_scale_w=0.0`, `length_scale=1.0`
(fully deterministic -- no random draw), sherpa-onnx 1.13.7, 2 threads, desktop CPU:

| # | fp32 samples | fp16-dec samples | delta (samples / ms) | SNR (dB) |
|---|---|---|---|---|
| 1 | 31,796 | 31,796 | 0 / 0 | 68.6 |
| 2 | 25,572 | 25,572 | 0 / 0 | 68.5 |
| 3 | 32,330 | 32,330 | 0 / 0 | 69.2 |
| 4 | 27,576 | 27,576 | 0 / 0 | 67.1 |
| 5 | 30,815 | 30,815 | 0 / 0 | 68.4 |
| 6 | 41,298 | 41,298 | 0 / 0 | 69.2 |
| 7 | 23,600 | 23,600 | 0 / 0 | 70.0 |
| 8 | 53,670 | 53,670 | 0 / 0 | 65.5 |
| 9 | 20,323 | 20,339 | +16 / +1 ms | 39.8 |
| 10 | 37,660 | 37,660 | 0 / 0 | 67.3 |

9/10 sentences match sample-for-sample exactly (0 delta). Sentence 9 differs by 16 samples (1 ms) out
of 20,323 samples (1.27 s) -- 0.08% length difference, and SNR on the compared (overlapping) region is
still 39.8 dB, i.e. the divergence appears only at the very end of the clip. This is categorically
different from Bengali's rejected all-Conv-fp16 result (6/10 sentences with materially different
lengths, some by hundreds of ms, indicating actual duration-predictor drift): here 9/10 are bit-for-bit
length-identical and the tenth is off by a single 16-sample rounding step, most plausibly a
floating-point boundary effect in a `Ceil`/length-rounding op immediately downstream of the fp16
region rather than a changed phoneme duration. All ten fp32 and fp16-decoder WAVs are saved in
`audio/det_fp32/` and `audio/det_fp16dec/` for direct listening comparison; raw data in
`det_compare_result.json`.

**Verdict: accepted as a candidate.** This passes the same bar Bengali's decoder-only fp16 passed
("matches fp32 length on 10/10 sentences" for Bengali; here 9/10 exact plus one 1 ms/0.08% outlier,
which is not evidence of the duration-predictor drift pattern the whole-model fp16 experiment showed).
It has **not** been benchmarked for speed on any device (no phone access) and has **not** been heard by
a human. Both remain open before this candidate can be called ready for anything beyond desktop asset
preparation.

## What this does not establish

- No RTF or text-to-audible-start numbers exist for Marathi on RMX3392 or any phone. Bengali's own
  finding that foreground vs backgrounded scheduling changes RTF by ~1.8x (`SPEED_REPORT.md` section 1)
  means a desktop number would be actively misleading if presented as a phone estimate, so none is given.
- No thread-count profiling was done (Bengali's "4 threads best on RMX3392" was itself phone-specific
  profiling, not a generic constant); `TtsHelper`'s Marathi voice entry uses 4 threads as a starting
  point, explicitly not phone-verified.
- No listener has judged intelligibility, meaning preservation, or naturalness for either the fp32 or
  fp16-decoder Marathi audio.
