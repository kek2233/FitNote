# v2 视觉系统升级：色彩 / 排版 / 材质 + 液态玻璃

Written against: `fa64f4a02f4d`

> 本方案**不是** improve-ui 的审查缺陷（审查见 `audit-2026-10-14.md`，其中仅 1 条 finding，单列于 `plan-1-tabular-nums.md`）。
> 本方案是用户主动指定的增强：按 `FitNote-系统设计规范-v2.md` + 移植后的 Kyant0 液态玻璃重刷界面，并补齐设计稿。

## Evidence chain

- Surface: 全部 Tab（今日 / 热量 / 记录 / 计划 / 食物库）+ 吸顶栏 + 底部导航 + 弹层
- Problem: 现有中性色为「冷灰」（实测 `#F2F2F7` → `oklch(96.3% 0.007 286)`，色相 286° 蓝紫方向），与品牌绿（154°）方向相反，导致整体呈「标准灰 + 一个绿贴片」，缺乏整体性格；正文 15px 低于可读下限；吸顶栏用硬 1px 分隔线。
- Design evidence:
  - `docs/FitNote-系统设计规范-v2.md` §2 色彩（OKLCH + 品牌偏移中性色）、§3 排版（16px / rem / 24px 韵律）
  - `impeccable` skill：禁止纯黑纯白、禁止中性色死灰、alpha 为坏味道、正文 ≥16px
  - `apple-design` skill §12「Scroll edge effects, not hard dividers」「Materialize, don't just fade」、§15「tracking 随字号变化」「spacing 用 rem/em」
  - `baseline-ui` skill：数据必须 `tabular-nums`
- Owner: 内联样式表的 `:root` / `[data-theme]`（色彩与排版 token）与 `.hd`（吸顶栏材质）
- Scope and affected surfaces: 所有页面与组件；不含功能逻辑、数据口径、构建流程
- Uncertainty: OKLCH 在旧 WebView 的支持度——已用 hex 回退双写消除

## Design decision

用「中性色整体旋转到品牌色方向」替代当前的冷灰中性色，并把排版提升到 16px/rem/24px 韵律；同时把吸顶栏的硬分隔线换成滚动边缘渐隐、弹层入场改为「模糊材质化」。液体玻璃沿用已移植的 Kyant0 算法（circleMap 折射 + 解析法线 + 深度项），本次不再改动算法本身。

之所以这样解决根因：当前观感的根本问题是**中性色与品牌色方向相反**，这会让界面读作「通用组件套了个绿按钮」；把中性色转到同一色相方向后，灰阶与强调色成为同一家族，观感会从「贴片」变为「一台有性格的仪器」。

## Reuse

- 既有 token 名继续沿用：`--ground` / `--surface` / `--t1` / `--t2` / `--t3` / `--accent` / `--sep` / `--hairline`
- 既有组件：`.card` / `.hd` / `.tabs` / `.sheet` / `.btn`
- Exemplar: `docs/design/src/fitnote.css`（设计稿侧 token，同步同一套值）
- **不新增 primitive**：所有改动都在既有 token 与既有组件上完成

## Changes

1. `体脂率测算.html` — `:root` / `[data-theme="light"]` 色彩 token
   - Change: 中性色改为 OKLCH 品牌偏移（色相 150°），主值写 `oklch(...)` 并保留 hex 回退；去掉纯黑 `#000` 与纯白 `#FFF`
   - Preserve: token 名称与语义不变，避免影响既有组件引用
   - Verify: 实算对比度——正文 ≥4.5:1、大字号 ≥3:1；浅色模式无纯白、深色模式无纯黑

2. `体脂率测算.html` — 排版
   - Change: 正文 15px → `1rem`(16px)；纵向间距改为 24px 韵律的分数/倍数；大标题保持负字距、小字加正字距（tracking 随字号变化）
   - Preserve: 既有 5 档层级关系与字重
   - Verify: 系统字号放大 1.5 倍时布局不错乱（因改用 rem）

3. `体脂率测算.html` — `.hd` 吸顶栏材质
   - Change: 移除 `border-bottom: 1px`，改为滚动时出现的渐变/模糊边缘（scroll edge effect）
   - Preserve: 吸顶行为与玻璃材质不变
   - Verify: 顶部无硬线；内容滚到栏下时过渡自然

4. `体脂率测算.html` — `.sheet` 弹层入场
   - Change: 入场时 blur 半径与 scale 同步动画（materialize），而非纯位移淡入
   - Preserve: 时长与 iOS 曲线不变
   - Verify: 弹层出现时读作「材质抵达」

5. `体脂率测算.html` — 全局
   - Change: 增加 `@media (prefers-reduced-motion: reduce)` 降级（apple-design §14 明确要求，v2 规范 §? 已列）
   - Preserve: 默认动效不变
   - Verify: 开启系统「减弱动效」后无弹簧/位移，仅交叉淡入

6. `docs/design/src/fitnote.css` + 设计稿画板
   - Change: 同步同一套 v2 token 并重导全部画板
   - Preserve: 画板结构与内容
   - Verify: 设计稿与 App 实测色值一致

## Scope

- Inherit: 全部页面、组件、两种外观（深/浅）
- Verify: 五个 Tab、弹层、Toast、底部导航、图表配色
- Exclude: 功能逻辑、训练模块数据层、食物数据、构建流程、APK 打包（本次不产出 APK）

## Validation

- Product: 五个 Tab 在深/浅两种外观下逐一目视；数字读数统一等宽且无抖动
- Interface: 系统字号 1.5×、390/430px 两种宽度、开启减弱动效
- System: 全站只有一个强调色；中性色与强调色同色相方向；无纯黑白
- Repository:
  - `node regression.js` → 41/41
  - `node test_v140.js` → 38/38
  - `node test_train.js` → 30/30
  - `node test_foodlib.js` → 17/17

## Stop conditions

- 若任一既有测试套件失败且无法在本次 scope 内解释，停止并回退，不继续改动其它文件。
- 若 OKLCH 在目标 WebView 上渲染异常（hex 回退失效），停止并保留 v1 色值。

## Design documentation

- 验收后：把 v2 规范从「提案」升为「已接受」，更新 `README.md` 版本说明与 `docs/design/index.html` 色板页的对比度数值。
