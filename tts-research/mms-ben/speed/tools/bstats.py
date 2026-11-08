import re, sys, statistics as st
for path in sys.argv[1:]:
    init = None; gens = []; errs = 0
    for l in open(path, encoding="utf-8", errors="replace"):
        m = re.search(r"bench init tag=(\S+) threads=(\d+) provider=(\S+) ms=(\d+)", l)
        if m: init = m.groups()
        m = re.search(r"bench gen tag=(\S+) cp=(\d+) samples=(\d+) rate=(\d+) ms=(\d+)", l)
        if m: gens.append((int(m[2]), int(m[3]) / int(m[4]), int(m[5])))
        if "bench failed" in l or "FATAL" in l: errs += 1
    if not gens: print(path, "no runs", "errors", errs); continue
    cold, warm = gens[0], gens[1:]
    rtf = [ms / 1000 / d for _, d, ms in warm]
    print("%s | init %s | cold gen %.2fs | warm n=%d median RTF %.2f (%.2f-%.2f) | median synth %.2fs max %.2fs | median audio %.2fs | errors %d"
          % (path.replace("\\", "/").split("/")[-1], init, cold[2] / 1000, len(warm), st.median(rtf), min(rtf), max(rtf),
             st.median(ms for *_, ms in warm) / 1000, max(ms for *_, ms in warm) / 1000, st.median(d for _, d, _ in warm), errs))
