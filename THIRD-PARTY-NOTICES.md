# 第三方依赖与许可声明（THIRD-PARTY NOTICES）

本项目（FitNote / 体脂率测算）整体以 **CC BY-NC-SA 4.0** 许可发布（**禁止商业用途**），见 [LICENSE](LICENSE)。
以下是随本项目一起分发或运行时加载的第三方组件。**"自研"仅指本项目的判定层与算法**（体脂测算、热量计算、计划生成、动作判定与计数逻辑、肌群着色），不含下列上游模型与运行时。

> 许可兼容性说明：本项目整体采用「禁止商业」的 CC BY-NC-SA 4.0；下列第三方组件各按其自身许可证（多为 MIT / Apache-2.0，允许商业）。下游使用者须**同时遵守**本项目的非商业条款与各第三方组件各自的许可证条款。

---

## 1. onnxruntime-web 1.17.1

| 项 | 内容 |
|---|---|
| 用途 | 浏览器/WebView 内的 ONNX 本地推理运行时 |
| 许可 | **MIT** |
| 来源 | https://github.com/microsoft/onnxruntime |
| 分发方式 | 从 CDN 获取，**不入库**（见仓库 `.gitignore`） |

| 文件 | 大小 | SHA-256 |
|---|---|---|
| `ort.min.js` | 528 KB | `1dd624bd217ab652f7d6715440d0807b7daa2999fa598eb9254fff7c69e89d0f` |
| `ort-wasm-simd.wasm` | 11 MB | `0855759fb0ef86d4c64df1ccaaabead0ec402a68a658ad107cf2e3616b5fd48f` |

获取地址：`https://cdn.jsdelivr.net/npm/onnxruntime-web@1.17.1/dist/ort.min.js` 与同目录 `ort-wasm-simd.wasm`

## 2. mobilenet_v2_food101.onnx（食物识别模型）

| 项 | 内容 |
|---|---|
| 用途 | 离线食物识别（101 类，Food-101） |
| 来源 | ONNX Model Zoo：https://github.com/onnx/models |
| 许可 | 遵循 ONNX Model Zoo 仓库声明的许可（**Apache-2.0**） |
| 数据集 | Food-101 由 ETH Zurich（Lukas Bossard 等）构建，**数据集版权归原作者**；本项目仅分发从 ONNX Model Zoo 获取的**模型权重**，不分发原始图像数据 |
| 分发方式 | 随 APK 分发；源码仓库不入库 |

| 文件 | 大小 | SHA-256 |
|---|---|---|
| `mobilenet_v2_food101.onnx` | 9.4 MB | `af237923fd4636f5c6156059e372871a2b63e4a851f377f1b92ae8c71d018890` |

## 3. 姿态估计模型（规划中，v1.1 视频识别）

| 项 | 内容 |
|---|---|
| 候选 | MoveNet（TensorFlow）／ MediaPipe Pose（Google） |
| 许可 | **Apache-2.0** |
| 分发方式 | 首次使用视频识别时按需加载，源码仓库不入库 |
| 说明 | 上线时补全具体版本、文件与 SHA-256 |

## 4. 食物营养数据（《中国食物成分表标准版 第 6 版》）

| 项 | 内容 |
|---|---|
| 用途 | App 扩展食物库（新增 1,566 条；与内置 231 条合并后共 **1,797 种**） |
| 原始出处 | 《中国食物成分表标准版（第 6 版）》——中国疾病预防控制中心营养与健康所 编著 |
| 结构化整理 | https://github.com/Sanotsu/china-food-composition-data —— 第三方对该书「能量和食物一般营养成分」表格的截图 + OCR / 视觉大模型识别结果 |
| **⚠️ 许可状态** | **该仓库未声明任何开源许可，根目录也没有 LICENSE 文件**。按 GitHub 默认规则即「保留所有权利，All Rights Reserved」。此外原书本身为**有版权的出版物** |
| 本项目的处理 | **仅做格式转换与类别映射**（CSV → 紧凑 JSON；61 个原始类别 → 13 个 App 分类），剔除 19 条中药滋补类与 34 条无有效能量值条目，并按名称与既有内置库去重。**未改动任何营养数值** |
| 分发方式 | 派生文件 `assets/foods.json`（90.7 KB），随 APK 与源码仓库一起分发 |

