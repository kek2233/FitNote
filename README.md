# FitNote

**装在手机里的私人身体数据本** —— 训练、体脂、饮食，一本记清。

单文件、离线优先的安卓 App（WebView 壳，无框架），采用 iOS 26 风格 **液态玻璃（Liquid Glass）** 界面。网页版双击即用，安卓可直接安装 APK。

> **开源但禁止商业**：代码完全公开，可自由复制、修改、自用；**禁止商业用途**。许可证 CC BY-NC-SA 4.0（见文末）。

## ✨ 功能

| 模块 | 说明 |
|---|---|
| **今日聚合首页** | 今日训练主卡（今日计划/进行中的训练一键继续）+ 今日已摄入/训练容量/连续记录 + 饮食进度 + 身体基线 |
| **训练记录** | 中文动作库 122 个（100% 配同类替代），极速记组（重量/次数步进器）、组间休息倒计时、总时长计时、切换/添加动作、训练总结（总容量/组数/时长/PR 突破） |
| **肌肉热力图** | 人体肌群图（正/背面 22 肌群）按本周训练量分档着色（未练/偏低/中等/达标/过量）+ 部位明细 + 推拉平衡弱项提示 |
| **选动作部位高亮** | 选动作时人体图高亮目标肌群（主发力实色 / 协同肌群淡色），动作详情页给出同类替代 |
| **体脂测算** | 美国海军围度法 + Deurenberg 公式，男女分档；脂肪重量、去脂体重、BMI、标准体重区间 |
| **饮食计划** | 按目标（减脂/维持/增肌）生成每日热量、三大营养素、四餐餐单、七天训练安排 |
| **拍照识餐（离线）** | 内置 Food-101 MobileNetV2 ONNX 模型（完全离线识别 101 类）+ 免费本地颜色算法兜底 + 可选 AI 密钥通道 |
| **热量记账** | 三餐时间窗自动归类，进度条显示摄入进度，动态推荐该餐还能吃什么 |
| **食物库** | 1,797 种（内置 + 《中国食物成分表》第 6 版 1,566 条），搜索、分类、一键记餐、自定义增删 |
| **统计与日历** | 近 8 周训练容量趋势、近 30 天肌群占比、训练日历（已练/未练/今天）、PR 三种口径（1RM/最大重量/最大容量） |
| **数据备份** | 每次改动自动本机快照，JSON 导出/导入；数据全部存本机 localStorage，不上传 |

## 🧭 应用结构

```
今日 ──── 记录 ──── [ ＋ ] ──── 计划 ──── 我的
```

- **今日**：当天训练卡 + 摄入/容量/连续记录 + 饮食 + 身体
- **记录**：训练 / 饮食 / 体脂 三类按天聚合，可筛选
- **＋**：开始训练 / 记一餐 / 录体脂 / 查食物库
- **计划**：本周进度 + 周安排 + 调整建议
- **我的**：身体档案、本月概览、容量趋势、肌群占比、肌肉热力图、训练日历、数据与设置

首次启动有 3 步引导（欢迎 → 身体数据 → 选起点），可跳过。

## 📁 目录结构

```
.
├── FitNote-2.0.0.apk        # 已签名安卓安装包（内含离线识别模型）
├── gradle-app/              # 安卓工程（WebView 壳；Gradle 或本地 aapt2/d8 构建）
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bodyfat/app/    # MainActivity + FileProvider
│       ├── res/                      # 图标 / 字符串（app_name = FitNote）
│       └── assets/
│           ├── index.html            # 应用本体（单文件，全部逻辑+样式内联）
│           ├── foods.json            # 《中国食物成分表》扩展库
│           ├── exercises.json        # 动作库
│           ├── mobilenet_v2_food101.onnx   # 离线识餐模型（gitignore，见下）
│           └── ort.min.js / ort-wasm-simd.wasm
├── foods.json / exercises.json       # 与 assets 同源
├── docs/                    # 产品 brief、功能清单、设计规范、设计稿（暗/亮 18 屏）
├── design-plans/            # UI 演进记录（tabular-nums、v2 重设计）
├── THIRD-PARTY-NOTICES.md   # 第三方依赖与数据来源声明
└── LICENSE                  # CC BY-NC-SA 4.0
```

> 离线识餐所需的大体积运行时（`ort.min.js`、`ort-wasm-simd.wasm`、`mobilenet_v2_food101.onnx`）按 `.gitignore` 不入库，来源见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 🔨 构建 APK

依赖：JDK、Android SDK（build-tools 34.0.0 + platform android-34）。

```bash
# 使用本地 aapt2/d8 构建（免 Gradle）
export JAVA_HOME=/path/to/jdk
BT=/path/to/android-sdk/build-tools/34.0.0
PLATFORM=/path/to/android-sdk/platforms/android-34/android.jar
ROOT=gradle-app

$BT/aapt2 compile --dir $ROOT/app/src/main/res -o res.zip
$BT/aapt2 link -o base.apk -I $PLATFORM --manifest $ROOT/app/src/main/AndroidManifest.xml -A $ROOT/app/src/main/assets res.zip
javac --release 8 -classpath $PLATFORM -d classes \
  $ROOT/app/src/main/java/com/bodyfat/app/MainActivity.java \
  $ROOT/app/src/main/java/com/bodyfat/app/FileProvider.java
$BT/d8 --release --min-api 23 --lib $PLATFORM --output dex $(find classes -name '*.class')
zip -j base.apk dex/classes.dex
$BT/zipalign -f -p 4 base.apk aligned.apk
$BT/apksigner sign --ks $ROOT/app/release.keystore --ks-key-alias bft \
  --ks-pass pass:android --key-pass pass:android --out FitNote-2.0.0.apk aligned.apk
```

> ⚠️ 关键：`d8` 必须传入 **全部 `.class`**（`$(find classes -name '*.class')`），漏传内部类会导致启动即闪退（`NoClassDefFoundError`）。

## 📄 许可证

本项目采用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)（署名-非商业性使用-相同方式共享）。

- **可**：自由复制、修改、分发、自用
- **须**：署名（保留作者与出处）、相同方式共享
- **禁止**：任何商业用途

第三方依赖与数据来源见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
