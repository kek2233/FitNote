const { chromium } = require('playwright');
const fs = require('fs');

const DIR = '/root/.codebuddy/artifact/2f8a7d30-9c21-4f0b-1d45-2c32b8129000/design/screens/';
const OUT = '/tmp/design_shots/';
fs.mkdirSync(OUT, { recursive: true });

const SHOTS = [
  ['d1-today',      '01-today',           '今日'],
  ['d2-training',   '02-training',        '训练进行中'],
  ['d3-log',        '03-log',             '记录'],
  ['d4-plan',       '04-plan',            '计划'],
  ['d5-me',         '05-me',              '我的'],
  ['d6-picker',     '06-picker',          '选动作'],
  ['d7-summary',    '07-summary',         '训练总结'],
  ['d11-cal',       '08-cal',             '热量记账'],
  ['d12-foodlib',   '09-foodlib',         '食物库'],
  ['d9-welcome',    '10-onboard-welcome', '引导 1 · 欢迎'],
  ['d8-onboard',    '11-onboard-body',    '引导 2 · 身体数据'],
  ['d10-plan-pick', '12-onboard-plan',    '引导 3 · 选计划'],
  ['d13-calendar',  '13-calendar',        '训练日历'],
  ['d14-pr',        '14-pr',              'PR 详情'],
  ['d15-empty',     '15-empty',           '空状态'],
  ['d16-exdetail',  '16-exdetail',        '动作详情 · 同类替代'],
];

(async () => {
  const browser = await chromium.launch({ args: ['--no-sandbox', '--font-render-hinting=none'] });
  const errs = [];
  for (const [file, name, label] of SHOTS) {
    for (const theme of ['dark', 'light']) {
      const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3 });
      page.on('pageerror', e => errs.push(name + ': ' + e.message));
      page.on('console', m => { if (m.type() === 'error') errs.push(name + ' console: ' + m.text()); });
      await page.goto('file://' + DIR + file + '.html', { waitUntil: 'load' });
      if (theme === 'light') await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'light'));
      await page.waitForTimeout(260);
      const suffix = theme === 'dark' ? '' : '-light';
      await page.screenshot({ path: OUT + name + suffix + '.png' });
      await page.close();
    }
    console.log('✓', label, '(' + name + ')');
  }
  await browser.close();
  console.log('JS 错误:', errs.length ? JSON.stringify(errs.slice(0, 6)) : '无');
})();
