/** 轮询 GitHub Actions 'Release tagged build' workflow 状态。用法：node tools/wiki/watch_release.mjs <count> */
import https from 'node:https';

const proxyHost = '127.0.0.1';
const proxyPort = 33210;
const rounds = Number(process.argv[2] || 6);

function pollOnce() {
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        host: proxyHost,
        port: proxyPort,
        method: 'GET',
        path: 'https://api.github.com/repos/wztwztss/AE2-QoL/actions/runs?event=push&per_page=3',
        headers: { 'User-Agent': 'wzt-release-watch', Accept: 'application/vnd.github+json' },
        timeout: 20000,
      },
      res => {
        let d = '';
        res.on('data', c => (d += c));
        res.on('end', () => {
          try {
            const j = JSON.parse(d);
            if (!j.workflow_runs) {
              resolve('no-runs field (HTTP ' + res.statusCode + ')');
              return;
            }
            resolve(
              j.workflow_runs
                .slice(0, 3)
                .map(r => `${r.head_branch} ${r.status} ${r.conclusion || '-'} ${r.created_at}`)
                .join(' | ')
            );
          } catch (e) {
            resolve('parse err ' + e.message);
          }
        });
      }
    );
    req.on('error', e => reject(e));
    req.on('timeout', () => {
      req.destroy(new Error('timeout'));
    });
    req.end();
  });
}

for (let i = 1; i <= rounds; i++) {
  try {
    console.log('poll' + i + ':', await pollOnce());
  } catch (e) {
    console.log('poll' + i + ': ERR ' + e.message);
  }
  if (i < rounds) await new Promise(r => setTimeout(r, 30000));
}
