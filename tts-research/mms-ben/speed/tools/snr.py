import wave, numpy as np, sys
def rd(p):
    w = wave.open(p); return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float64) / 32768
for v in sys.argv[1:]:
    snrs, lens = [], []
    for i in range(1, 11):
        a, b = rd("phone_wav/det_fp32_%02d.wav" % i), rd("phone_wav/%s_%02d.wav" % (v, i))
        lens.append(len(a) == len(b)); n = min(len(a), len(b))
        snrs.append(10 * np.log10(np.sum(a[:n] ** 2) / max(np.sum((a[:n] - b[:n]) ** 2), 1e-12)))
    print(v, "same length %d/10" % sum(lens), "SNR vs fp32 dB: median %.1f min %.1f" % (np.median(snrs), min(snrs)))
