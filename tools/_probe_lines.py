import gzip, re, sys

p = r'E:/wzt/MC/PL genmulu/GT_New_Horizons_2.9.0-beta-3_Java_17-26/.minecraft/saves/world/data/AE2QoLProductionLines.dat'
d = open(p, 'rb').read()
print("size", len(d), "head", d[:8].hex())
raw = d
try:
    raw = gzip.decompress(d)
    print("gzip ok -> nbt bytes", len(raw))
except Exception as e:
    print("not gzip:", e)

for tok in [b'Lines', b'Id', b'Name', b'Steps', b'Schema', b'Cumu', b'SExp', b'Owner']:
    print(tok.decode(), "count =", raw.count(tok))

# 提取可打印 ASCII（NBT 字符串是 UTF-8 长度前缀）
found = re.findall(rb'[0-9A-Za-z_:\-\.\u4e00-\u9fff]{2,40}', raw)
uniq = []
for f in found:
    try:
        s = f.decode('utf-8')
    except Exception:
        continue
    if s not in uniq:
        uniq.append(s)
print("--- strings (first 60) ---")
for s in uniq[:60]:
    print(repr(s))
