import csv
def load(path):
    rows=[]
    with open(path,encoding='utf-8',errors='replace') as f:
        for row in csv.reader(f):
            if row: rows.append(row)
    return rows
ores=load('oredict.csv')
for kw in ['dustMeatRaw','dustMysteriousCrystal','cellFermentationBase']:
    print(f"=== {kw} ===")
    for r in ores:
        if kw.lower() in '|'.join(r).lower():
            print(r)