| 文件 | 大小 | SHA-256 |
|---|---|---|
| `foods.json` | 90.7 KB | `7867f799b9794bbfdeb6606437e5fb73f58e6a6964a4c64aa12857ee7014d8c5` |

**风险提示与替代方案**

1. 营养成分数值本身属于**事实数据**，在多数法域不受著作权保护；但原书作为**汇编作品**可能就「内容的选择与编排」主张权利，且该第三方仓库对其整理成果**未作任何许可声明**。
2. 本项目将其用于**非商业的开源个人项目**，并在本文件与 App 内显著注明来源。**如需商业分发，请自行向原出版方取得授权。**
3. 该数据以**独立文件** `assets/foods.json` 的形式存在、而非内嵌进页面，因此**可整体替换**：删掉这个文件即回到「仅含 231 条内置食物」的状态，其它功能完全不受影响。
4. 若你更看重许可的确定性，可换用 **USDA FoodData Central**（美国农业部，**CC0 公共领域**，无任何使用限制）——缺点是中餐条目覆盖较差。替换时只需按同样格式重新生成 `foods.json`。

---

## 5. 液体玻璃算法（Kyant0/AndroidLiquidGlass）

| 项 | 内容 |
|---|---|
| 用途 | 底部导航 / 吸顶栏 / 弹层的「液态玻璃」折射与色散效果 |
| 来源 | https://github.com/Kyant0/AndroidLiquidGlass |
| 许可 | **Apache-2.0**（允许使用、修改、再分发，需保留版权声明） |
| 本项目如何使用 | **仅移植其着色器算法思路，并在 JavaScript 中重新实现**：<br>· 解析梯度 `gradSdRoundedRect`（表面法线）<br>· 折射曲线 `circleMap(x) = 1 − √(1 − x²)`<br>· 深度项 `depthEffect × normalize(coord)`<br>· 圆角矩形 SDF `sdRoundedRect` |
| 未使用的部分 | 未引入其 Kotlin/Compose 运行时、未使用其 Wasm 产物、未复制其源码文件 |
| 原因 | 上游是 Compose Multiplatform 原生库，本项目为 WebView + 单文件 HTML；<br>玻璃需折射「背后的真实 DOM 内容」，Web 端只有 `backdrop-filter: url(#svgFilter)` 能做到，因此正确路线是移植**算法**而非运行时 |
| 源码位置 | `assets/index.html` 内 `LiquidGlass` 模块，注释中已标注来源与许可 |

> 我们在此致谢 Kyant0 的开源工作。若上游作者认为本项目的移植方式不妥，请联系后我们会立即调整或移除。

---

## 6. 人体肌群图数据（react-body-highlighter）

| 项 | 内容 |
|---|---|
| 用途 | 「肌肉热力图」的人体正/背面肌群矢量多边形（22 个肌群） |
| 来源 | https://github.com/GV79/react-body-highlighter |
| 版本 | 2.0.5 |
| 许可 | **MIT**（允许使用、修改、再分发，需保留版权声明） |
| 本项目如何使用 | 提取其 `anteriorData` / `posteriorData` 中的肌群多边形点串，转换为紧凑 JSON 嵌入 `design/body.js`；未引入其 React 运行时 |
| 源码位置 | `design/body.js`（`BODY` 对象），注释中已标注来源与许可 |

---

## 复用本项目时的注意事项

1. **请勿声称对上述上游模型的所有权**；如需再分发模型文件，请一并保留本节与上游许可文本。
2. 构建时请按上表 **校验 SHA-256**，确保与你测试过的版本一致（模型文件不同会导致识别结果不可复现）。
3. 本项目不含 Food-101 数据集图像，也不含任何用户数据。

*本文件随版本更新；如发现遗漏的第三方组件，请提 Issue。*
