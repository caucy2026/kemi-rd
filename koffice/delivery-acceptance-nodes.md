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

## 3. 本轮总评（第 1 轮）

- 已通过：A、B
- 未通过：C、F
- 阻塞：D、E
- 第一优先处理项：修复 CI 第 6 步 Install Android NDK

## 4. 第 2 轮验收（2026-07-28 / run #11）

基线提交：7e20010
改动：重写 NDK 安装步骤，拆为下载/解压/校验/sdk 四段独立验证

本轮验收结果
- A1-A3: 通过（未变更）
- B1-B3: 通过（未变更）
- C1: 通过（run #11 已触发并完成）
- C2: **通过**（NDK 安装步骤成功，clang 验证通过）
- C3: **失败**（Build LibreOffice engine: configure 报错 `uuid is required for internal Python`）
- C4: 未执行到（被 C3 阻断）
- D1-D2: 阻塞
- E1-E4: 阻塞
- F1-F3: 阻塞

本轮结论
- 进展：C2（NDK）已修复，C3 新阻塞为缺少 `uuid-dev`。
- 已识别修复：安装 `uuid-dev libssl-dev libcurl4-openssl-dev`。

## 5. 第 3 轮验收（2026-07-28 / run #12）

基线提交：3e5ab54
改动：engine 依赖新增 `uuid-dev libssl-dev libcurl4-openssl-dev`

本轮验收结果
- C1: 通过
- C2: 通过（NDK 安装正常）
- C3: **通过**（engine 编译成功，耗时约 170 分钟）
- C3.5: **失败**（app layer configure 报错 `lxml for python3 is needed`）
- C4: 未执行到（被 C3.5 阻断）
- D/E/F: 阻塞

本轮结论
- 进展：C3 engine 编译通过。新阻塞：缺少 `python3-lxml`。

## 6. 第 4 轮验收（2026-07-28 / run #14-#21）

基线提交：488361c → c4b05bd
改动：依赖新增 `python3-lxml`，runner 切 `ubuntu-latest`

本轮验收结果
- C1: 失败（run #14-#18 全部 0 步，runner_id=0，2 秒内结束）
- 根因诊断：GitHub API annotation 明确返回 `The job was not started because your account is locked due to a billing issue.`
- 非 workflow 代码问题

本轮结论
- 阻塞：GitHub 账户账单锁定；处理账单后 run #22 已恢复正常 runner。
- 当前 workflow（含 `python3-lxml`）已就绪。

## 7. 下一轮待办

1. ~~去 GitHub Settings → Actions 启用~~ ✅ 已恢复（账单问题解决）
2. ~~确认后 CI 自动触发~~ ✅ run #22 正在运行
3. 等待 C3.5（app layer）→ C4（APK）→ D（产物下载）→ E（设备验收）

## 8. 第 5 轮验收（2026-07-28 至 2026-07-29 / run #22-#23）

基线提交：待确认
状态：CI 已恢复，两轮均完成

本轮验收结果
- C1: 通过（run #22/#23 正常启动）
- C2: 通过（NDK 安装）
- C3: 通过（engine 编译）
- C3.5: 失败（app configure 报错 `polib for python3 is needed`）
- C4: 未执行到
- D/E/F: 阻塞

本轮结论
- `lxml` 修复有效；新阻塞为 `python3-polib`，已在提交 7fb8997 补齐。

## 9. 第 6 轮验收（2026-07-29 / run #24-#27）

改动
- 新增 `python3-polib`。
- 增加 host dependency 快速门禁，缺 Python 模块时在全量 engine 编译前失败。
- 增加固定 5GB `ccache` 跨 run 复用。
- 增加 workflow concurrency；新提交自动取消旧的同组 run。

本轮验收结果
- run #24: 进行中，包含 `python3-polib` 修复；因启动时尚无 concurrency 分组，继续保留。
- run #25: 已被新 concurrency 策略自动取消。
- run #26: host dependency 快速门禁通过，随后由最终 workflow 更新触发取消。
- run #27: 最终优化 workflow 已排队；后续文档更新不再触发构建。

本轮验收目标
- 快速门禁通过。
- C3.5 app layer 通过。
- C4 APK 打包并上传 artifact。

## 10. 下一轮验收触发条件

- 触发条件 1：CI 最新 run 首次 success。
- 触发条件 2：产生 APK artifact 并可安装。
- 触发条件 3：完成设备端 E1-E4 验收记录。

## 11. 验收记录模板（后续每轮追加）

- 轮次：YYYY-MM-DD / run #编号
- A 源码基线：通过/失败（证据）
- B 补丁一致性：通过/失败（证据）
- C CI 构建：通过/失败（失败步骤）
- D 产物交付：通过/失败（artifact）
- E 设备验收：通过/失败（截图+日志）
- F 发布封板：通过/失败（发布包+说明）
- 本轮结论：继续推进项 / 阻塞项
