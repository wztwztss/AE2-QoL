import csv

def load(path):
    rows=[]
    with open(path,encoding='utf-8',errors='replace') as f:
        r=csv.reader(f)
        for row in r:
            if row: rows.append(row)
    return rows

items=load('item.csv')
fluids=load('fluid.csv')
ores=load('oredict.csv')

def search(rows,keywords):
    out=[]
    for row in rows:
        text=' | '.join(row)
        if all(k.lower() in text.lower() for k in keywords):
            out.append(row)
    return out

print("=== 氯化铵 (fluid) ===")
for r in search(fluids,['氯化铵']): print(r)
print("=== ammonium chloride (fluid) ===")
for r in search(fluids,['ammonium chloride']): print(r)
print("=== ammonchlor (fluid) ===")
for r in search(fluids,['ammonchlor']): print(r)
print("=== gt.metaitem.03 重地/hellish (item) ===")
for r in items:
    if 'gt.metaitem.03' in str(r) and ('重地' in str(r) or 'hellish' in str(r).lower() or 'mud' in str(r).lower()):
        print(r)
print("=== 神明不愿 (item) ===")
for r in items:
    if '神明' in str(r): print(r)
print("=== gt.metaitem.03 all with mud/hellish (oredict) ===")
for r in ores:
    if 'gt.metaitem.03' in str(r) and ('mud' in str(r).lower() or 'hellish' in str(r).lower() or '重地' in str(r)):
        print(r)
