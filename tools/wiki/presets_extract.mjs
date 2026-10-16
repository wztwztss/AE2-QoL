/**
 * huijiwiki 模板:RecipeCalculator 预置提取器（构建/内容期工具，不打进 jar）。
 *
 * 用法：node tools/wiki/presets_extract.mjs build/tmp/recipecalc_full.txt
 * 输入：页面的 wikitext 原文（浏览器"查看源码"或"编辑→源码"全量复制保存）。
 * 输出：src/main/resources/assets/ae2_qof/proline/presets.json（{"presets":[{name,json}]}）。
 *
 * 提取规则：逐段扫描 `{{RecipeCalculator`；段内 JSON 从"引号紧跟的 {"（平衡括号扫描，跳过字符串）
 * 起到深度归零止。名称取该段前最近的 `==标题==` 行；没有标题就用首条 recipeQuery.name。
 */
import fs from 'node:fs';

const src = fs.readFileSync(process.argv[2] || 'build/tmp/recipecalc_full.txt', 'utf8');
const out = process.argv[3] || 'src/main/resources/assets/ae2_qof/proline/presets.json';

function lineText(src, i) {
  const lineStart = src.lastIndexOf('\n', i) + 1;
  return src.slice(lineStart, i);
}
function titleBefore(src, i) {
  const lines = src.slice(0, i).split(/\r?\n/);
  for (let k = lines.length - 1; k >= 0; k--) {
    const m = lines[k].match(/^\s*=+\s*([^=]+?)\s*=+\s*$/);
    if (m) return m[1].trim();
  }
  return null;
}

const outPresets = [];
let idx = 0;
while (true) {
  const m = src.indexOf('{{RecipeCalculator', idx);
  if (m < 0) break;
  idx = m + 18;
  // JSON 起点：本段内第一个 {' 后紧跟空白/引号的 {
  let start = -1;
  for (let i = m; i < src.length - 1; i++) {
    if (src[i] === '{' && /[\s"]/.test(src[i + 1] || '') && src[i + 1] === '"') {
      start = i;
      break;
    }
    if (src[i] === '{' && src[i + 1] === '\n') {
      start = i;
      break;
    }
  }
  if (start < 0) continue;
  // 平衡扫描（跳过字符串）
  let depth = 0;
  let end = -1;
  let inStr = false;
  let esc = false;
  for (let i = start; i < src.length; i++) {
    const c = src[i];
    if (esc) {
      esc = false;
      continue;
    }
    if (inStr) {
      if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === '{') depth++;
    else if (c === '}') {
      depth--;
      if (depth === 0) {
        end = i + 1;
        break;
      }
    }
  }
  if (end < 0) continue;
  const json = src.slice(start, end);
  const name = titleBefore(src, m) || (json.match(/"name"\s*:\s*"([^"]+)"/) || [])[1] || '未命名产线';
  outPresets.push({ name, json });
}

fs.mkdirSync(out.substring(0, out.lastIndexOf('/')), { recursive: true });
fs.writeFileSync(out, JSON.stringify({ presets: outPresets }, null, 1), 'utf8');
console.log('presets=', outPresets.length, 'file=', out);
