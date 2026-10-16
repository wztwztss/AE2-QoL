/** 工具脚本：对两个 .lang 文件做 proline 域去重（en 删中文行，zh 删英文行）+ 对齐复检。 */
import fs from 'node:fs';

const zhF = 'src/main/resources/assets/ae2_qof/lang/zh_CN.lang';
const enF = 'src/main/resources/assets/ae2_qof/lang/en_US.lang';

const cjk = /[一-鿿]/;
const latinOnly = /^[A-Za-z0-9 :;,.%%\-\[\]()/·→—#=%&+'!?\\u0000-\u007F]*$/;

function stripLangSide(f, shouldHaveCJK) {
  const lines = fs.readFileSync(f, 'utf8')
    .split(/\r?\n/)
    .filter((l, i) => {
      if (!l.startsWith('ae2_qof.proline.')) return true;
      const hasCjk = cjk.test(l);
      if (shouldHaveCJK) return hasCjk || !/[A-Za-z]{4,}/.test(l.slice(l.indexOf('=') + 1));
      return !hasCjk;
    });
  fs.writeFileSync(f, lines.join('\n') + '\n');
  console.log('stripped', f);
}

stripLangSide(enF, false);
// zh: 允许中文 + 半角符号；剔除纯英文句值的行
stripLangSide(zhF, true);

function keys(f) {
  const m = new Map();
  const dup = [];
  for (const l of fs.readFileSync(f, 'utf8').split(/\r?\n/)) {
    if (!l || l.startsWith('#')) continue;
    const i = l.indexOf('=');
    if (i < 1) continue;
    const k = l.slice(0, i).trim();
    if (m.has(k)) dup.push(k);
    m.set(k, 1);
  }
  return { m, dup };
}
const zh = keys(zhF);
const en = keys(enF);
console.log('zh keys', zh.m.size, 'dup', zh.dup.length);
console.log('en keys', en.m.size, 'dup', en.dup.length);
const z = [...zh.m.keys()];
const e = [...en.m.keys()];
console.log('only_zh', z.filter(k => !en.m.has(k)));
console.log('only_en', e.filter(k => !zh.m.has(k)));
