# 第三方依赖与许可声明（THIRD-PARTY NOTICES）

本项目（FitNote / 体脂率测算）以 **MIT** 许可发布，见 [LICENSE](LICENSE)。
以下是随本项目一起分发或运行时加载的第三方组件。**"自研"仅指本项目的判定层与算法**（体脂测算、热量计算、计划生成、动作判定与计数逻辑），不含下列上游模型与运行时。

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

---

## 复用本项目时的注意事项

1. **请勿声称对上述上游模型的所有权**；如需再分发模型文件，请一并保留本节与上游许可文本。
2. 构建时请按上表 **校验 SHA-256**，确保与你测试过的版本一致（模型文件不同会导致识别结果不可复现）。
3. 本项目不含 Food-101 数据集图像，也不含任何用户数据。

*本文件随版本更新；如发现遗漏的第三方组件，请提 Issue。*
