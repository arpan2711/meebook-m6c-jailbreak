import tarfile,csv,os,sys,re,json
B=sys.argv[1]; t=tarfile.open(os.path.join(B,'device-books.tar'))
rows=list(csv.DictReader(open(os.path.join(B,'export-map.tsv'),encoding='utf-8'),delimiter='\t'))
# identical-content duplicates share a sha256; let an sdr follow its twin to the kept copy
man=list(csv.DictReader(open(os.path.join(B,'manifest.tsv'),encoding='utf-8'),delimiter='\t'))
sha={r['device_path']:r['sha256'] for r in man}
twins={}
for r in man: twins.setdefault(r['sha256'],[]).append(r['device_path'])
for r in list(rows):
    for p in twins.get(sha.get(r['device_path']),[]):
        if p!=r['device_path']: rows.append(dict(r,device_path=p))
sdrs={}
for m in t.getmembers():
    if '.sdr/' in m.name and m.isfile():
        sdrs.setdefault(m.name.split('.sdr/')[0],[]).append(m)
moved=lost=0; notes=[]
for base,members in sdrs.items():
    norm=lambda x: re.sub(r'[^A-Za-z0-9]+','',x)
    hit=next((r for r in rows if norm(os.path.splitext(r['device_path'][len('/sdcard/'):])[0])==norm(base)),None)
    if not hit: lost+=1; notes.append('no book kept for: '+base); continue
    old_ext=os.path.splitext(hit['device_path'])[1].lower(); new_ext=os.path.splitext(hit['new_rel_path'])[1].lower()
    if old_ext!=new_ext: lost+=1; notes.append('format changed %s->%s: %s'%(old_ext,new_ext,base)); continue
    newbase=os.path.splitext(hit['new_rel_path'])[0]
    newdoc='/storage/emulated/0/Books/'+hit['new_rel_path']
    for m in members:
        inner=m.name.split('.sdr/',1)[1]
        dst=os.path.join(B,'export',*(newbase+'.sdr').split('/'),*inner.split('/'))
        os.makedirs(os.path.dirname(dst),exist_ok=True)
        data=t.extractfile(m).read()
        if inner.startswith('metadata.') and inner.endswith('.lua'):
            s=data.decode('utf-8','replace')
            s=re.sub(r'(\["doc_path"\]\s*=\s*)"[^"]*"',lambda mm: mm.group(1)+json.dumps(newdoc,ensure_ascii=False),s)
            data=s.encode('utf-8')
        open(dst,'wb').write(data)
    moved+=1
print('sdr moved',moved,'not carried',lost); print('\n'.join(notes))
