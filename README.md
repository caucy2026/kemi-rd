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

---

## 📚 知识库文档索引

| 文档名称 | 核心职责与关键能力 |
| :--- | :--- |
| 📄 **[dev-iron-rules.md](./dev-iron-rules.md)** | **底层开发铁律**：约束 AI/开发者的核心准则（路径锁定 / 自测闭环交付 / 死磕到底），杜绝退缩与未验证交付。 |
| 📄 **[chip.md](./chip.md)** | **芯片平台 & 双屏异显实战手册（主文档）**：双 Activity & Presentation 架构、D2 误启动硬件级反射重定向（`setLaunchDisplayId`）、生命周期与 `Process.killProcess` 进程清理、WiFi ADB 部署与踩坑全集。 |
| 📄 **[dscr.md](./dscr.md)** | **免弹窗无感双屏录制**：基于 `SurfaceControl.Transaction` 隐藏 API 的底层录影方案，直接绑定 SurfaceFlinger 图层栈自动灌帧硬编码，无需 `MediaProjection` 弹窗。 |
| 📄 **[iflytek_asr_interface_doc.md](./iflytek_asr_interface_doc.md)** | **系统级语音听写指南**：基于 `Settings.Global` 凭证共享，无需应用申请 Key，支持“按住说话”（Hold-to-Speak）与“自动聆听”（Auto-Listen）状态机。 |
| 📄 **[rule.md](./rule.md)** | **开发行为规范（CLAUDE.md）**：保持代码精简干净，外科手术式修改，严禁过度设计。 |

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
3. **AI 自动套用防呆模版**：AI 会自动提取 `chip.md` 中的架构代码与防呆逻辑，遵循 `dev-iron-rules.md` 生成无 Bug 代码。
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
