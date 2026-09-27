# Bengali TTS speed rescue on RMX3392

Date 2026-09-27. Branch `research/bn-tts-speed` (from `723acec`). RMX3392, MediaTek mt6877 (6x Cortex-A55
2.0 GHz = cpu0-5, 2x Cortex-A78 2.5 GHz = cpu6-7), Android 14, sherpa-onnx 1.13.7 AAR (bundles ONNX Runtime 1.27.1).
**QUALITY UNVERIFIED by a listener. Not GREEN.** Only Vivek marks GREEN.

## Result in one paragraph

Two things were wrong with the old picture. (1) Every earlier Bengali number (RTF 2.46, start 6.9 s) was
measured with the app **not visible**, where Android confines the process to the `background` cpuset: four
little A55 cores. With the app visible (`top-app`, all eight cores) the same fp32 model runs at RTF ~1.05.
(2) 92-94% of MMS time is the HiFi-GAN V1 decoder's convolutions, and ORT 1.27 on this ARM build has fp16
Conv kernels. Running only the decoder in fp16 gives, in the app with playback and the app visible,
**warm median RTF 0.77 and median text-to-audible-start 2.42 s (max 2.98 s, 21/21 under 3 s), 0 failures in
22 runs**. The **frozen speed gates are met with the app visible and still missed when it is not** (RTF 1.37,
start 4.4 s). A Bengali number/Unicode normaliser was added because MMS drops Bengali digits and mis-speaks
ASCII digits.

## 1. Bottleneck

- Graph (`profiles/graph_structure.txt`): MMS `ben` is VITS with a HiFi-GAN V1 decoder (upsample start 512 ch,
  resblock type 1, kernels 3/7/11 with 3 dilations = 6 convs per resblock), ~37 GFLOP per second of 16 kHz audio.
  Piper "medium" (en/hi/ml) uses 256 ch and resblock type 2 (kernels 3/5/7, 2 convs): an order of magnitude less
  decoder work, which is why Piper is fast on the same phone.
- ORT profile on the phone (`profiles/phone_fp32_t4_background_profile.txt`, via sherpa's `cpu:<config>` provider
  with `ProfilingFilePrefix`): Conv+FusedConv 88%, ConvTranspose 7%; by module the decoder resblocks are 85%,
  upsamplers 7%, flow 3.5%, text encoder 1.7%, duration predictor 1.0%. Desktop profile agrees (decoder 92%).
- Scheduling: `/proc/<pid>/cpuset` is `background` (cpu0-3) when the app is not visible and `top-app` (cpu0-7)
  when it is (`/dev/cpuset/background/cpus` = 0-3). That explains the old thread result (4 threads best, 6-8
  worse): there were only four little cores to run on.
- INT8 hypothesis from the previous run: not re-tested (profiling gave no reason to); fp16 took its place.

## 2. What was tried (bench receiver, generate only, 10 sentences, warm medians; logs in `bench-logs/`)

| variant | condition | RTF median (range) | synth median / max | notes |
|---|---|---|---|---|
| MMS fp32, 4 thr | background | 2.40 (2.11-2.66) | 7.21 / 9.16 s | reproduces the old 2.46 |
| MMS fp32, 5 / 8 thr | background | 2.68 / 3.16 | | more threads = worse |
| MMS fp32, 4 / 8 thr + `session.dynamic_block_base=4` | background | 2.40 / 3.12 | | no effect |
| MMS fp32, 2 / 4 / 6 / 8 thr | top-app | 1.04 / 1.07 / 1.14 / 1.36 | 3.13 / 3.25 / 3.51 / 4.01 s | init 1.1 s (3.9 s in background) |
| MMS all Convs fp16 | top-app | 0.81 (4 thr), 0.86 (2 thr) | 2.45 / 3.23 s | changes predicted durations (encoder/duration predictor in fp16): rejected |
| MMS decoder Convs fp16 | top-app | 0.86 | 2.59 / 3.60 s | |
| **MMS whole decoder fp16 (`dec16_full`, shipped)** | top-app | **0.81 (0.71-0.89)** | **2.44 / 3.05 s** | ORT re-inserts casts where no fp16 kernel exists |
| MMS whole decoder fp16 | background | 1.41 (1.24-1.53) | 4.14 / 5.14 s | fp32 in same condition: 2.42 |
| Coqui `vits-coqui-bn-custom_female` | background | 2.87 (2.63-3.28) | 7.81 / 11.13 s | same heavy decoder at 22.05 kHz |
| Mimic3 `vits-mimic3-bn-multi_low`, 4 thr | background | 0.49 (0.47-0.58) | 1.50 / 2.17 s | Piper-medium-sized decoder |
| Mimic3, 2 thr | top-app | 0.21 (0.19-0.24) | 0.59 / 0.91 s | |

