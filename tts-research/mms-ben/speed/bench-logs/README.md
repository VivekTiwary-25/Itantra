# Bench-receiver logs (DebugTtsBenchReceiver: sherpa-onnx `generate` only, no playback)

One driver at a time (`tools/bench.sh`), sentences `tts-research/inputs/bn.txt` in file order: 1 cold/first
sentence (excluded) + 10 warm. RTF = generate time / returned audio duration (after sherpa's silence
shortening, same definition as the earlier app runs). Summaries come from `tools/bstats.py <log>`.

Condition = the app process cpuset while the run happened:
- `background`: cpu0-3 only (four Cortex-A55 2.0 GHz). This is what every earlier Bengali measurement used.
- `top-app`: cpu0-7 (six A55 + two Cortex-A78 2.5 GHz), app visible.

| log prefix | condition | how the condition is known | use |
|---|---|---|---|
| `s1_*`, `s2_*` | background | run before the cpuset issue was found, launcher focused; `/proc/<pid>/cpuset` read `background` right after `s2_*`; numbers match the guarded `bg_*` runs | candidate screen, thread/ORT sweeps, fp32 profile |
| `fg_mms_t*`, `fg_mimic3_*`, `fg_fp16all_t*`, `fg_fp16dec_t4` | top-app | cpuset printed `top-app` right after each run in the same command | foreground sweeps |
| `fg_dec16full_t4`, `fg_dec16noct_t4`, `fg_fp16all_prof` | top-app (not re-checked) | same session, app kept in front; timings consistent with the checked runs | whole-decoder fp16 timing, fp16 profile |
| `bg_*`, `listen_*` | as named | automatic guard in `bench.sh` (`cond start=... end=...` line at the end of each log) | background fp16/fp32; listening pack |
| `det_*` | top-app | deterministic runs (noise 0, `silence_scale` 1 on the last `det_*` set) | numerical comparison only; timings not used |
| `q_*` | **CONTAMINATED / mixed**: `q_*_r0`, `q_*_r1` ran in background, `q_*_r2` partly | the app left the foreground mid-session; found afterwards | audio only (paired fp32 vs fp16 ASR check); **timings excluded** |
