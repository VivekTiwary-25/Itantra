import wave, numpy as np

def rd(p):
    w = wave.open(p)
    return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float64) / 32768

snrs, lens = [], []
for i in range(1, 11):
    a = rd(r"D:\iTantra-tts-models\mms-tel\evidence\det_fp32_%02d.wav" % i)
    b = rd(r"D:\iTantra-tts-models\mms-tel\evidence\det_fp16dec_%02d.wav" % i)
    lens.append(len(a) == len(b))
    n = min(len(a), len(b))
    snr = 10 * np.log10(np.sum(a[:n] ** 2) / max(np.sum((a[:n] - b[:n]) ** 2), 1e-12))
    snrs.append(snr)
    print(i, len(a), len(b), "%.1f dB" % snr)

print("same length %d/10" % sum(lens), "SNR vs fp32 dB: median %.1f min %.1f" % (np.median(snrs), min(snrs)))
