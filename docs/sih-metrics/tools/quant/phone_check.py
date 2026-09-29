#!/usr/bin/env python3
"""Phone check for one package with the quant-ladder harness (quant-ladder/phone-proto, app id com.chmod777.itantra.icquant).

1. install the harness APK; 2. stream the package (shared/encoder.weights.bin, <l>.onnx, languages/<l>/tokens.txt) and the
first N check-set clips per language into the app's private filesDir (adb exec-in + run-as, no /data/local/tmp copy);
3. start the activity, wait for logcat ICQ_DONE / ICQ_FAILED; 4. pull results_<name>.json; 5. parity: pass-0 phone text vs
the desktop transcript of the same clip (normalised, measure_wer.normalize); 6. remove the pushed files.
Only run after PART1_PHONE_DONE is in agent-status.txt.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from measure_wer import normalize  # noqa: E402

ADB = r"C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe"
APP = "com.chmod777.itantra.icquant"
ACTIVITY = f"{APP}/com.chmod777.itantra.icproto.ProtoActivity"
LANGS = ["hi", "gu", "mr", "ta", "te", "or", "bn", "kn", "ml"]


def adb(serial: str, *args: str, stdin=None, check=True, text=True) -> str:
    r = subprocess.run([ADB, "-s", serial, *args], stdin=stdin, capture_output=True, text=text, encoding="utf-8" if text else None)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr}")
    return r.stdout


_SHA: dict[Path, str] = {}


def _local_sha(local: Path) -> str:
    if local not in _SHA:
        h = hashlib.sha256()
        with local.open("rb") as f:
            for block in iter(lambda: f.read(1 << 24), b""):
                h.update(block)
        _SHA[local] = h.hexdigest()
    return _SHA[local]


def _remote_sha(serial: str, remote: str) -> str:
    out = adb(serial, "shell", "run-as", APP, "sha256sum", remote, check=False).strip()
    return out.split()[0] if out and not out.startswith("sha256sum:") else ""


def push(serial: str, local: Path, remote: str, attempts: int = 4) -> None:
    """Stream a file into the app's filesDir; verified by SHA-256 on the device (exec-in streams can truncate)."""
    want = _local_sha(local)
    if _remote_sha(serial, remote) == want:
        return
    parent = remote.rsplit("/", 1)[0]
    for attempt in range(1, attempts + 1):
        with local.open("rb") as handle:
            adb(serial, "exec-in", "run-as", APP, "sh", "-c", f"mkdir -p {parent} && cat > {remote}", stdin=handle, check=False)
        if _remote_sha(serial, remote) == want:
            return
        print(f"  push retry {attempt} for {remote}", flush=True)
        time.sleep(2)
    raise RuntimeError(f"could not push {remote} intact after {attempts} attempts")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--serial", required=True)
    p.add_argument("--pkg", type=Path, required=True)
    p.add_argument("--name", required=True)
    p.add_argument("--desktop-run", type=Path, required=True, help="score_package.py run dir on the check set")
    p.add_argument("--data", type=Path, required=True)
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--clips", type=int, default=5)
    p.add_argument("--keep", action="store_true", help="do not delete pushed files at the end")
    a = p.parse_args()
    a.out.mkdir(parents=True, exist_ok=True)
    abi = adb(a.serial, "shell", "getprop", "ro.product.cpu.abi").strip()
    if a.serial.startswith("emulator-") or abi != "arm64-v8a":
        raise SystemExit(f"refusing: {a.serial} abi={abi}")
    print("device", adb(a.serial, "shell", "getprop", "ro.product.model").strip(), abi, flush=True)
    print(adb(a.serial, "shell", "df", "-h", "/data").strip(), flush=True)
    adb(a.serial, "install", "-r", str(a.apk))
    base = f"files/pkgs/{a.name}"
    push(a.serial, a.pkg / "shared" / "encoder.weights.bin", f"{base}/shared/encoder.weights.bin")
    for lang in LANGS:
        push(a.serial, a.pkg / f"{lang}.onnx", f"{base}/{lang}.onnx")
        push(a.serial, a.pkg / "languages" / lang / "tokens.txt", f"{base}/languages/{lang}/tokens.txt")
    desktop = {}
    for lang in LANGS:
        with (a.data / "check" / lang / "manifest.csv").open(encoding="utf-8", newline="") as h:
            rows = list(csv.DictReader(h))[:a.clips]
        with (a.desktop_run / f"{lang}-results.csv").open(encoding="utf-8", newline="") as h:
            hyp = {r["wav"]: r["hypothesis"] for r in csv.DictReader(h)}
        for r in rows:
            wav = a.data / r["wav"]
            push(a.serial, wav, f"files/clips/{lang}/{wav.name}")
            desktop[(lang, wav.name)] = hyp[r["wav"]]
    print("pushed", flush=True)
    adb(a.serial, "logcat", "-c")
    adb(a.serial, "shell", "am", "force-stop", APP)
    adb(a.serial, "shell", "am", "start", "-n", ACTIVITY, "--es", "pkg", a.name, "--ei", "clips", str(a.clips))
    deadline, status = time.time() + 3600, None
    while time.time() < deadline:
        log = adb(a.serial, "logcat", "-d", "-s", "ICQ:*", "AndroidRuntime:E", "libc:F", "DEBUG:F", check=False)
        if "ICQ_DONE" in log or "ICQ_FAILED" in log or "FATAL" in log:
            status = log
            break
        time.sleep(10)
    (a.out / f"logcat_{a.name}.txt").write_text(status or "TIMEOUT", encoding="utf-8")
    raw = adb(a.serial, "exec-out", "run-as", APP, "cat", f"files/results_{a.name}.json", check=False)
    report: dict = {"name": a.name, "serial": a.serial, "logcat_end": (status or "TIMEOUT").strip().splitlines()[-3:]}
    if raw.strip().startswith("{"):
        res = json.loads(raw)
        (a.out / f"results_{a.name}.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
        parity = {}
        for lang in res["languages"]:
            cold = [r for r in lang["runs"] if r["pass"] == 0]
            diff = [r["clip"] for r in cold if normalize(r["text"]) != normalize(desktop[(lang["language"], r["clip"])])]
            parity[lang["language"]] = {"differ": len(diff), "of": len(cold), "clips": diff}
        report.update({"device": res["device"], "peak_rss_MB": res["peak_rss_kB"] / 1024, "peak_pss_MB": res["peak_pss_kB"] / 1024,
                       "warm_rtf_all": res["warm_rtf_all"],
                       "per_language": {l["language"]: {"load_ms": round(l["load_ms"]), "warm_rtf": round(l["warm_rtf"], 4)} for l in res["languages"]},
                       "parity": parity, "parity_pass": all(v["differ"] <= 1 for v in parity.values())})
    else:
        err = adb(a.serial, "exec-out", "run-as", APP, "cat", f"files/results_{a.name}.error.txt", check=False)
        report["error"] = err[-3000:]
    (a.out / f"report_{a.name}.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "parity"}, ensure_ascii=False, indent=1))
    print(json.dumps(report.get("parity"), indent=None))
    if not a.keep:
        adb(a.serial, "shell", "run-as", APP, "rm", "-rf", "files/pkgs", "files/clips", check=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
