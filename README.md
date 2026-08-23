# 🚀 KEMI-S1 双屏 Vibe-Coding 开发者套件

> **面向 AI-Native / Vibe-Coding 的 Android 双屏异显与系统能力开发知识库**

![Platform](https://img.shields.io/badge/Platform-RK356x%20%7C%20HiSilicon%20V900-blue)
![Android](https://img.shields.io/badge/Android-12.0%20(API%2031)-green)
![JDK](https://img.shields.io/badge/JDK-17-orange)
![VibeCoding](https://img.shields.io/badge/AI--Native-Vibe%20Coding-purple)

---

## 📖 项目简介

**KEMI-S1 双屏开发指南** 是一套专为开发者及 AI 编程助手（如 Antigravity, Cursor, Claude Code 等）打造的软硬件一体化开发知识库。

本项目封装了基于 **Rockchip RK356x** 与 **HiSilicon V900** 芯片平台（Android 12）开发双屏异显应用时的所有架构模板、底层隐藏 API 调优、系统级能力（录影、语音听写）以及大量真机踩坑避坑经验。把本知识库作为上下文提供给 AI，即可通过**自然语言（Vibe Coding）**极速生成专业、稳健、无 Bug 的双屏应用。

## 📁 项目目录结构

```text
kemi-rd/
├── README.md                           # 项目根主页指南
├── .gitignore
├── .github/
│   └── workflows/                      # CI/CD 工作流
└── md/                                 # 核心 Markdown 技术与规范文档
    ├── android-http-file-server.md     # Android 内置 HTTP 文件分发服务
    ├── hbbc-http-https-service-guide.md # PAD HTTP与HBBC云端HTTPS统一分工
    ├── chip.md                         # 芯片平台 & 双屏异显实战手册
    ├── ci-build.md                     # 本地、云端构建协调与加速
    ├── cross-display-keyboard.md       # 跨屏软键盘需求与设计
    ├── deepseek-quant-plan.md          # DeepSeek 智能量化交易系统规划
    ├── dev-iron-rules.md               # 底层开发铁律
    ├── dscr.md                         # 免弹窗无感双屏录制
    ├── huawei-apk-trust-rustdesk.md    # 华为设备 APK 签名与分发合规
    ├── iflytek_asr_interface_doc.md    # 系统级语音听写对接指南
    ├── requirements_v2.md              # STK 双屏对战完整需求文档 v2.0
    ├── reseach.md                      # 双屏开源组合与硬件算力调研报告
    ├── rule.md                         # 开发行为规范 (CLAUDE.md)
    ├── stock-software-selection.md     # 双屏股票软件开源组合选型
    └── vibecoding-prompt.md            # 双屏 AI Vibe-Coding 系统提示词
```

---

## 📚 知识库文档索引

| 文档名称 | 核心职责与关键能力 |
| :--- | :--- |
| 📄 **[chip.md](./md/chip.md)** | **芯片平台 & 双屏异显实战手册（主文档）**：双 Activity & Presentation 架构、D2 误启动硬件级反射重定向（`setLaunchDisplayId`）、生命周期与 `Process.killProcess` 进程清理、WiFi ADB 部署与踩坑全集。 |
| 📄 **[dual-single-screen-launch-architecture.md](./md/dual-single-screen-launch-architecture.md)** | **双屏/单屏启动与连续画布架构**：从任意屏启动、公开 `launchDisplayId` 路由、一个渲染会话两块 Surface、触摸坐标、IME、退出联动、黑屏与抖动避坑及真机验收。 |
| 📄 **[vibecoding-prompt.md](./md/vibecoding-prompt.md)** | **双屏 AI Vibe-Coding 系统提示词**：自包含的系统级 Prompt，涵盖硬件、双屏架构模板、防呆、生命周期、跨屏通信、键盘、录制、语音和编码铁律。粘贴到 AI 编程工具即可直接生成可编译的双屏 APK。 |
| 📄 **[cross-display-keyboard.md](./md/cross-display-keyboard.md)** | **跨屏软键盘需求与设计**：双屏设备软键盘行为、状态模型、原生与 Flutter 职责边界，以及重构和验收标准。 |
| 📄 **[dev-iron-rules.md](./md/dev-iron-rules.md)** | **底层开发铁律**：约束 AI/开发者的核心准则（路径锁定 / 自测闭环交付 / 死磕到底），杜绝退缩与未验证交付。 |
| 📄 **[rule.md](./md/rule.md)** | **开发行为规范（CLAUDE.md）**：保持代码精简干净，外科手术式修改，严禁过度设计。 |
| 📄 **[dscr.md](./md/dscr.md)** | **免弹窗无感双屏录制**：基于 `SurfaceControl.Transaction` 隐藏 API 的底层录影方案，直接绑定 SurfaceFlinger 图层栈自动灌帧硬编码，无需 `MediaProjection` 弹窗。 |
| 📄 **[iflytek_asr_interface_doc.md](./md/iflytek_asr_interface_doc.md)** | **系统级语音听写指南**：基于 `Settings.Global` 凭证共享，无需应用申请 Key，支持"按住说话"（Hold-to-Speak）与"自动聆听"（Auto-Listen）状态机。 |
| 📄 **[reseach.md](./md/reseach.md)** | **硬件算力与开源组合调研报告**：结合 V900/RK356x 芯片（6G RAM, 8核 A73, Mali-G52）精准评估 5 大双屏开源项目组合的 CPU/GPU 内存负载。 |
| 📄 **[stock-software-selection.md](./md/stock-software-selection.md)** | **双屏股票软件开源组合选型**：对比 Android 图表、A 股行情、研究与交易后端，给出只读行情、模拟交易和真实交易评审的分阶段架构。 |
| 📄 **[deepseek-quant-plan.md](./md/deepseek-quant-plan.md)** | **DeepSeek 智能量化交易系统规划**：用户只需填入 DeepSeek API Key 即可独立运行。涵盖 AI 选股、AI 看盘、AI 策略生成、AI 财报解读、量化回测、模拟交易的全架构设计和 6 阶段实施路线。 |
| 📄 **[requirements_v2.md](./md/requirements_v2.md)** | **STK 双屏对战完整需求文档 v2.0**：双屏独立渲染架构、虚拟方向盘操控、道具/漂移系统、局域网对战协议、AI 行为树等。 |
| 📄 **[android-http-file-server.md](./md/android-http-file-server.md)** | **Android 内置 HTTP 文件分发服务**：零配置局域网文件分发，内置 HTTP Server + NanoHTTPD，支持 Windows 客户端一键下载安装。 |
| 📄 **[hbbc-http-https-service-guide.md](./md/hbbc-http-https-service-guide.md)** | **PAD本地HTTP与HBBC云端HTTPS统一说明**：明确8686/8687局域网服务、21120兼容入口、21121 HTTPS下载与账号接口、证书和验收规则。 |
| 📄 **[ci-build.md](./md/ci-build.md)** | **本地、云端构建协调与加速**：分支策略、CI 触发器、候选版本身份、Rust/Gradle/Flutter/NDK 缓存分层与实施优先级。 |
| 📄 **[huawei-apk-trust-rustdesk.md](./md/huawei-apk-trust-rustdesk.md)** | **华为设备 APK 签名与分发合规**：RustDesk 移植版发布签名、包名、远控合规和华为 AppGallery 审核要点。 |

---

## 🔥 核心技术亮点与防护网

### 1. 🛡️ 双屏异显与自愈防呆 (Self-Healing Dual Display)
* **双 Activity 独立架构**：主屏 (Display 0) 与副屏 (Display 2) 独立生命周期、独立 UI 布局与独立触摸事件（副屏配置 `launchMode="singleInstance"`）。
* **启动器防呆重定向**：若应用被副屏启动器误拉起在 Display 2，`onCreate` 阶段会自动触发反射调用 `ActivityOptions.setLaunchDisplayId(0)` 强制平滑迁移回主屏 D0。
* **干净退出机制**：配置 `excludeFromRecents="true"`，并结合 HOME 键硬杀进程（`Process.killProcess()`），彻底消除 NDK/C++ 原生线程在异步退出时的竞态黑屏。

### 2. 🎥 零交互后台双屏录制 (Stealth Dual-Screen Recording)
* 依赖 AOSP 平台签名与 `android.uid.system` 权限。
* 利用 `SurfaceControl.createDisplay` 与 `setDisplayLayerStack`，直接将物理屏 LayerStack 路由至 `MediaCodec` Input Surface，实现主屏 / 副屏的高性能 H.264 / MP4 后台无感录影。

### 3. 🎙️ 全局配置共享 ASR 语音服务
* 从 `Settings.Global.getString(cr, "iflytek_params")` 统一获取鉴权 Token 与设备 MAC 派生的 `auth_id`。
* 内置完整状态机：支持 `cloud_vad_eos=60000` 搭配客户端松手发 `--end--` 的按住模式，以及 `cloud_vad_eos=3000` 靠云端 VAD 判停的自动模式。

---

## 🤖 如何使用本知识库进行 Vibecoding？

1. **引入上下文**：将本仓库或文档添加到 AI Agent（如 Antigravity、Cursor）的上下文规则中。
2. **自然语言交互**：直接向 AI 提出业务需求，例如：
   > 💬 *“帮我用双 Activity 架构写一个双屏应用：主屏显示控制面板，副屏显示 3D 地球；如果被误启动到副屏要能自动切回主屏。副屏退出时通知主屏弹窗确认。”*
3. **AI 自动套用防呆模版**：AI 会自动提取 `md/chip.md` 中的架构代码与防呆逻辑，遵循 `md/dev-iron-rules.md` 生成无 Bug 代码。
4. **真机闭环验证**：使用下文脚本一键部署测试。

---

## ⚡ 快速开发与调试脚本

### 环境要求
- **Android SDK**: compileSdk 34, minSdk 31 (Android 12+)
- **JDK**: 17

### 一键部署命令
```bash
# 1. WiFi ADB 连接真机
adb connect 192.168.3.46:5555

# 2. 编译并部署启动
./gradlew clean assembleDebug && \
  adb -s 192.168.3.46:5555 install -r app/build/outputs/apk/debug/app-debug.apk && \
  adb -s 192.168.3.46:5555 shell am start -n com.globe.dualscreen/.MainActivity

# 3. 截取 Display 2 副屏画质
adb shell screencap -d 2 -p /sdcard/display2.png && adb pull /sdcard/display2.png
```

---

## 📄 License & Maintainers

Maintained by **caucy2026**.  
Based on real-world production experience from **Go3DGlobe** & **GoDualScreen** projects on RK356x / V900 platforms.
