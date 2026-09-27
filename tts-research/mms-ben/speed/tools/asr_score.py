# Intelligibility PROXY: IndicConformer-600M (bn) transcribes synthesized WAVs; CER/WER vs input text.
# Not a substitute for a native listener.
import sys, json, glob, os, re, unicodedata
import numpy as np, soundfile as sf, torch, torchaudio
MODEL = r"D:\projects\sih\itantra\.indicconformer-600m\model"
sys.path.insert(0, MODEL)
from model_onnx import IndicASRConfig, IndicASRModel
cfg = json.load(open(MODEL + r"\config.json"))
model = IndicASRModel(IndicASRConfig(ts_folder=MODEL, **{k: cfg[k] for k in ("BLANK_ID", "RNNT_MAX_SYMBOLS", "PRED_RNN_LAYERS", "PRED_RNN_HIDDEN_DIM", "SOS")}))
refs = [l.strip() for l in open(os.environ.get("REFS", "bn_input.txt"), encoding="utf-8") if l.strip()]
sys.path.insert(0, "D:/projects/sih/itantra-tts/tools/vendor")
from indic_numtowords.ben.cardinal import convert as _conv
def _num(m):
    d = "".join(str("০১২৩৪৫৬৭৮৯".index(c)) if c in "০১২৩৪৫৬৭৮৯" else c for c in m.group())
    return " ".join(_conv(int(c))[0] for c in d) if len(d) > 9 or (len(d) > 1 and d[0] == "0") else _conv(int(d))[0]
def norm(s):
    s = re.sub(r"[0-9০-৯]+", _num, s)
    s = unicodedata.normalize("NFC", s)
    s = re.sub(r"[।॥.,!?;:\"'()\-]", " ", s)
    return re.sub(r"\s+", " ", s).strip()
def ed(a, b):
    d = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        p, d[0] = d[0], i
        for j in range(1, len(b) + 1):
            p, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, p + (a[i - 1] != b[j - 1]))
    return d[len(b)]
def load(path):
    a, sr = sf.read(path, dtype="float32", always_2d=True)
    w = torch.from_numpy(a.T).mean(0, keepdim=True)
    return torchaudio.transforms.Resample(sr, 16000)(w) if sr != 16000 else w
out = {}
for d in sys.argv[1:]:
    rows = []
    for i, ref in enumerate(refs):
        if not os.path.exists(os.path.join(d, "%02d.wav" % (i + 1))): continue
        f = os.path.join(d, "%02d.wav" % (i + 1))
        with torch.no_grad():
            hyp = model(load(f), "bn", "ctc")
        r, h = norm(ref), norm(hyp)
        rows.append({"i": i + 1, "hyp": hyp, "cer": ed(r, h) / len(r), "wer": ed(r.split(), h.split()) / len(r.split())})
    name = "mms_human_screened" if d.endswith("mms-ben/evidence/raw") else d.replace("\\", "/").rstrip("/").split("/")[-1]
    out[name] = rows
    print("%-22s CER %.3f  WER %.3f  | per-sentence CER %s" % (name, np.mean([x["cer"] for x in rows]), np.mean([x["wer"] for x in rows]),
          " ".join("%.2f" % x["cer"] for x in rows)), flush=True)
json.dump(out, open("asr_%d.json" % len(os.listdir(".")), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
