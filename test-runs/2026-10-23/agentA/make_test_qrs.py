# Builds test QR images for Stage 3 test 1 (wrong / bad codes). Writes into agentA\qr\.
# A = not an iTantra code; B = I2221's real code with one signature byte flipped;
# C = a VALID, correctly self-signed capsule for a made-up third person who calls itself "Utkarsh Keshri".
import base64, hashlib, os, sys
import cv2, numpy as np

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "qr")
os.makedirs(OUT, exist_ok=True)
REAL_I2221 = sys.argv[1]

# ---- RFC 8032 Ed25519 (reference, slow but fine) ----
p = 2**255 - 19
L = 2**252 + 27742317777372353535851937790883648493
d = -121665 * pow(121666, p - 2, p) % p
I = pow(2, (p - 1) // 4, p)
def xrecover(y):
    xx = (y*y - 1) * pow(d*y*y + 1, p - 2, p)
    x = pow(xx, (p + 3) // 8, p)
    if (x*x - xx) % p != 0: x = x * I % p
    if x % 2 != 0: x = p - x
    return x
By = 4 * pow(5, p - 2, p) % p
B = (xrecover(By), By)
def add(P, Q):
    x1, y1 = P; x2, y2 = Q
    x3 = (x1*y2 + x2*y1) * pow(1 + d*x1*x2*y1*y2, p - 2, p)
    y3 = (y1*y2 + x1*x2) * pow(1 - d*x1*x2*y1*y2, p - 2, p)
    return (x3 % p, y3 % p)
def mul(P, e):
    Q = (0, 1)
    while e:
        if e & 1: Q = add(Q, P)
        P = add(P, P); e >>= 1
    return Q
def enc(P):
    x, y = P
    return (y | ((x & 1) << 255)).to_bytes(32, "little")
def H(m): return hashlib.sha512(m).digest()
def keypair(seed):
    h = H(seed)
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8; a |= 1 << 254
    return a, h[32:], enc(mul(B, a))
def sign(seed, msg):
    a, prefix, A = keypair(seed)
    r = int.from_bytes(H(prefix + msg), "little") % L
    R = enc(mul(B, r))
    k = int.from_bytes(H(R + A + msg), "little") % L
    S = (r + k * a) % L
    return R + S.to_bytes(32, "little")

# ---- minimal canonical CBOR ----
def head(major, n):
    if n < 24: return bytes([major << 5 | n])
    if n < 256: return bytes([major << 5 | 24, n])
    if n < 65536: return bytes([major << 5 | 25]) + n.to_bytes(2, "big")
    return bytes([major << 5 | 26]) + n.to_bytes(4, "big")
def cbor(v):
    if isinstance(v, int): return head(0, v)
    if isinstance(v, bytes): return head(2, len(v)) + v
    if isinstance(v, str): b = v.encode(); return head(3, len(b)) + b
    if isinstance(v, dict): return head(5, len(v)) + b"".join(cbor(k) + cbor(x) for k, x in sorted(v.items()))
    raise TypeError(v)

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def unb64(s): return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))

def save(name, text):
    enc_ = cv2.QRCodeEncoder.create()
    img = enc_.encode(text)
    img = cv2.resize(img, (img.shape[1] * 12, img.shape[0] * 12), interpolation=cv2.INTER_NEAREST)
    img = cv2.copyMakeBorder(img, 60, 60, 60, 60, cv2.BORDER_CONSTANT, value=255)
    path = os.path.join(OUT, name + ".png")
    cv2.imwrite(path, img)
    back = cv2.QRCodeDetector().detectAndDecode(cv2.imread(path))[0]
    print(name, "roundtrip_ok=", back == text, "|", text[:60] + ("..." if len(text) > 60 else ""))

# A: garbage
save("A_not_itantra", "HELLO-THIS-IS-NOT-AN-ITANTRA-CODE-agentA-test")

# B: tamper the last signature byte of I2221's real code
raw = bytearray(unb64(REAL_I2221.removeprefix("ITANTRA1:")))
raw[-1] ^= 0x01
save("B_bad_signature", "ITANTRA1:" + b64(bytes(raw)))

# C: valid impostor capsule
seed = hashlib.sha256(b"agentA-impostor-seed-2026-10-23").digest()
spk = keypair(seed)[2]
epk = hashlib.sha256(b"agentA-impostor-x25519").digest()
capsule = cbor({1: 1, 2: spk, 3: epk, 4: "Utkarsh Keshri", 5: 1})
sig = sign(seed, b"itantra-v1/capsule" + capsule)
fp = hashlib.sha256(b"itantra-v1/fingerprint" + spk + epk).digest()[:16].hex()
print("C fingerprint (expected on phone):", " ".join(fp[i:i+4] for i in range(0, 32, 4)))
save("C_impostor_valid", "ITANTRA1:" + b64(cbor({1: capsule, 2: sig})))

# sanity: decode real I2221 capsule the same way and recompute its fingerprint
real = unb64(REAL_I2221.removeprefix("ITANTRA1:"))
print("real code bytes:", len(real))
