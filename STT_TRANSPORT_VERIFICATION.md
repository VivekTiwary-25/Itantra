# STT-to-Bluetooth Physical Verification

## Checkpoint

- Integration branch: `typed-text-integration`
- Typed-text transport commits: `f26fcdd` and `6e95f8f`
- Test result: **GREEN**

## Local model setup

The first STT attempt crashed because the local Whisper model binary
`tiny.en-encoder.int8.onnx` was absent. These large model binaries are ignored
by Git by design. Before building an STT-capable APK, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-models.ps1
```

The script downloads and verifies the STT/TTS model files under
`app/src/main/assets`, including the pinned Dolphin base multilingual INT8 model
used for `hi`, `gu`, `mr`, `ta`, `te`, `or`, and `bn`. Do not commit the
downloaded `.onnx` files.

## Physical test performed

1. Built one debug APK from `typed-text-integration` after local model setup.
2. Installed the same APK on two physical phones.
3. Phone B listened for Bluetooth RFCOMM connections.
4. Phone A connected to Phone B.
5. On Phone A, held `HOLD TO TALK`, spoke an English test phrase, and released.
6. The transcript entered the existing New Message draft flow and was sent only
   by the user's explicit Send action.
7. Phone B's real iTantra Logs screen displayed the received message exactly as:

   ```text
   STT integration test.
   ```

## Scope boundary

This verifies English STT -> editable message draft -> verified Bluetooth
transport -> visible received Log entry. It does not verify received-message
TTS, relay/store-and-forward, Hands-free VAD, or additional language routing.
