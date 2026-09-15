import csv
def load(path):
    rows=[]
    with open(path,encoding='utf-8',errors='replace') as f:
        for row in csv.reader(f):
            if row: rows.append(row)
    return rows
fluids=load('fluid.csv')
# 搜索含镧/锶/硼/等离子体的流体
keywords = ['lanthanum','strontium','praseodymium','boron','plasma','radioactive','锶','镧','镨','硼']
for r in fluids:
    text=' | '.join(r).lower()
    for kw in keywords:
        if kw.lower() in text:
            print(r[:3])
            break
