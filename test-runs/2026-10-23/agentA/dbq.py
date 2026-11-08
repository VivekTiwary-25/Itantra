import sqlite3, sys, shutil, os, tempfile, datetime
# usage: dbq.py <dir with itantra_net.db[-wal]> [sql]
src = sys.argv[1]
tmp = tempfile.mkdtemp()
for f in ("itantra_net.db", "itantra_net.db-wal"):
    p = os.path.join(src, f)
    if os.path.exists(p):
        shutil.copy(p, os.path.join(tmp, f))
con = sqlite3.connect(os.path.join(tmp, "itantra_net.db"))
sql = sys.argv[2] if len(sys.argv) > 2 else "select local_name, fingerprint, trust_method, created_at, node_id from contacts"
for row in con.execute(sql):
    out = []
    for v in row:
        if isinstance(v, bytes):
            v = v.hex()
        elif isinstance(v, int) and v > 1_600_000_000_000:
            v = f"{v} ({datetime.datetime.fromtimestamp(v/1000)})"
        out.append(str(v))
    print(" | ".join(out))
