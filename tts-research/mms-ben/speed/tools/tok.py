# Replicates sherpa-onnx's MMS "characters" frontend: map chars via tokens.txt, drop unknown, intersperse blank 0.
def load_tokens(p):
    t = {}
    for line in open(p, encoding="utf-8").read().split("\n"):
        if not line: continue
        sym, idx = line.rsplit(" ", 1) if not line.startswith(" ") else (" ", line.split()[-1])
        t[sym] = int(idx)
    return t
def encode(text, t, add_blank=True):
    ids = [t[c] for c in text.lower() if c in t]
    if not add_blank: return ids
    out = [0]
    for i in ids: out += [i, 0]
    return out
