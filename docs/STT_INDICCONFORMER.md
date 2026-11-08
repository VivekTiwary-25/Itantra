# IndicConformer STT: model files and how to put them on the phone

The app recognises the Indian languages with IndicConformer-600M (INT8, NeMo CTC) through
sherpa-onnx. English stays on Whisper tiny.en from the APK assets. The IndicConformer files are
about 700 MB, so they are **not** in the APK: they are read from the app's private files folder
by absolute path (`SpeechRecognizerManager.INDIC_CONFORMER_DIR_NAME`, currently `indicconformer`).

## Layout on the phone

```
/data/data/com.chmod777.itantra/files/indicconformer/
  shared/encoder.weights.bin        651,952,128 B, shared by every language
  hi.onnx gu.onnx mr.onnx ta.onnx te.onnx or.onnx bn.onnx   ~5 MB each (encoder graph + head; weights are external)
  kn.onnx ml.onnx                   Kannada and Malayalam (in the picker since Task 5b)
  languages/<lang>/tokens.txt       ~3 KB each
```

The wrappers must sit at the folder root and the weights in `shared/`: ONNX Runtime rejects
external-data paths that leave the model folder. Keep this layout when swapping in another build.

## 1. Stage the files on the PC

```powershell
.\setup-models.ps1 -IndicConformerModel "D:\projects\SIH\iTantra\.indicconformer-600m\multilingual-adapters"
```

This checks every file's size and SHA-256 (known-good list is inside the script) and copies the
files the app needs into `model-staging\indicconformer` (git-ignored). To deliberately use a
different build of the same layout (for example a re-quantised encoder) add
`-IndicConformerSkipHash`. The sizes and layout are still checked.

## 2. Push to the phone (debug build; `run-as` needs a debuggable app)

Install the app first so its private folder exists. Use `-s <serial>` if an emulator is also attached.

```powershell
$adb = "C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$s = "<serial>"                                   # adb devices -l
& $adb -s $s push model-staging\indicconformer /data/local/tmp/indicconformer
& $adb -s $s shell "run-as com.chmod777.itantra sh -c 'mkdir -p files && rm -rf files/indicconformer && cp -r /data/local/tmp/indicconformer files/indicconformer'"
& $adb -s $s shell "rm -rf /data/local/tmp/indicconformer"
& $adb -s $s shell "run-as com.chmod777.itantra ls -lR files/indicconformer"
```

Roughly 0.7 GB: the push takes tens of seconds over USB. The phone needs about 1.5 GB free
during the copy (temporary copy plus final copy).

Uninstalling the app deletes these files; a reinstall with `adb install -r` keeps them.

> **Warning: any uninstall wipes the models.** That includes `adb uninstall`, uninstalling from
> the phone's settings and Android Studio's "uninstall before install". Clearing the app's data
> in Settings wipes them too. (`setup-phone.ps1 -Clean` also uninstalls, but it pushes the models
> back itself, so it is safe as long as it finishes.) Afterwards English still works (Whisper is
> inside the APK) but **every Indian language stops working**. Re-run step 2 above, or
> `setup-phone.ps1 -Serial <serial>` without `-Clean`, before testing any Indian language.
> To check a phone quickly:
> `adb -s <serial> shell "run-as com.chmod777.itantra sh -c 'find files/indicconformer -type f | wc -l'"`
> must print `19`.
>
> This happened on RMX3392 on 2026-10-29: the app was reinstalled, the models were not pushed
> back, and on 2026-11-07 Kannada and Hindi push-to-talk both showed "Didn't catch anything".

If you run the step-2 commands from **Git Bash** rather than PowerShell, put
`export MSYS_NO_PATHCONV=1` first. Otherwise Git Bash rewrites `/data/local/tmp/...` into a
Windows path and `adb push` hangs without copying anything.

## 3. If a model is missing

`SpeechRecognizerManager` logs `IndicConformer files missing for language=...` (tag
`ITANTRA_PERF_STT`) and returns an empty transcript. The app then checks
`SpeechEngine.canTranscribe(language)`. When the model is missing it shows "Speech recognition
for this language isn't installed on this phone. You can still type your message." instead of
"Didn't catch anything", and logs `nothing heard, modelMissing=true` (tag `ITANTRA_LATENCY`).
Nothing crashes. The fix is to push the files again (step 2).

## Rules kept from the earlier prototype

- Load by file path, never from APK assets (no memory spike).
- Release the old recognizer before creating the next one; two live sessions need about 1.3 GB.
- Two threads (six little cores plus two big ones: four threads was slower on the test phone).
