/* 公共外壳：状态栏 + 底部 Tab 栏（玻璃层） */
function SB(){
  return '<div class="sb"><span class="num">9:41</span><span class="r">'
   + '<svg viewBox="0 0 20 13" fill="currentColor"><rect x="0" y="7.5" width="3" height="5.5" rx="1"/><rect x="4.6" y="5.2" width="3" height="7.8" rx="1"/><rect x="9.2" y="2.8" width="3" height="10.2" rx="1"/><rect x="13.8" y="0.4" width="3" height="12.6" rx="1"/></svg>'
   + '<svg viewBox="0 0 26 13" fill="none"><rect x="0.6" y="0.6" width="21" height="11.8" rx="3.2" stroke="currentColor" stroke-opacity=".45"/><rect x="2.2" y="2.2" width="15.6" height="8.6" rx="2" fill="currentColor"/><path d="M23.6 4.4v4.2a2.6 2.6 0 000-4.2z" fill="currentColor" fill-opacity=".45"/></svg>'
   + '</span></div>';
}

/* Tab 栏：今日 / 记录 / [凸起＋] / 计划 / 我的 */
var TABS = [
  {k:'today', i:'i-today',  t:'今日'},
  {k:'log',   i:'i-list',   t:'记录'},
  {k:'plan',  i:'i-target', t:'计划'},
  {k:'me',    i:'i-user',   t:'我的'}
];
function TABBAR(active){
  var cells = '';
  TABS.forEach(function(x, idx){
    if (idx === 2) cells += '<div class="tb" style="flex:0 0 62px"><div class="fab"><svg><use href="#i-plus"/></svg></div></div>';
    cells += '<div class="tb' + (x.k === active ? ' on' : '') + '"><svg><use href="#' + x.i + '"/></svg><span>' + x.t + '</span></div>';
  });
  return '<nav class="tabbar"><span class="glider" style="left:9px;width:66px"></span>' + cells + '</nav>';
}

/* 滚轮（引导页用） */
function WHEEL(label, value, unit){
  return '<div style="flex:1;text-align:center">'
   + '<div style="font-size:12px;color:var(--t3);margin-bottom:6px">' + label + '</div>'
   + '<div class="num" style="font-size:28px;font-weight:700;letter-spacing:-.6px">' + value
   + '<i style="font-size:13px;font-weight:600;font-style:normal;color:var(--t2);margin-left:3px">' + unit + '</i></div>'
   + '<div style="height:1px;background:var(--accent);margin:8px 22px 0;opacity:.5"></div></div>';
}
