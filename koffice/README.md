# KOffice Android 移植

KOffice 是 KEMI 双屏 Android 设备上的离线办公套件移植工程，基于
[Collabora Online](https://gerrit.collaboraoffice.com/online) 的 Android 应用和
LibreOffice engine。

[![KOffice Android Build](https://github.com/caucy2026/kemi-rd/actions/workflows/koffice-build.yml/badge.svg)](https://github.com/caucy2026/kemi-rd/actions/workflows/koffice-build.yml)

## 产品边界

- DOCX、XLSX、PPTX：离线查看、编辑和保存。
- PDF：仅查看和搜索，不提供编辑或覆盖保存。
- 办公窗口可运行在主屏或副屏，输入法显示在另一块屏幕。
- 编辑器保留原生 `InputConnection`，跨屏输入由 KEMI 系统 IME 路由实现。

## 上游基线

- 仓库：`https://gerrit.collaboraoffice.com/online`
- 分支：`main`
- 初始提交：`b91cb7428e620f5c34c2ff94d7f8ce2a7d494c62`
- 目标 ABI：`arm64-v8a`
- 目标系统：Android 12（API 31）

上游源码放在 `upstream/`，KEMI 自有配置、补丁和验证工具保留在本目录，避免把产品改动混入不可追踪的源码副本。

## 目录

```text
koffice/
├── README.md
├── patches/                 # 可重放的 KEMI 产品补丁
├── scripts/                 # 获取、构建、部署和验证脚本
└── upstream/                # 固定提交的 Collabora 官方源码
```

原生 engine 必须在 Linux 构建。macOS 可用于 Android Java/Kotlin 层开发、补丁维护和真机调试，但不能完成官方原生 engine 全量构建。

## 快速开始

初始化子模块并重放 KEMI 补丁：

```bash
./scripts/prepare-upstream.sh
./scripts/verify-source.sh
```

在 x86_64 Linux 构建机上编译 `arm64-v8a` Debug APK：

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
./scripts/build-android-linux.sh
```

构建脚本默认使用 NDK `28.2.13676358`，与当前上游 Android Gradle 工程一致。可以通过 `ANDROID_NDK_HOME` 或 `KOFFICE_NDK_VERSION` 覆盖，并通过 `KOFFICE_JOBS` 控制并行度。

产品构建参数：

- 应用名：`KOffice`
- applicationId：`org.kemi.koffice`
- ABI：`arm64-v8a`
- vendor：`KEMI`

## KEMI 补丁

`patches/0001-pdf-view-only.patch` 实现 PDF 严格只读：

- MIME、文件名或 URI 后缀识别为 PDF 时，Android 会话使用 `permission=readonly`。
- 禁止把 PDF 复制成可编辑文档。
- 禁止新增、修改、删除或管理 PDF 评论。
- 保留 PDF 打开、翻页、缩放、搜索和打印能力。

DOCX、XLSX 和 PPTX 不经过上述限制，仍按原有 Collabora 编辑链路工作。

## CI 自动构建

推送 `koffice/` 目录或 workflow 文件到 `main` 分支时，GitHub Actions 自动在 Ubuntu 云端构建 arm64-v8a APK。
也可以在 [Actions 页面](https://github.com/caucy2026/kemi-rd/actions/workflows/koffice-build.yml) 手动触发（`workflow_dispatch`）。

构建产物（Debug APK）保留 30 天，从 Actions run 的 Artifacts 下载。

## 当前验证状态

- 已验证上游 SHA、标准子模块布局和补丁可重复应用。
- 已通过 Shell 语法、Git 补丁反向应用和源码约束检查。
- GitHub Actions CI workflow 已就绪，推送后自动构建。
- 本地 macOS 环境无法完成 engine 交叉编译，CI 是第一构建渠道。
- 下一闭环：CI 产出 APK → 安装目标双屏设备 → 验证 Office 编辑与 PDF 只读行为。