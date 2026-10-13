# 体脂率测算 · Body Fat Calculator

一个**单文件、离线优先**的体脂率测算与热量管理 App，采用 iOS 26 风格的**液态玻璃（Liquid Glass）**界面。
既可以直接在浏览器打开网页版使用，也可以打包成安卓 APK 安装到手机。

![首页](docs/screenshots/01-home.png)

## ✨ 功能

| 模块 | 说明 |
|---|---|
| **体脂测算** | 支持美国海军围度法（腰围/颈围，女性加臀围）与 Deurenberg 公式法，男女分档给出体脂等级、脂肪重量、去脂体重、标准体重区间 |
| **历史记录** | 保存每次测算，绘制体脂率趋势折线图 |
| **饮食锻炼计划** | 按体脂区间 + 目标（减脂/维持/增肌）生成每日热量、三大营养素配比、四餐餐单、七天训练安排 |
| **计划 → 签到** | 选定计划后该页直接变成每日签到：训练 + 三餐 + 饮水，做完即算一天，并记录连续签到天数 |
| **拍照识餐** | 拍照后自动识别食物并估算克数；识别不到的食物**自动加入食物库**；无 AI 密钥时用**本地免费算法**给候选 |
| **热量记账** | 内置 230+ 种常见食物，按三餐时间窗归类；过了饭点没记会显示「没吃」；环形进度显示摄入/目标，超标变红 |
| **数据导出** | 可导出/导入 JSON 备份，数据全部存于本机 localStorage，不上传 |

## 📁 目录结构

```
.
├── 体脂率测算.html          # 网页版（单文件，双击即可用；也是 APK 内的页面）
├── 体脂率测算-1.1.0.apk      # 已签名的安卓安装包
├── android/                 # 安卓工程（WebView 壳 + Gradle 标准构建）
│   ├── settings.gradle
│   ├── build.gradle
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle
│       └── src/main/        # AndroidManifest / Java / res / assets
└── docs/screenshots/        # 界面截图
```

## 🚀 使用方法

**网页版**：把 `体脂率测算.html` 用浏览器打开即可（手机浏览器同样可用）。

**安卓版**：安装 `体脂率测算-1.1.0.apk`（需 Android 6.0 及以上）。

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

## 📷 拍照识餐说明

- **默认免费**：使用本地颜色识别算法（离线、无需密钥、不上传图片），识别后给出候选让你点选确认。
- **更精准（可选）**：在 App 内「设置 → AI 识餐」填入自己的视觉大模型密钥（OpenAI / 通义千问 / SiliconFlow 均可），即可自动识别具体菜名与克数。

## 🎨 界面

深色液态玻璃风格：悬浮玻璃 Dock、彩色 iOS 圆角图标、跟随手指的阻尼高光与 3D 倾斜、彩色光斑壁纸、卡片周期扫光。

![热量页](docs/screenshots/02-calories.png)

## 🔒 隐私

所有数据（测算记录、计划、签到、热量账本）保存在设备本地，**不会上传到任何服务器**。拍照识餐默认在本地处理；仅当你主动配置 AI 密钥时，图片才会发送给对应的云服务。

## 📄 许可

MIT License
