# 已安装的 UI 技能集（ui-skills）

> 用途：本项目界面设计与审查的方法论来源。所有技能均为 **纯 Markdown 文档**（无可执行代码），安装前已逐个做安全扫描。

## 来源

| 项 | 内容 |
|---|---|
| 聚合包 | [`ui-skills`](https://www.npmjs.com/package/ui-skills) v0.2.4（MIT） |
| 上游仓库 | [ibelick/ui-skills](https://github.com/ibelick/ui-skills) |
| 注册表条目 | **181** 个（收录自社区各作者仓库） |
| 下载成功 | **143** 个 |
| 失败（源端 404/不可达） | 38 个（源仓库文件已移动或删除，非本项目问题） |
| 本地安装 | **163** 个目录（含本项目此前已装的 `impeccable` 等） |

## 安全扫描

安装前对每个文件扫描以下模式，**命中数 0**，全部为纯文档：

```
curl|bash · wget|sh · eval() · rm -rf / · $(curl ...) · base64 -d 管道执行
npx -y &&  · 写 ~/.ssh · 读取环境变量令牌 · 写 .gitconfig
```

未执行该包自带的 Astro 开发服务器（`npx ui-skills start`）——沙箱内无意义，且本项目只需要技能内容本身。

## 与本项目相关的分组

| 分组 | 数量 | 代表 |
|---|---|---|
| 视觉 / 排版 / 色彩 | 19 | `apple-design`、`better-typography`、`taste-skill`、`frontend-design`、`minimalist-skill` |
| UI 审查 / 去 AI 味 | 14 | **`improve-ui`**、`impeccable`、`baseline-ui`、`audit`、`critique`、`polish`、`distill` |
| 动效 / 交互 | 22 | `micro-interaction`、`animation-systems`、`60fps-animation`、`gsap`、`12-principles-of-animation` |
| 液态玻璃 / 材质 | 3 | `progressive-blur`、`threejs-materials`、`threejs-shaders` |
| 无障碍 | 3 | `fixing-accessibility`、`wcag-audit-patterns`、`accessible-animation` |
| 苹果 / iOS | 2 | `apple-design`、`swiftui-ui-patterns` |

## 本项目实际引用的条款

| 结论 | 依据技能 | 条款 |
|---|---|---|
| 中性色须与品牌色同色相方向（v1 冷灰 286° → v2 150°） | `impeccable` | 中性色须带品牌偏移，禁止死灰 |
| 禁止纯黑 `#000` / 纯白 `#FFF` | `impeccable` | 真实材质无纯黑白 |
| 正文 16px、`rem`、24px 垂直韵律 | `impeccable` + `apple-design` §15 | 正文最低 16px；spacing 用 rem/em |
| 字距随字号变化（大号收紧、小字放开） | `apple-design` §15 | tracking 是 size-specific |
| **吸顶栏去掉硬 1px 线，改滚动边缘渐隐** | `apple-design` §12 | Scroll edge effects, not hard dividers |
| **弹层入场 blur 与位移同步** | `apple-design` §12 | Materialize, don't just fade |
| 数据必须 `tabular-nums` | `baseline-ui` | MUST use tabular-nums for data |
| 减弱动效降级 | `apple-design` §14 | Reduced motion → 交叉淡入 |
| 审查只认「契约+运行时+修正」三证明 | `improve-ui` §3 | 三证明门；最多报告 3 条 |

## 未采用的技能及原因

| 技能 | 原因 |
|---|---|
| `progressive-blur` | 需叠加 6 层 `backdrop-filter` 实现渐变模糊，Android WebView 上离屏渲染开销大；本项目目标机型性能敏感（用户曾反馈卡顿），暂不采用，保留为高端机可选项 |
| `tailwindcss` / `shadcn` / `vue*` / `nextjs` 等 | 本项目为无框架单文件 HTML，技术栈不适用 |

## 许可

各技能著作权归其原作者所有，由 `ui-skills` 以 MIT 聚合收录。本项目仅在开发过程中**参考其方法论**，未复制其代码，也未将其文档再分发（本地安装副本不入库）。
