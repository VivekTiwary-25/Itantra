# Bengali MMS INT8 experiment — result: NEGATIVE (INT8 is ~2.7x slower than fp32)

Date 2026-09-26. Starting point: checkpoint tag `checkpoint/bn-mms-fp32-8001c37`. RMX3392, bn at 4 threads, same 10 sentences, same driver, same order as fp32.
**QUALITY UNVERIFIED — nobody has listened. Not GREEN.**

## Method
ORT dynamic quantization (`quantize_bn.py`, `quantize_dynamic`, QUInt8 weights, per-tensor); graph, frontend and metadata unchanged (sherpa metadata preserved).
| variant | bytes | sha256 | note |
|---|---|---|---|
| fp32 (baseline) | 114,065,046 | f8f7bf4f0b703f706e0531ff2b364bf9a3ebb8bed69262258f543e0212094e71 | |
| int8 MatMul only | 114,404,484 | 0ea27d4b1267a2648ca6b3190a770a0425fb3d67123c3b4e774b6f7aaf3df778 | no size gain (weights are Conv), not tested further |
| int8 MatMul+Conv | 38,067,490 | be7d05a7d78cc20c4235980ca880b8346710ec3e979a414c7474e96cae358ea73 | tested |

Not tried (out of scope by instruction / for the next decision): static/QDQ calibrated quantization, fp16, ORT graph optimization, NNAPI, sentence chunking.

## Desktop (sherpa-onnx 1.13.7)
Loads and synthesizes all 20 lines; durations and RMS/peak match fp32 closely (e.g. line 1: 3.43 s vs 3.41 s, RMS 0.178 vs 0.177), i.e. no evidence of corrupted output (pronunciation not judged).
But slower: desktop RTF ~3.6-4.3 vs fp32 0.4-1.4 (host noisy). Evidence: `evidence/matmul_conv/`.

## RMX3392 comparison (fp32 = `../android/final_logcat.log`, INT8 = `int8_logcat.log`)
| | fp32 | INT8 (MatMul+Conv) |
|---|---|---|
| model file | 114,065,046 B | 38,067,490 B |
| APK, clean build | 608,240,873 B | 531,711,369 B (-76,529,504) |
| on-device init (model load) | 4.1 s | 5.4 s |
| cold first synthesis | 8.1 s | 21.7 s |
| cold text-to-audible-start (incl. asset copy) | 14.1 s | 27.9 s |
| warm median RTF (pass1 / pass2) | 2.46 / 2.46 | 6.69 / 6.67 |
| warm RTF range | 2.11-2.73 | 5.97-8.10 |
| warm median text-to-audible-start | 6.9 s (max 9.0) | 19.8 s (max 26.6) |
| PSS, Bengali only | 299,622 KB | 240,487 KB |
| PSS, all four voices loaded | 728,244 KB | 681,050 KB |
| stability | 22 bn runs, 0 errors | 22 bn runs, 0 errors, 0 crashes |
| output generation | ok, codepoints match input | ok, codepoints match input (44,37,28,43,51,45,32,27,34,44 both passes) |
| English / Hindi / Malayalam RTF | 0.65-0.72 / 0.62-0.68 / 0.69 | 0.66-0.70 / 0.66-0.68 / 0.67-0.70 (unchanged) |

Frozen gates are warm RTF <= 1.0 and start <= 3 s. INT8 fails by a wide margin and is worse than fp32 on every speed metric; it only saves size/memory.

## Likely cause (not verified by a profile)
Dynamic quantization rewrites the 183 Conv ops as ConvInteger, which ONNX Runtime executes with unoptimized CPU kernels on both x86 and this ARM phone, and it adds per-call activation quantization. This is a hypothesis; no profiler run was done.

## Data-integrity note
The first INT8 run (`int8_CONTAMINATED_two_drivers_logcat.log`, 39 syntheses) is contaminated: a forked helper subagent in this session also drove the phone at the same time, so it was discarded. Its trend agrees with the clean run (median RTF ~6.8), but all numbers above come from the clean single-driver rerun.
After the experiment the fp32 model was restored (hash verified), the fp32 APK was rebuilt clean and reinstalled. The INT8 model files are not committed (they stay in `D:\iTantra-tts-models\mms-ben\`).
