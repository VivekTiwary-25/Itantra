# Fingerprint/node id an iTantra identity would have if both private keys were all zero bytes.
import hashlib, importlib.util, os
spec = importlib.util.spec_from_file_location("m", os.path.join(os.path.dirname(os.path.abspath(__file__)), "make_test_qrs_lib.py"))
p = 2**255 - 19
def x25519_base(k):
    k = bytearray(k); k[0] &= 248; k[31] &= 127; k[31] |= 64
    n = int.from_bytes(k, "little")
    x1, x2, z2, x3, z3, swap = 9, 1, 0, 9, 1, 0
    a24 = 121665
    for t in reversed(range(255)):
        kt = (n >> t) & 1
        swap ^= kt
        if swap: x2, x3, z2, z3 = x3, x2, z3, z2
        swap = kt
        A = (x2 + z2) % p; AA = A*A % p; Bv = (x2 - z2) % p; BB = Bv*Bv % p; E = (AA - BB) % p
        C = (x3 + z3) % p; D = (x3 - z3) % p; DA = D*A % p; CB = C*Bv % p
        x3 = (DA + CB) ** 2 % p; z3 = x1 * (DA - CB) ** 2 % p
        x2 = AA*BB % p; z2 = E * (AA + a24*E) % p
    if swap: x2, x3, z2, z3 = x3, x2, z3, z2
    return (x2 * pow(z2, p - 2, p) % p).to_bytes(32, "little")
# Ed25519 public key of the all-zero seed (RFC 8032 derivation, same code as make_test_qrs.py)
d = -121665 * pow(121666, p - 2, p) % p
I = pow(2, (p - 1) // 4, p)
def xrecover(y):
    xx = (y*y - 1) * pow(d*y*y + 1, p - 2, p); x = pow(xx, (p + 3) // 8, p)
    if (x*x - xx) % p: x = x * I % p
    if x % 2: x = p - x
    return x
By = 4 * pow(5, p - 2, p) % p; B = (xrecover(By), By)
def add(P, Q):
    x1, y1 = P; x2, y2 = Q
    return ((x1*y2 + x2*y1) * pow(1 + d*x1*x2*y1*y2, p - 2, p) % p, (y1*y2 + x1*x2) * pow(1 - d*x1*x2*y1*y2, p - 2, p) % p)
def mul(P, e):
    Q = (0, 1)
    while e:
        if e & 1: Q = add(Q, P)
        P = add(P, P); e >>= 1
    return Q
h = hashlib.sha512(bytes(32)).digest()
a = int.from_bytes(h[:32], "little"); a &= (1 << 254) - 8; a |= 1 << 254
x, y = mul(B, a)
spk = (y | ((x & 1) << 255)).to_bytes(32, "little")
epk = x25519_base(bytes(32))
print("ed25519 pk(zero seed) =", spk.hex())
print("x25519 pk(zero key)   =", epk.hex())
fp = hashlib.sha256(b"itantra-v1/fingerprint" + spk + epk).digest()[:16].hex()
print("fingerprint =", " ".join(fp[i:i+4] for i in range(0, 32, 4)))
print("node id     =", hashlib.sha256(b"itantra-v1/node-id" + spk + epk).digest()[:16].hex())
