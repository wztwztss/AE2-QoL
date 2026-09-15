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

def search(rows,keywords,col=None):
    out=[]
    for row in rows:
        text=' | '.join(row)
        if all(k.lower() in text.lower() for k in keywords):
            out.append(row)
    return out

print("=== 重地泥 (item) ===")
for r in search(items,['重地泥']): print(r)
print("=== 重地泥 (oredict) ===")
for r in search(ores,['重地泥']): print(r)
print("=== hellishmud (item) ===")
for r in search(items,['hellishmud']): print(r)
print("=== hellishmud (oredict) ===")
for r in search(ores,['hellishmud']): print(r)
print("=== 硅岩基 (fluid) ===")
for r in search(fluids,['硅岩基']): print(r)
print("=== naquadah based (fluid) ===")
for r in search(fluids,['naquadah based']): print(r)
print("=== 电子 (oredict particle) ===")
for r in search(ores,['particleElectron']): print(r)
print("=== 中子 (oredict particle) ===")
for r in search(ores,['particleNeutron']): print(r)
print("=== 质子 (oredict particle) ===")
for r in search(ores,['particleProton']): print(r)
print("=== 氢等离子体 (fluid) ===")
for r in search(fluids,['氢等离子体']): print(r)
print("=== plasma.hydrogen (fluid) ===")
for r in search(fluids,['plasma.hydrogen']): print(r)
print("=== 氯化铵 (item) ===")
for r in search(items,['氯化铵']): print(r[:4])
print("=== ammonium chloride (oredict) ===")
for r in search(ores,['AmmoniumChloride']): print(r)
