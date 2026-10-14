# 体脂率测算 · Body Fat Calculator

一个**单文件、离线优先**的体脂率测算与热量管理 App，采用 iOS 26 风格的**液态玻璃（Liquid Glass）**界面。
既可以直接在浏览器打开网页版使用，也可以安装安卓 APK。

![首页](docs/screenshots/01-home.png)

## ✨ 功能

| 模块 | 说明 |
|---|---|
| **体脂测算** | 美国海军围度法（腰围/颈围，女性加臀围）与 Deurenberg 公式法；男女分档给出体脂等级、脂肪重量、去脂体重、标准体重区间 |
| **历史记录** | 保存每次测算，绘制体脂率与**体重趋势**折线图，支持删除单条 |
| **数据统计** | 近 7 天「实际热量 vs 目标」柱状图（超标变红）、近 7 天签到完成率、**今日三餐热量占比环形图** |
| **饮食锻炼计划** | 按体脂区间 + 目标（减脂/维持/增肌）生成每日热量、三大营养素配比、四餐餐单、七天训练安排 |
| **计划 → 签到** | 选定计划后该页直接变成每日签到：训练 + 三餐 + 饮水，做完即算一天，并记录连续签到天数 |
| **拍照识餐（离线 AI）** | 内置 Food-101 的 MobileNetV2 ONNX 模型，**完全离线**识别 101 类食物并映射到中文名与热量；自动记入当前餐，支持一键**纠正/撤销** |
| **食物库** | 232+ 种食物（主食/肉蛋/水产/蔬菜/水果/坚果/饮品/零食/快餐/汤羹/油脂/调味/豆奶），支持搜索、分类筛选、一键记餐、**自定义增删**（内置食物受保护不可删） |
| **热量记账** | 按三餐时间窗归类；过了饭点没记会显示「没吃」；环形进度显示摄入/目标，超标变红；未收录的食物**自动入库** |
| **数据备份** | 每次改动**自动在本机留快照**，可一键恢复；JSON 导出（安卓下直接写入「下载」目录）/ 导入；数据全部存于本机 localStorage，不上传 |

## 📁 目录结构

```
.
├── 体脂率测算.html          # 网页版（单文件，双击即可用；也是 APK 内的页面）
├── 体脂率测算-1.2.0.apk      # 已签名的安卓安装包（内含离线识别模型）
├── android/                 # 安卓工程（WebView 壳 + Gradle 标准构建）
│   ├── settings.gradle
│   ├── build.gradle
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/bodyfat/app/     # MainActivity（WebView + 原生桥）
│           ├── res/                      # 图标 / 字符串
│           └── assets/
│               └── index.html            # 打包进 APK 的页面
└── docs/screenshots/        # 界面截图
```

## 🚀 使用方法

**网页版**：把 `体脂率测算.html` 用浏览器打开即可（手机浏览器同样可用）。
离线形态识别需要同目录放置 `ort.min.js` / `ort-wasm-simd.wasm` / `mobilenet_v2_food101.onnx`；缺失时会优雅降级为本地颜色识别算法。

**安卓版**：安装 `体脂率测算-1.2.0.apk`（Android 6.0 及以上），模型与运行时已随包内置，首次识别无需联网、无需任何密钥。

## 🔨 自行构建 APK

前置：JDK 17+、Android SDK（platform 34 + build-tools 34）。

```bash
cd android

# 配置 SDK 路径（二选一）
export ANDROID_HOME=/path/to/android-sdk
# 或写入 local.properties: sdk.dir=/path/to/android-sdk

# 首次构建前生成签名文件（仓库不含 keystore，避免私钥泄露）
keytool -genkeypair -v -keystore app/release.keystore -alias bft \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass android -keypass android \
  -dname "CN=BodyFat, OU=App, O=App, L=CN, ST=CN, C=CN"

# 构建
gradle assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

### 可选：启用离线食物识别

仓库内的 APK 已内置模型。若要从源码构建出同样的离线识别能力，需下载三个文件放到 `android/app/src/main/assets/`：

| 文件 | 大小 | 来源 |
|---|---|---|
| `ort.min.js` | 540 KB | [onnxruntime-web **1.17.1**](https://cdn.jsdelivr.net/npm/onnxruntime-web@1.17.1/dist/ort.min.js)（须用此版本：1.19+ 为 ESM/多线程，`file://` 下无法加载） |
| `ort-wasm-simd.wasm` | 10.1 MB | 同上 CDN，同版本 `dist/` 目录 |
| `mobilenet_v2_food101.onnx` | 9.4 MB | ONNX Model Zoo 的 Food-101 MobileNetV2（101 类，Apache-2.0） |

缺失时 APK 依然可用，识别会降级为本地颜色算法。

## 📷 拍照识餐说明

识别链路为三级降级，**全部离线**：

1. **本地 ONNX 模型**（默认）：MobileNetV2 + Food-101，101 类；top-1 自动记入当前餐，置信度 <15% 时提示确认，可点其他候选纠正或一键撤销。实测加载约 0.4s、单次推理约 50ms。
2. **本地颜色算法**：无模型时的兜底，给出候选让你点选。
3. **可选 AI 识餐**：在「设置 → AI 识餐」填入视觉大模型密钥（OpenAI / 通义千问 / SiliconFlow 均可）可获得更精准的菜名与克数——这会联网。

> 注意：模型只认得 Food-101 收录的 101 类（偏西餐/日料）。对典型中餐会退化为「最接近的已知类 + 中文热量映射」，所以 UI 提供了候选切换与撤销来保证可用性。

## 🎨 界面

深色液态玻璃风格：悬浮玻璃 Dock（5 项：测算/记录/计划/热量/食物库）、彩色 iOS 圆角图标、跟随手指的阻尼高光与 3D 倾斜、彩色光斑壁纸、卡片周期扫光、SDF 位移图实现的真折射玻璃。

![热量页](docs/screenshots/02-calories.png)

![记录页·数据统计](docs/screenshots/03-history.png)
> 记录页：体脂率/体重双趋势、近 7 天热量 vs 目标、签到完成率、今日热量来源

![食物库](docs/screenshots/04-food.png)
> 食物库：232+ 种食物，搜索/分类筛选/一键记餐/自定义增删

## 🔒 隐私

所有数据（测算记录、计划、签到、热量账本、自定义食物）保存在设备本地，**不会上传到任何服务器**。
默认识别流程完全在本机完成；仅当你主动配置 AI 密钥时，图片才会发送给对应的云服务。导出备份通过原生桥写入设备「下载」目录，不经网络。

## 📄 许可

MIT License