Also tried and **rejected: splitting at the danda** (so the first sentence could play early). It lowered the
ASR intelligibility proxy for MMS (mean CER 3.8% raw vs 5.3% split, 3 repeats each) and is not needed for
the gate with fp16. Not tried (no profiling reason, or no benefit possible): NNAPI (dynamic output length),
XNNPACK EP (1-D convs), BF16 fastmath (A78 has no BF16), static INT8/QDQ (INT8 conv path was already 2.7x
slower), retraining/distillation (no suitable GPU; out of scope).

fp16 fidelity (deterministic: noise 0, `silence_scale` 1, on the phone, `bench-logs/det_*`): decoder-only fp16
matches fp32 length on 10/10 sentences with median SNR 29.9 dB (min 28.3 dB). All-Conv fp16 changed the length of
6/10 sentences, so it is not used.

## 3. Shipped configuration

- `bn` -> `vits-mms-ben/model.onnx` = MMS `ben` export with the decoder in fp16, 4 threads, character frontend,
  sherpa defaults (noise 0.667, noise_w 0.8, length 1, silence 0.2).
  Produced by `speed/mms_decoder_fp16.py` from the fp32 export; byte-reproducible.
  | file | bytes | sha256 |
  |---|---|---|
  | fp32 export (input) | 114,065,046 | f8f7bf4f0b703f706e0531ff2b364bf9a3ebb8bed69262258f543e0212094e71 |
  | **fp16-decoder model (asset)** | **85,415,964** | **8a819bba1b0c424842b71f89d36987e278dde7e4e564e94af0b90a782a2fb90e** |
  `setup-models.ps1 -MmsBengaliModel <fp16 model>` pins the new hash (it rejected the old fp32 asset).
- `BengaliTextNormalizer` (bn only): digit runs -> Bengali number words (AI4Bharat indic-numtowords 1.1.0 data, MIT,
  first variant; <= 9 digits as one number, leading zero or longer runs digit by digit), then Unicode NFC. JVM test
  checks 75 numbers against the Python package.
- `TtsHelper.installAssets` now stamps its marker with the APK `lastUpdateTime`. **Bug found:** the phone was
  still running the INT8 Bengali model after the "restore to fp32", because the old marker never re-copied an asset
  whose file name was unchanged. Existing installs re-copy each voice once after this update (0.55-0.76 s per voice).
- Source/license unchanged: facebook/mms-tts `models/ben`, **CC BY-NC 4.0**, attribution in `MODEL_CARD`.

## 4. In-app measurement (TtsHelper: synthesis + AudioTrack playback; `inapp/`, `run_speed_benchmark.sh`)

APK built from this branch (clean build 581,445,137 B; fp32 clean build was 608,240,873 B: -26,795,736 B).
Installed with `adb install -r` immediately before the run; one driver; same sentences/order as fp32/INT8 runs.

| | app visible (top-app) | app not visible (background) |
|---|---|---|
| Bengali runs / errors / crashes | 22 / 0 / 0 | 10 / 0 / 0 |
| cold, first use after install | 4.12 s to audible = asset copy 0.55 + init 1.18 + synth 2.34 | n/a (engine already loaded) |
| warm median RTF (range) | **0.77 (0.68-0.86)** | 1.37 (1.28-1.73) |
| warm text-to-audible-start median / max | **2.42 s / 2.98 s** (0 of 21 over 3 s) | 4.39 s / 5.33 s |
| codepoints received = input | yes, all 22 (44,44,37,28,43,51,45,32,27,34,...) | yes |
| English / Hindi / Malayalam RTF | 0.19 / 0.18 / 0.19, start 0.50 / 0.47 / 0.66 s | 0.64 / 0.68 / 0.69 (old background runs: 0.65-0.72 / 0.62-0.68 / 0.69) |

Memory (`mem_compare.txt`: fresh process, app visible, one model loaded, one sentence, 2 repeats): app baseline PSS
137-139 MB; MMS fp32 346-354 MB; **MMS fp16-decoder 359-364 MB (+5 to +17 MB, no memory saving)**; Mimic3 297-306 MB.
In the full in-app run: PSS 358,487 KB with Bengali only, 933,679 KB with all four voices loaded (app visible;
the earlier 299,622 / 728,244 KB were most likely taken with the app not visible, as their timings match the
background condition, so they are not comparable; the isolated comparison above is).

## 5. Intelligibility proxy (not a listener)

IndicConformer-600M (ai4bharat, `bn`, CTC) transcribes the audio; CER/WER against the input text with digits
verbalised on both sides (`tools/asr_score.py`, results in `asr/`). It tracks the human screen only partly (it heard
"আগুন" in 02 where the listener heard "abun"); use it to rank, not to accept.

