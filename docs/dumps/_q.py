import csv, io

targets = ['糖','钐精','硝酸铵','蒸汽','铈粉','铕粉','钆粉','磷酸盐','钍粉','氯甲烷','熔融铿','铿铀','质子','重地','天呐','粉碎的独居石','方钍石','金红石粉','钛铁矿粉','铀-238','铀-235','铪锭','锆锭','锆粉','钐粉']

print('=== item.csv ===')
with io.open('item.csv', encoding='utf-8-sig') as f:
    for row in csv.reader(f):
        if len(row)>=3:
            for t in targets:
                if t in '|'.join(row):
                    print(t + ': ' + row[0])
                    break

print()
print('=== fluid.csv ===')
with io.open('fluid.csv', encoding='utf-8-sig') as f:
    for row in csv.reader(f):
        if len(row)>=3:
            for t in targets:
                if t in '|'.join(row):
                    print(t + ': ' + str(row[:3]))
                    break

print()
print('=== oredict ===')
with io.open('oredict.csv', encoding='utf-8-sig') as f:
    for row in csv.reader(f):
        if len(row)>=3:
            for t in targets:
                if t in '|'.join(row):
                    print(t + ': ' + str(row[:4]))
                    break
