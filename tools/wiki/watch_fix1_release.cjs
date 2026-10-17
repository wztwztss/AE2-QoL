/** 轮询 Release 4.1.0-fix1 出现与否 + Actions 状态。 */
const { execSync } = require('node:child_process');

for (let i = 1; i <= 8; i++) {
  execSync('sleep 25');
  try {
    const rel = execSync(
      'curl -s --max-time 20 "https://api.github.com/repos/wztwztss/AE2-QoL/releases/tags/4.1.0-fix1" -H "User-Agent: wzt"',
      { encoding: 'utf8' }
    );
    const j = JSON.parse(rel);
    if (j.tag_name) {
      console.log('RELEASE UP:', j.tag_name, '| body head:', (j.body || '').slice(0, 60));
      for (const a of j.assets) console.log(' -', a.name, a.size, 'B');
      break;
    } else {
      const runs = execSync(
        'curl -s --max-time 20 "https://api.github.com/repos/wztwztss/AE2-QoL/actions/runs?event=push&per_page=2" -H "User-Agent: wzt"',
        { encoding: 'utf8' }
      );
      const rr = JSON.parse(runs);
      console.log(
        'poll' + i + ':',
        (rr.workflow_runs || []).slice(0, 2).map(r => r.head_branch + ' ' + r.status + ' ' + (r.conclusion || '-')).join(' | ')
      );
    }
  } catch (e) {
    console.log('poll' + i + ': ERR', e.message);
  }
}
