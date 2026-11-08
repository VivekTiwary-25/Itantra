import json,sys,collections,datetime
since=int(sys.argv[2]); c=collections.Counter(); hits=[]
for l in open(sys.argv[1],encoding='utf-8'):
  try: e=json.loads(l)
  except: continue
  if e.get('wall_ms',0)<since: continue
  c[e.get('ev')]+=1
  if e.get('ev') not in ('frame_tx','frame_rx','dtn_inventory_tx','dtn_inventory_rx') or e.get('wants') or ('596de98a' in l or 'a10b5860' in l): hits.append(l.strip())
  last=e
print(dict(c)); print('last', datetime.datetime.fromtimestamp(last['wall_ms']/1000), last['ev'])
for h in hits: print(h)
