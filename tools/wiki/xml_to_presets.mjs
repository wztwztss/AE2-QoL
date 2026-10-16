/**
 * Special:Export XML → presets.json 转换器（内容期工具，不打进 jar）。
 *
 * 用法：node tools/wiki/xml_to_presets.mjs <export.xml> [输出=src/main/resources/assets/ae2_qof/proline/presets.json]
 * 输入：https://gtnh.huijiwiki.com/wiki/Special:Export?pages=... 的导出 XML（<page><title>+<revision><text>）。
 * 行为：对每个 <page> 的 <text>（=子模板 wikitext，其内就是一段产线 JSON）做
 *       ①剥 <!-- --> ②剥 {{RecipeCalculator|...}} 模板头尾 ③从 "recipeQuery" 锚点平衡扫描取 JSON
 *       → {name, json} 序列打包。name 优先取 XML <title> 去前缀（"稀土处理/独居石主线"，无层级时直接子名）。
 */
import fs from 'node:fs';

const [, , xmlPath, outPathArg] = process.argv;
const out = outPathArg || 'src/main/resources/assets/ae2_qof/proline/presets.json';
if (!xmlPath) {
  console.error('usage: node tools/wiki/xml_to_presets.mjs <export.xml>');
  process.exit(1);
}
const xml = fs.readFileSync(xmlPath, 'utf8');

function pageTitle(block) {
  const m = block.match(/<title>([\s\S]*?)<\/title>/);
  return m ? m[1].trim() : '未命名产线';
}
function pageText(block) {
  const m = block.match(/<text[\s\S]*?>([\s\S]*?)<\/text>/);
  return m ? m[1] : '';
}
function unescapeWiki(t) {
  return t
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}
/** 从文本内提取一段平衡的产品 JSON（锚点=首对 '"xxx"' 紧随的 { ）。 */
function extractJson(text) {
  text = text.replace(/<!--[\s\S]*?-->/g, '');
  let a = text.indexOf('"recipeQuery"');
  let b = text.indexOf('"groups"');
  let anchor = Math.min(a === -1 ? Infinity : a, b === -1 ? Infinity : b);
  if (anchor === Infinity) return null;
  const braceStart = text.lastIndexOf('{', anchor);
  if (braceStart < 0) return null;
  let depth = 0;
  let end = -1;
  let inStr = false;
  let esc = false;
  for (let i = braceStart; i < text.length; i++) {
    const c = text[i];
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
  if (end < 0) return null;
  return text.slice(braceStart, end);
}

const presets = [];
const blocks = xml.split('<page>');
for (const block of blocks.slice(1)) {
  const title = pageTitle(block);
  const text = unescapeWiki(pageText(block));
  const json = extractJson(text);
  if (!json) {
    console.warn('SKIP（无 JSON）：', title);
    continue;
  }
  let name = title.replace(/^模板:RecipeCalculator\/?/, '').trim();
  try {
    const parsed = JSON.parse(json);
    if (parsed.name) name = parsed.name;
  } catch (e) {
    console.warn('WARN JSON.parse 失败仍保留原文：', title, e.message);
  }
  presets.push({ name, json });
}

fs.mkdirSync(out.substring(0, out.lastIndexOf('/')), { recursive: true });
fs.writeFileSync(out, JSON.stringify({ presets }, null, 1), 'utf8');
console.log('presets=', presets.length, '→', out);
