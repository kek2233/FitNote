# 修复当日热量主读数的等宽数字

Written against: `fa64f4a02f4d`

## Evidence chain

- Surface: `体脂率测算.html` → Tab「热量 cal」→ 当日汇总区
- Problem: 该区主读数（「已摄入 0 kcal」）与食物建议行（「水产 · 建议 120g」）未启用等宽数字，数值变化时字符宽度不一致，数字与右侧单位左右抖动。
- Design evidence: `docs/FitNote-系统设计规范.md` §4.2 ——「**所有数字**（容量 / 重量 / 计时 / 热量）使用 `font-variant-numeric: tabular-nums`，保证等宽对齐。」；`README.md` 界面章节「`tabular-nums` 等宽数字」。
- Owner: 内联样式表的 `.hero-num` / `.hero-ring-in` / `.st-n` / `.rec-n` / `.macro-n`（既有正确实现，作为 exemplar）
- Scope and affected surfaces: 仅 `.big` 与 `.fd-u` 两条规则；消费页面为「热量 cal」与食物详情弹层。
- Uncertainty: none

## Design decision

为 `.big` 与 `.fd-u` 补 `font-variant-numeric: tabular-nums`，使其与既有 `.hero-num` 等一致。不改字号、字重、颜色，不引入新 token——既有系统已能表达该决定，只是两处遗漏。

## Reuse

- 属性：`font-variant-numeric: tabular-nums`
- Exemplar: `体脂率测算.html` 中的 `.hero-num`（`font-variant-numeric:tabular-nums`）

## Changes

1. `体脂率测算.html`（内联样式表）
   - Change: 在 `.big` 与 `.fd-u` 规则中追加 `font-variant-numeric:tabular-nums`
   - Preserve: 既有的字号、字重、颜色、行高
   - Verify: 「热量」页主读数实算 `fontVariantNumeric` 含 `tabular-nums`；0 → 1240 变化时文本左边缘不位移

## Scope

- Inherit: 所有使用 `.big` / `.fd-u` 的页面（热量页、食物详情弹层）
- Verify: 热量页当日汇总、食物详情建议行
- Exclude: 已正确实现的 `.hero-num` 等类；SVG 图表内文字

## Validation

- Product: 打开「热量」页，记录一条饮食，观察「已摄入」数字增长时是否跳动
- Interface: 深色 / 浅色两种外观；数字长度极值（0、99、1240、12400）
- System: 全页面数字读数统一为 tabular-nums，不存在第二种写法
- Repository: `grep -c "font-variant-numeric:tabular-nums" 体脂率测算.html` → 不少于 7（原有 5 处 + 新增 2 处）

## Stop conditions

- 若 `font-variant-numeric` 在该 WebView 版本不被支持导致数字异常，回退为 `font-feature-settings: "tnum"` 并停止。

## Design documentation

- 无需新文档：规范 §4.2 已规定，本次只是补齐实现。验收后在审查记录中标记 #1 已解决。
