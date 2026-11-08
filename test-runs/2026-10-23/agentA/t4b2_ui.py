import sys,re,xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding="utf-8")
r=ET.parse(sys.argv[1]).getroot()
for n in r.iter('node'):
    t=n.get('text') or ''; d=n.get('content-desc') or ''
    if t or d or n.get('clickable')=='true' and False:
        b=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        print(f"{t!r}|{d!r}|({(b[0]+b[2])//2},{(b[1]+b[3])//2})|clk={n.get('clickable')}|{n.get('class').split('.')[-1]}")
