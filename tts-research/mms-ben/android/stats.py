import re,sys,statistics as st
def sec(t):h,m,s=t.split(':');return int(h)*3600+int(m)*60+float(s)
def parse(path):
    runs=[];cur=None
    for l in open(path,encoding='utf-8',errors='replace'):
        ts=re.match(r'\S+ (\S+)',l)
        m=re.search(r'debug speak lang=(\w+) codepoints=(\d+)',l)
        if m: cur={'lang':m[1],'cp':int(m[2]),'t0':sec(ts[1])};runs.append(cur);continue
        if not cur: continue
        if 'initialised' in l: cur['init_end']=sec(ts[1])
        if 'initialising' in l: cur['init_start']=sec(ts[1])
        m=re.search(r'synthesis done lang=\w+ samples=(\d+) rate=(\d+) deltaMs=(\d+)',l)
        if m: cur.update(dur=int(m[1])/int(m[2]),ms=int(m[3]))
        if 'playback start' in l: cur['t_play']=sec(ts[1])
        if 'FATAL' in l or 'Fatal signal' in l: cur['crash']=True
    for r in runs:
        if 'ms' in r: r['rtf']=r['ms']/1000/r['dur']; r['start']=r['t_play']-r['t0']
    return runs
if __name__=='__main__':
    runs=parse(sys.argv[1]); lang=sys.argv[2]
    rs=[r for r in runs if r['lang']==lang and 'ms' in r]
    print(len(runs),'total runs;',len(rs),lang,'with synthesis;',sum(1 for r in runs if r.get('crash')),'crashes')
    for i,r in enumerate(rs): print(i,r['cp'],'dur %.2f synth %.1fs rtf %.2f start %.1fs'%(r['dur'],r['ms']/1000,r['rtf'],r['start']))
    w=rs[1:]
    print('warm(excl first) median rtf %.2f min %.2f max %.2f | median start %.1fs'%(st.median(r['rtf'] for r in w),min(r['rtf'] for r in w),max(r['rtf'] for r in w),st.median(r['start'] for r in w)))
