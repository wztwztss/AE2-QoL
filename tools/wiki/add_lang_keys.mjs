/** 补全 4.1.0 产线页新增 lang 键（中英，幂等）。 */
import fs from 'node:fs';

const zh = {
  'ae2_qof.proline.no_selection': '未选中产线',
  'ae2_qof.proline.hier.main': '主线',
  'ae2_qof.proline.branch.all': '全部',
  'ae2_qof.proline.step.branch': '分支: ',
  'ae2_qof.proline.step.bind_tip': '点击选择绑定机器',
  'ae2_qof.proline.del_confirm': '再点一次【删除整线】才会真删（3 秒内有效）',
  'ae2_qof.proline.candidates.click': '点击绑定/解绑到当前步骤',
  'ae2_qof.proline.import.empty': '先粘贴 JSON',
  'ae2_qof.proline.import.toolong': '超长（%s 字符 > 28000）：请拆分产线后再导入',
  'ae2_qof.proline.import.nostep': '没有解析出任何步骤',
  'ae2_qof.proline.import.fail': '建线失败（团队门/上限，见聊天与日志）',
  'ae2_qof.proline.import.ok': '已创建【%s】并导入 %s 步',
  'ae2_qof.proline.scope.all': '全部',
};

const en = {
  'ae2_qof.proline.no_selection': 'no line selected',
  'ae2_qof.proline.hier.main': 'Main',
  'ae2_qof.proline.branch.all': 'All',
  'ae2_qof.proline.step.branch': 'Branch: ',
  'ae2_qof.proline.step.bind_tip': 'Click to pick machines to bind',
  'ae2_qof.proline.del_confirm': 'Click Delete line again within 3s to confirm',
  'ae2_qof.proline.candidates.click': 'Click to bind/unbind to current step',
  'ae2_qof.proline.import.empty': 'Paste JSON first',
  'ae2_qof.proline.import.toolong': 'Too long (%s chars > 28000): split the line',
  'ae2_qof.proline.import.nostep': 'No step parsed',
  'ae2_qof.proline.import.fail': 'Line creation failed (team gate/limit, see chat & log)',
  'ae2_qof.proline.import.ok': 'Created [%s] with %s steps',
  'ae2_qof.proline.scope.all': 'All',
};

function patch(file, add) {
  let s = fs.readFileSync(file, 'utf8');
  if (!s.endsWith('\n')) s += '\n';
  const existing = new Set();
  for (const line of s.split(/\r?\n/)) {
    const i = line.indexOf('=');
    if (i > 0) existing.add(line.slice(0, i).trim());
  }
  let n = 0;
  for (const [k, v] of Object.entries(add)) {
    if (existing.has(k)) continue;
    s += k + '=' + v + '\n';
    n++;
  }
  fs.writeFileSync(file, s);
  console.log(file.split('/').pop(), '+' + n);
}

patch('src/main/resources/assets/ae2_qof/lang/zh_CN.lang', zh);
patch('src/main/resources/assets/ae2_qof/lang/en_US.lang', en);
