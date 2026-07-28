# KOffice 移植交付与节点验收清单

更新日期：2026-07-28
仓库：kemi-rd
基线提交：0966f38

## 1. 目标与范围

- 目标：形成可持续维护的 Collabora Android 移植交付链路。
- 范围：源码准备、补丁落地、CI 构建、产物交付、设备验收、发布封板。
- 边界：PDF 仅查看，不进入编辑能力验收。

## 2. 分环节关键节点

### 环节 A：源码与基线准备

关键节点
- A1. 上游子模块可定位到固定 SHA。
- A2. 补丁目录完整，脚本可执行。
- A3. verify-source 校验通过。

执行命令
- `cd /Users/newlink/kemi/kemi-rd/koffice/upstream && git rev-parse --short HEAD`
- `cd /Users/newlink/kemi/kemi-rd && bash koffice/scripts/verify-source.sh`

通过标准
- 上游 SHA 与预期一致。
- verify-source 输出 passed。

本轮验收结果
- A1: 通过（b91cb74）
- A2: 通过（koffice/patches, koffice/scripts 均存在）
- A3: 通过（KOffice source verification passed）

结论
- 环节 A 通过。

### 环节 B：补丁落地一致性

关键节点
- B1. PDF 只读补丁在补丁集中存在且版本受控。
- B2. 双屏 + 跨屏 IME 补丁在补丁集中存在且版本受控。
- B3. 上游子模块工作区状态与预期一致（补丁引入的变更可见）。

执行命令
- `cd /Users/newlink/kemi/kemi-rd && ls koffice/patches`
- `cd /Users/newlink/kemi/kemi-rd/koffice/upstream && git status --short`

通过标准
- 0001、0002 补丁文件存在。
- 上游变更项与补丁内容方向一致。

本轮验收结果
- B1: 通过（0001-pdf-view-only.patch 存在）
- B2: 通过（0002-dual-screen-ime.patch 存在）
- B3: 通过（Manifest、LOActivity、UIActivity、docstate、CommentSection 等改动可见，新增 DualScreenHelper 与 ImeProxyActivity）

结论
- 环节 B 通过。

### 环节 C：CI 构建链路

关键节点
- C1. Workflow 触发成功。
- C2. NDK 安装步骤通过。
- C3. Engine/Gradle 构建通过。
- C4. APK artifact 上传成功。

执行命令
- `curl -s 'https://api.github.com/repos/caucy2026/kemi-rd/actions/workflows/koffice-build.yml/runs?per_page=1' ...`
- `curl -s 'https://api.github.com/repos/caucy2026/kemi-rd/actions/runs/30339809070/jobs' ...`

通过标准
- 最新 run conclusion=success。
- 无失败步骤。

本轮验收结果
- C1: 通过（run #10 已触发并完成）
- C2: 失败（fail_step 6: Install Android NDK）
- C3: 未执行到（被 C2 阻断）
- C4: 未执行到（被 C2 阻断）

结论
- 环节 C 未通过。
- 当前总阻塞点：NDK 安装步骤。

### 环节 D：产物交付

关键节点
- D1. CI artifact 可下载。
- D2. 本地可安装包命名与版本信息可追溯。

执行命令
- 在 C 环节通过后检查 artifacts 与 APK 元信息。

通过标准
- 存在可下载 APK。
- 版本与提交可映射。

本轮验收结果
- D1: 阻塞（C 环节未通过）
- D2: 阻塞（无产物）

结论
- 环节 D 阻塞。

### 环节 E：设备功能验收

关键节点
- E1. DOCX/XLSX/PPTX 可打开并编辑。
- E2. PDF 可打开且只读（不可编辑、不可评论编辑）。
- E3. 双屏任意屏启动文档正确。
- E4. 跨屏 IME 输入闭环正常（含中文组合态）。

执行命令
- 安装 APK 后进行 adb 启动、操作、日志与截图验证。

通过标准
- 四类格式行为符合边界。
- 双屏/跨屏输入稳定无回归。

本轮验收结果
- E1-E4: 阻塞（D 环节无可安装产物）

结论
- 环节 E 阻塞。

### 环节 F：发布封板

关键节点
- F1. 验收记录完整（每环节状态和证据齐全）。
- F2. 风险项与回退方案明确。
- F3. 对外发布包与说明可交付。

执行命令
- 汇总验收文档、构建日志、变更说明。

通过标准
- 可追溯、可复现、可回退。

本轮验收结果
- F1: 部分通过（本文档已建立）
- F2: 部分通过（已识别 NDK 阻塞）
- F3: 阻塞（无产物）

结论
- 环节 F 未通过。

## 3. 本轮总评

- 已通过：A、B
- 未通过：C、F
- 阻塞：D、E
- 第一优先处理项：修复 CI 第 6 步 Install Android NDK

## 4. 下一轮验收触发条件

- 触发条件 1：CI 最新 run 首次 success。
- 触发条件 2：产生 APK artifact 并可安装。
- 触发条件 3：完成设备端 E1-E4 验收记录。

## 5. 验收记录模板（后续每轮追加）

- 轮次：YYYY-MM-DD / run #编号
- A 源码基线：通过/失败（证据）
- B 补丁一致性：通过/失败（证据）
- C CI 构建：通过/失败（失败步骤）
- D 产物交付：通过/失败（artifact）
- E 设备验收：通过/失败（截图+日志）
- F 发布封板：通过/失败（发布包+说明）
- 本轮结论：继续推进项 / 阻塞项
