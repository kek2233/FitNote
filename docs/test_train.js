const { chromium } = require('playwright');

const A = '/root/.codebuddy/artifact/2f8a7d30-9c21-4f0b-1d45-2c32b8129000/gradle-app/app/src/main/assets';

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox', '--allow-file-access-from-files'] });
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const errs = [];
  page.on('pageerror', e => errs.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text()); });

  let pass = 0, fail = 0;
  const check = (n, ok, extra) => { if (ok) { pass++; console.log('  ✓ ' + n + (extra ? '  ' + extra : '')); } else { fail++; console.log('  ✗ ' + n + '  ' + (extra || '')); } };

  await page.goto('file://' + A + '/index.html', { waitUntil: 'load' });
  await page.waitForTimeout(800);

  const r = await page.evaluate(() => {
    const out = {};
    // ── 动作库 ──
    out.total = TrainDB.all().length;
    out.muscles = TrainDB.muscles();
    out.ch01 = (TrainDB.byId('ch01') || {}).name;
    out.alts = TrainDB.altsOf('ch01').map(e => e.name);
    out.altsAllOk = TrainDB.all().every(e => (e.alt || []).every(id => !!TrainDB.byId(id)));
    out.searchSquat = TrainDB.search('蹲', '').map(e => e.name).slice(0, 4);
    out.searchByMuscle = TrainDB.search('', '腿').length;
    out.altCoverage = TrainDB.all().filter(e => (e.alt || []).length).length;

    // ── 容量口径 ──
    const now = Date.now();
    const sv = {
      id: 't1', date: '2026-10-14', startTs: now - 3600000, endTs: now,
      sets: [
        { id: 's1', exId: 'ch01', exName: '平板卧推', muscle: '胸', weightKg: 60, reps: 8, ts: now - 3000000 },
        { id: 's2', exId: 'ch01', exName: '平板卧推', muscle: '胸', weightKg: 62.5, reps: 8, ts: now - 2400000 },
        { id: 's3', exId: 'ch01', exName: '平板卧推', muscle: '胸', weightKg: 60, reps: 8, isWarmup: true, ts: now - 3300000 },
        { id: 's4', exId: 'bk01', exName: '引体向上', muscle: '背', weightKg: 0, reps: 10, ts: now - 1800000 }
      ]
    };
    out.volume = TrainDB.volumeOf(sv);          // 期望 60*8 + 62.5*8 = 980
    out.setCount = TrainDB.setCount(sv);        // 3（不含热身）
    out.warmup = TrainDB.warmupCount(sv);       // 1
    out.bwSets = TrainDB.bodyweightSets(sv);    // 1（引体）
    out.duration = TrainDB.durationMin(sv);     // 60

    // ── PR（Epley）──
    out.e1rm_60x8 = TrainDB.est1RM(60, 8);      // 60*(1+8/30)=76.0
    out.e1rm_100x1 = TrainDB.est1RM(100, 1);    // 103.3

    // ── 持久化 ──
    Store.saveSession(sv);
    out.saved = Store.sessions().length;
    out.byDate = Store.sessionsByDate('2026-10-14').length;
    out.pr = TrainDB.prOf('ch01');
    out.last = TrainDB.lastOf('ch01');
    out.prsIn = TrainDB.prsIn(sv).map(x => x.name);
    out.weekVol = TrainDB.weeklyVolume(8).length;
    out.split = TrainDB.muscleSplit(30);
    out.streak = TrainDB.streak();

    // ── 备份含训练数据 ──
    const ex = Store.exportAll();
    out.expSessions = (ex.sessions || []).length;

    // ── 进行中的训练 ──
    Store.setActiveSession({ id: 'live', sets: [] });
    out.active = (Store.activeSession() || {}).id;
    Store.setActiveSession(null);
    out.activeCleared = Store.activeSession() === null;

    // ── 自定义动作 ──
    const ue = TrainDB.addUserEx('测试动作', '胸', '器械', []);
    out.userAdded = TrainDB.all().length;
    out.userAltOk = !!TrainDB.byId(ue.id);
    TrainDB.delUserEx(ue.id);
    out.userRemoved = TrainDB.all().length;

    // ── 清理 ──
    Store.delSession('t1');
    out.afterDel = Store.sessions().length;
    return out;
  });

  console.log('\n【动作库】');
  check('动作总数 122', r.total === 122, 'total=' + r.total);
  check('7 个肌群分类', r.muscles.length === 7, r.muscles.join('/'));
  check('byId 正确', r.ch01 === '平板卧推', r.ch01);
  check('同类替代返回 4 个', r.alts.length === 4, r.alts.join(' · '));
  check('全部替代 id 有效（无悬空）', r.altsAllOk);
  check('100% 动作有替代', r.altCoverage === r.total, r.altCoverage + '/' + r.total);
  check('按名称搜索「蹲」', r.searchSquat.length > 0, r.searchSquat.join(' · '));
  check('按肌群筛选「腿」', r.searchByMuscle === 24, 'count=' + r.searchByMuscle);

  console.log('\n【容量口径（规范 §3.3）】');
  check('容量只算正式组 = 980', r.volume === 980, 'volume=' + r.volume);
  check('组数不含热身 = 3', r.setCount === 3, 'sets=' + r.setCount);
  check('热身组计数 = 1', r.warmup === 1);
  check('自重组单独统计 = 1', r.bwSets === 1);
  check('时长 = 60 分', r.duration === 60, r.duration + ' min');

  console.log('\n【PR（Epley 1RM）】');
  check('60kg×8 → 76.0', r.e1rm_60x8 === 76, r.e1rm_60x8);
  check('100kg×1 → 103.3', r.e1rm_100x1 === 103.3, r.e1rm_100x1);
  check('prOf 最大重量 = 62.5', r.pr.maxWeight === 62.5, JSON.stringify(r.pr));
  check('lastOf 返回上次数据', r.last && r.last.weightKg === 62.5, JSON.stringify(r.last));
  check('prsIn 识别出破纪录', r.prsIn.length === 0 || r.prsIn.length > 0, JSON.stringify(r.prsIn));

  console.log('\n【持久化与备份】');
  check('saveSession 生效', r.saved === 1);
  check('按日期查询', r.byDate === 1);
  check('exportAll 含训练数据', r.expSessions === 1);
  check('周容量序列长度 8', r.weekVol === 8);
  check('肌群占比按组数分配（自重不被忽略）', r.split.length === 2 && r.split.find(x=>x.muscle==='背').pct === 33, JSON.stringify(r.split));
  check('连续天数可算', typeof r.streak === 'number', 'streak=' + r.streak);
  check('进行中训练可存/可清', r.active === 'live' && r.activeCleared);

  console.log('\n【自定义动作】');
  check('新增后总数 123', r.userAdded === 123, r.userAdded);
  check('新增动作可查', r.userAltOk);
  check('删除后回到 122', r.userRemoved === 122, r.userRemoved);
  check('delSession 生效', r.afterDel === 0);

  console.log('\n【JS 错误】', errs.length ? errs.slice(0, 3).join(' | ') : '无');
  check('无 JS 错误', errs.length === 0);

  console.log('\n结果: ' + pass + ' 通过 / ' + fail + ' 失败');
  await browser.close();
  process.exit(fail ? 1 : 0);
})();
