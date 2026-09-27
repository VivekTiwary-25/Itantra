# RUN 1 desktop inference commands

Run from the specified candidate worktree. These are the commands that generated the ten retained WAVs. `TTS_LOG.md` records the official asset URLs, SHA-256 values, setup fixes and ONNX results. The test files are unapproved drafts.

The Indic-TTS commands use WSL Ubuntu, Python 3.10.21, CPU torch/torchaudio 2.2.2, Coqui TTS 0.22.0, ONNX 1.17.0 and ONNX Runtime 1.18.1. The original release archives are extracted under `D:\iTantra-tts-models\<code>`. The only config edit is external `fastpitch/config-local.json`, replacing the stale speaker-file path with `/mnt/d/iTantra-tts-models/<code>/fastpitch/speakers.pth` in both `speakers_file` fields. No original checkpoint or app source was changed.

## Bengali — `D:\iTantra-tts-bn-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-bn-indic/tools/indic_tts_desktop_probe.py --language-code bn --model-dir /mnt/d/iTantra-tts-models/bn --input-file /mnt/d/iTantra-tts-bn-indic/tts-research/inputs/bn.txt --output-dir /mnt/d/iTantra-tts-bn-indic/local-recordings/bn-indic'
```

## Gujarati — `D:\iTantra-tts-gu-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-gu-indic/tools/indic_tts_desktop_probe.py --language-code gu --model-dir /mnt/d/iTantra-tts-models/gu --input-file /mnt/d/iTantra-tts-gu-indic/tts-research/inputs/gu.txt --output-dir /mnt/d/iTantra-tts-gu-indic/local-recordings/gu-indic'
```

## Marathi — `D:\iTantra-tts-mr-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-mr-indic/tools/indic_tts_desktop_probe.py --language-code mr --model-dir /mnt/d/iTantra-tts-models/mr --input-file /mnt/d/iTantra-tts-mr-indic/tts-research/inputs/mr.txt --output-dir /mnt/d/iTantra-tts-mr-indic/local-recordings/mr-indic'
```

## Kannada — `D:\iTantra-tts-kn-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-kn-indic/tools/indic_tts_desktop_probe.py --language-code kn --model-dir /mnt/d/iTantra-tts-models/kn --input-file /mnt/d/iTantra-tts-kn-indic/tts-research/inputs/kn.txt --output-dir /mnt/d/iTantra-tts-kn-indic/local-recordings/kn-indic'
```

## Malayalam Arjun — `D:\iTantra-tts-ml`

```powershell
.\.gradle\tts-venv\Scripts\python.exe tools\tts_desktop_probe.py --language-code ml --model D:\iTantra-tts-models\vits-piper-ml_IN-arjun-medium\ml_IN-arjun-medium.onnx --tokens D:\iTantra-tts-models\vits-piper-ml_IN-arjun-medium\tokens.txt --data-dir D:\iTantra-tts-models\vits-piper-ml_IN-arjun-medium\espeak-ng-data --input-file tts-research\inputs\ml.txt --output-dir local-recordings\ml-arjun
```

## Tamil — `D:\iTantra-tts-ta-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-ta-indic/tools/indic_tts_desktop_probe.py --language-code ta --model-dir /mnt/d/iTantra-tts-models/ta --input-file /mnt/d/iTantra-tts-ta-indic/tts-research/inputs/ta.txt --output-dir /mnt/d/iTantra-tts-ta-indic/local-recordings/ta-indic'
```

## Telugu — `D:\iTantra-tts-te-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-te-indic/tools/indic_tts_desktop_probe.py --language-code te --model-dir /mnt/d/iTantra-tts-models/te --input-file /mnt/d/iTantra-tts-te-indic/tts-research/inputs/te.txt --output-dir /mnt/d/iTantra-tts-te-indic/local-recordings/te-indic'
```

## Odia — `D:\iTantra-tts-or-indic`

```powershell
wsl -d Ubuntu -- bash -lc '~/tts-bn-venv/bin/python /mnt/d/iTantra-tts-or-indic/tools/indic_tts_desktop_probe.py --language-code or --model-dir /mnt/d/iTantra-tts-models/or --input-file /mnt/d/iTantra-tts-or-indic/tts-research/inputs/or.txt --output-dir /mnt/d/iTantra-tts-or-indic/local-recordings/or-indic'
```

The four Indic-TTS ONNX export tests used each branch's `tools/export_indic_onnx.py` with the same model/input/output arguments; their exact output is retained in `local-recordings/<code>-indic/onnx/export.log`. No ONNX export was attempted for Tamil, Telugu or Odia after the frontend discarded tested native characters. Malayalam used an official ONNX package and needed no export.
