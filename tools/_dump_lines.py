"""解析 AE2QoLProductionLines.dat（gzip + NBT），列出全部产线。
一次性的只读取证工具，不改存档。
"""
import gzip
import struct
import sys

PATH = (r'E:/wzt/MC/PL genmulu/GT_New_Horizons_2.9.0-beta-3_Java_17-26'
        r'/.minecraft/saves/world/data/AE2QoLProductionLines.dat')


class R:
    def __init__(self, b):
        self.b, self.i = b, 0

    def u1(self):
        v = self.b[self.i]; self.i += 1; return v

    def u2(self):
        v = struct.unpack_from('>H', self.b, self.i)[0]; self.i += 2; return v

    def i4(self):
        v = struct.unpack_from('>i', self.b, self.i)[0]; self.i += 4; return v

    def i8(self):
        v = struct.unpack_from('>q', self.b, self.i)[0]; self.i += 8; return v

    def f4(self):
        v = struct.unpack_from('>f', self.b, self.i)[0]; self.i += 4; return v

    def f8(self):
        v = struct.unpack_from('>d', self.b, self.i)[0]; self.i += 8; return v

    def s(self):
        n = self.u2()
        v = self.b[self.i:self.i + n].decode('utf-8', 'replace')
        self.i += n
        return v

    def payload(self, t):
        if t == 1: return self.u1()
        if t == 2: return self.u2()
        if t == 3: return self.i4()
        if t == 4: return self.i8()
        if t == 5: return self.f4()
        if t == 6: return self.f8()
        if t == 7:
            n = self.i4()
            v = list(self.b[self.i:self.i + n]); self.i += n
            return v
        if t == 8: return self.s()
        if t == 9:
            e = self.u1(); n = self.i4(); return [self.payload(e) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = self.u1()
                if tt == 0: return d
                nm = self.s()
                d[nm] = self.payload(tt)
        if t == 11:
            n = self.i4(); return [self.i4() for _ in range(n)]
        if t == 12:
            n = self.i4(); return [self.i8() for _ in range(n)]
        raise ValueError('bad tag %d' % t)


def main():
    raw = gzip.decompress(open(PATH, 'rb').read())
    r = R(raw)
    t = r.u1()
    assert t == 10, t
    name = r.s()
    root = r.payload(10)
    print('root =', repr(name))
    print('keys =', list(root.keys()))
    # 1.7.10 WorldSavedData 外层多包一层 'data' 复合标签
    if 'data' in root and isinstance(root['data'], dict):
        root = root['data']
        print('unwrapped -> keys =', list(root.keys()))
    print('Schema =', root.get('Schema'))
    lines = root.get('Lines', [])
    print('产线总数 =', len(lines))
    print('-' * 70)
    for idx, ln in enumerate(lines):
        steps = ln.get('Steps', []) or []
        n_mach = 0
        n_in = n_out = 0
        for st in steps:
            n_mach += len(st.get('Machines', []) or [])
            n_in += len(st.get('In', []) or [])
            n_out += len(st.get('Out', []) or [])
        cumu = ln.get('Cumu', []) or []
        print('%2d) id=%-14s name=%-18s steps=%-3d machines=%-3d in=%-4d out=%-4d cumu=%-3d net=%r paused=%s'
              % (idx, ln.get('Id'), ln.get('Name'), len(steps), n_mach, n_in, n_out,
                 len(cumu), ln.get('Net'), ln.get('Paused')))
        if idx == 0 and steps:
            for j, st in enumerate(steps[:5]):
                print('      step[%d] id=%s name=%r type=%r branch=%r hier=%r'
                      % (j, st.get('Id'), st.get('Name'), st.get('Type'),
                         st.get('Branch'), st.get('Hier')))


if __name__ == '__main__':
    main()
