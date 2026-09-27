import sys, json, glob
import soundfile as sf, torch, torchaudio
MODEL = r"D:\projects\sih\itantra\.indicconformer-600m\model"; sys.path.insert(0, MODEL)
from model_onnx import IndicASRConfig, IndicASRModel
cfg = json.load(open(MODEL + r"\config.json"))
model = IndicASRModel(IndicASRConfig(ts_folder=MODEL, **{k: cfg[k] for k in ("BLANK_ID", "RNNT_MAX_SYMBOLS", "PRED_RNN_LAYERS", "PRED_RNN_HIDDEN_DIM", "SOS")}))
for f in sorted(glob.glob(sys.argv[1])):
    a, sr = sf.read(f, dtype="float32", always_2d=True); w = torch.from_numpy(a.T).mean(0, keepdim=True)
    if sr != 16000: w = torchaudio.transforms.Resample(sr, 16000)(w)
    with torch.no_grad(): print(f.replace("\\", "/").split("/")[-1], "|", model(w, "bn", "ctc"), flush=True)
