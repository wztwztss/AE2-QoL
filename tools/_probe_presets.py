"""离线复现 ProLineImporter.parse 的步骤抽取逻辑，统计每个预置能产出多少步骤。
只读，不改文件。对应 Java: src/main/java/com/wztwzt/ae2_qof/proline/ProLineImporter.java
"""
import json
import re

P = (r'E:/wzt/MC/modcreater/AE2-QoL-1.7.10-GTNH/src/main/resources'
     r'/assets/ae2_qof/proline/presets.json')


def sanitize(s):
    """对应 Java sanitize()：剥注释 + 从 recipeQuery/groups 锚点回退到根 '{' + 尾部剥 '}'"""
    s = re.sub(r'(?s)<!--.*?-->', '', s)
    s = re.sub(r'(?s)/\*.*?\*/', '', s)
    a = s.find('"recipeQuery"')
    b = s.find('"groups"')
    cands = [x for x in (a, b) if x != -1]
    if cands:
        anchor = min(cands)
        s = s[s.rfind('{', 0, anchor):]
    s = s.strip()
    for _ in range(3):
        try:
            json.loads(s)
            return s
        except Exception:
            e = s.rfind('}')
            if e > 0:
                s = s[:e]
            else:
                return s
    return s


def main():
    root = json.load(open(P, encoding='utf-8'))
    presets = root.get('presets', [])
    print('预置条数 =', len(presets))
    print('-' * 78)
    for i, p in enumerate(presets):
        name = p.get('name', '?')
        raw = p.get('json', '')
        try:
            obj = json.loads(sanitize(raw))
        except Exception as ex:
            print('%d) %-16s  JSON 解析失败: %s' % (i, name, ex))
            continue
        rq = obj.get('recipeQuery')
        n_rq = len(rq) if isinstance(rq, list) else 0
        steps = obj.get('steps')
        n_steps = len(steps) if isinstance(steps, list) else 0
        start = obj.get('start')
        n_start = len(start) if isinstance(start, (list, dict)) else 0
        tot_in = tot_out = 0
        for st in (rq if isinstance(rq, list) else []):
            if not isinstance(st, dict):
                continue
            for f, acc in (('input', 'in'), ('output', 'out')):
                v = st.get(f)
                if isinstance(v, list):
                    n = len(v)
                elif isinstance(v, str):
                    n = len([t for t in v.split(',') if t.strip()])
                else:
                    n = 0
                if acc == 'in':
                    tot_in += n
                else:
                    tot_out += n
        print('%d) %-16s top_keys=%-38s recipeQuery=%-3d steps=%-3d start=%-3d in=%-4d out=%-4d'
              % (i, name, ','.join(list(obj.keys())[:6]), n_rq, n_steps, n_start, tot_in, tot_out))
        # 抽样看第一条 step 的原始形态
        if isinstance(rq, list) and rq and i == 0:
            print('      sample recipeQuery[0] =',
                  json.dumps(rq[0], ensure_ascii=False)[:300])


if __name__ == '__main__':
    main()