| audio | CER | WER |
|---|---|---|
| MMS fp32, the human-screened WAVs | 3.4% | 10.1% |
| MMS fp32, raw text, 3 desktop repeats | 3.9 / 3.5 / 3.9% | 14-16% |
| MMS fp32 vs fp16 decoder, phone, same noise draw | 4.7% vs 5.0% | 17% vs 19% (9/10 sentences identical CER) |
| Coqui female | 5.9% | 19.9% |
| Mimic3, best speakers (s01/s02), danda->"." | 6.5-6.9% | 22-26% |
| Mimic3, all 16 speakers, raw | 7.4-15.4% | 26-42% |
| 15-sentence pack incl. 5 number sentences, phone: old fp32 raw | 17.5% | 32.1% |
| same pack, **fp16 decoder + normaliser (shipped)** | **5.2%** | **20.9%** |

With the normaliser all six numbers (12, 5, 250, 3, 108, 15) are recognised; the old path drops all of them.
Before this change an ASCII "12" in Bengali text was heard as "দুটি" (two) and ASCII 5/250/3 were lost
(`audio/desktop_mms_digits_ascii_vs_words/`), so mapping Bengali digits to ASCII would have been wrong.

## 6. Alternatives

| model | source | license | size | Android/sherpa | RTF bg / fg | proxy CER | verdict |
|---|---|---|---|---|---|---|---|
| MMS `ben` fp16 decoder | facebook/mms-tts | CC BY-NC 4.0 | 85.4 MB | yes (character frontend) | 1.41 / 0.81 | 3.5-5% | **kept** |
| `vits-mimic3-bn-multi_low` | sherpa-onnx `tts-models` release (MycroftAI mimic3-voices bn/multi_low; data OpenSLR 37 + CMU Indic) | CC BY-SA 4.0 (OpenSLR 37), CMU festvox licence | 76.4 MB + shared espeak-ng-data | yes (espeak, 16 speakers) | 0.49 / 0.21 | 6.5-15% | fallback: meets every speed gate even in background, but ~2x the ASR error; espeak bn G2P loses য়/য-ফলা (দয়া->দ, পেরিয়ে->পেরি) and clips the last syllable unless the danda is mapped to "." |
| `vits-coqui-bn-custom_female` | sherpa-onnx `tts-models` (Coqui `tts_models/bn/custom/vits-female`, @mobassir94) | Apache 2.0 (Coqui model list) | 114.3 MB | yes | 2.87 / not run | 5.9% | rejected: slower than MMS (same decoder, 22.05 kHz) |

Archives: `vits-mimic3-bn-multi_low.tar.bz2` sha256 a921a622e9dac5e0ad4bfe9f4a02b6d15fe6797532213718305e06312b7a0ae3,
`vits-coqui-bn-custom_female.tar.bz2` sha256 a03292d7da03650e892bb1989b40dc2c62574c0d6c34c8bef185fbb3151417a1
(local copies in `D:\iTantra-tts-models\candidates`).

## 7. Gates

| frozen gate | app visible | app not visible |
|---|---|---|
| median warm RTF <= 1.0 | met (0.77) | **not met** (1.37) |
| median text-to-audible-start <= 3 s | met (2.42 s) | **not met** (4.39 s) |
| zero failures in 20 repeats | met (22 runs, 0 errors) | 10 runs, 0 errors |
| >= 9/10 intended meanings, all actionable details | **not assessed**: needs a Bengali listener | same |

The gates do not say which condition applies. The app has no foreground service (the manifest declares none), so
when it is not visible it is also in the scheduling class Android freezes/kills. Vivek decides whether the gate means
"app visible". If receive-while-not-visible is required, the options are a foreground service (normally required for
background Bluetooth receive anyway; gives the `foreground` cpuset = cpu0-7) or Mimic3. One attempt to emulate a
foreground-priority process with `am broadcast --receiver-foreground` gave RTF ~0.8, but that process was then ANR-killed
and the run was inconclusive; do not rely on it.

## 8. Text normalisation status

Handled: Bengali and ASCII digits (as above); precomposed ড় ঢ় য় U+09DC/DD/DF (NFC turns them into letter + nukta,
both in the vocabulary; before, they were silently dropped). Unchanged and still dropped: danda । (sentences run
together without a pause, measured better than splitting), ৳ and other currency signs, vocalic L/LL, ZWJ/ZWNJ.
Not handled: digits separated by commas or decimal points, ordinals, dates, and context (a phone number like
১০৮ is read as the cardinal একশো আট).

## 9. For Vivek to verify

1. Listen to `audio/phone_fp16dec_normalized/01-15.wav` (the shipped path, generated on the phone) against
   `audio/phone_fp32_raw/` and the earlier human-screened set: meaning of each line, the three actionable details,
   and the number lines 09 and 11-15 (is "একশো আট" acceptable for 108?).
2. On the phone with the app **open**, receive or send several Bengali messages and confirm speech starts in about
   2.5 s; also confirm English/Hindi/Malayalam still speak.
3. Decide whether the gates apply only while the app is visible (section 7).
4. Optionally compare `audio/desktop_mimic3_s01_dot_ns0333/` (fast fallback voice) by ear.
