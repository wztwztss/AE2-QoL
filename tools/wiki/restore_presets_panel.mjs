/** 从 HEAD 恢复被误删的 buildPresetsPanel / buildPresetList 两个方法体到当前 ProLinePage。 */
import fs from 'node:fs';

const oldSrc = fs.readFileSync('build/tmp/head_page.java', 'utf8');
const target = 'src/main/java/com/wztwzt/ae2_qof/proline/gui/ProLinePage.java';

function grab(sig) {
  const a = oldSrc.indexOf(sig);
  if (a < 0) return null;
  const start = oldSrc.lastIndexOf('\n    ', a) + 1;
  let depth = 0;
  let end = -1;
  let inStr = false;
  let esc = false;
  for (let i = oldSrc.indexOf('{', a); i < oldSrc.length; i++) {
    const c = oldSrc[i];
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
  return end < 0 ? null : oldSrc.slice(start, end);
}

let p1 = grab('private static ModularPanel buildPresetsPanel(PanelSyncManager syncManager, IPanelHandler panelHandler,\n        StockMonitorTerminal terminal) {');
let p2 = grab('private static IWidget buildPresetList(List<String> rows, PanelSyncManager syncManager,\n        StockMonitorTerminal terminal) {');
if (!p2) {
  p2 = grab('private static IWidget buildPresetList(List<String> rows, PanelSyncManager syncManager) {');
}
if (!p1 || !p2) {
  console.error('grab fail', 'p1=', !!p1, 'p2=', !!p2);
  process.exit(1);
}

let cur = fs.readFileSync(target, 'utf8');
const anchor = '    // ===== 子面板：wiki JSON 导入（A5/O8 v1：单段 ≤28KB；名称解析失败=标红不静默）=====';
if (!cur.includes(anchor)) {
  console.error('anchor miss');
  process.exit(1);
}
cur = cur.replace(anchor, '    // ===== 子面板：预置产线选择（restore）=====\n\n' + p2 + '\n\n' + p1 + '\n\n' + anchor);
// 若 p2 签名无 terminal 参数，buildPresetsPanel 里对 buildPresetList(rows, syncManager) 的调用需要同步
if (/buildPresetList\(rows\.getValue\(\), syncManager\)/.test(p2)) {
  p1 = p1.replace(/\(rows\.getValue\(\), syncManager, terminal\)/g, '(rows.getValue(), syncManager)');
}
fs.writeFileSync(target, cur);
console.log('restored p1=', p1.length, 'p2=', p2.length);
