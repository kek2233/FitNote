const { chromium } = require('playwright');
const fs = require('fs');

const path = require('path');
const DIR = path.join(__dirname, 'screens') + '/';
const OUT = process.env.OUT || '/tmp/design_shots/';
fs.mkdirSync(OUT, { recursive: true });

const SHOTS = [
  ['d1-today',    '01-today',    '今日'],
  ['d2-training', '02-training', '训练进行中'],
  ['d3-log',      '03-log',      '记录'],
  ['d4-plan',     '04-plan',     '计划'],
  ['d5-me',       '05-me',       '我的'],
  ['d6-picker',   '06-picker',   '选动作'],
  ['d7-summary',  '07-summary',  '训练总结'],
  ['d8-onboard',  '08-onboard',  '首次引导'],
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
