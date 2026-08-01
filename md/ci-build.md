# 本地开发、GitHub 备份与云端构建协调

> 本文前半部分是可复用于其他项目的通用方法，后半部分是 KEMI-远程桌面的具体实现。
> 核心目标是同时满足：开发进度及时备份、正在执行的候选构建不被误取消、云端资源不被
> 重复浪费、最终制品可以追溯到唯一源码提交。

---

# 第一部分：通用方法

## 1. 先区分四种对象

很多冲突来自把“备份”“构建”“发布”当成同一件事。任何项目都应明确区分：

| 对象 | 含义 | 推荐载体 |
|---|---|---|
| 开发备份 | 保存尚未达到交付条件的源码进度 | `wip/*` 远端分支 |
| 候选构建 | 准备让 CI 生成客户端的不可变源码快照 | 交付分支上的一个 commit |
| 构建制品 | CI 生成但尚未完成目标系统验收的文件 | Actions artifact |
| 候选发布 | 已汇总版本、commit、哈希的待验收安装包 | prerelease 或制品仓库 |

结论：

- “需要备份”不等于“必须推送交付分支”。
- “云端构建成功”不等于“客户端已经交付”。
- 源码 Git、Actions artifact 和正式发布包解决的是三个不同问题。

## 2. 推荐分支职责

每个项目至少约定以下角色，实际分支名称可以不同：

| 分支类型 | 示例 | 用途 | 是否自动构建交付客户端 |
|---|---|---|---|
| 交付分支 | `main`、`master`、`release-candidate` | 只接收通过本地预检、准备生成候选包的提交 | 是 |
| 工作备份分支 | `wip/日期-功能` | 备份未完成代码，允许继续修改 | 否 |
| 文档分支或文档路径 | `docs/*`、`docs/**` | 说明、记录、交接 | 通常否 |
| 正式发布引用 | `v1.2.3` tag | 锁定正式交付源码 | 只运行发布流水线 |

交付分支必须保持单一语义：每次非纯文档提交都代表“允许启动下一轮候选构建”。

## 3. 构建未完成时如何备份新代码

### 3.1 尚未提交的新改动

从当前候选提交创建工作分支，在工作分支提交并推送：

```bash
git switch -c wip/20260730-feature-name
git status --short
git add <明确文件列表>
git commit -m "wip: back up feature progress"
git push -u <backup-remote> HEAD
```

结果：

- 新代码已经保存在 GitHub；
- 交付分支没有移动；
- 旧候选 run 继续运行；
- 后续开发可以继续提交到同一 `wip/*` 分支。

### 3.2 已经误提交在本地交付分支，但还没有推送

不要把它推到远端交付分支。直接把当前 HEAD 推成远端 WIP 分支：

```bash
git push <backup-remote> HEAD:refs/heads/wip/20260730-feature-name
```

这样无需改写提交，也不会移动远端交付分支。当前 run 完成后再整理本地分支关系。

### 3.3 当前 run 完成后进入下一轮

先确认上一轮状态和产物已经记录，再把 WIP 整理进交付分支：

```bash
git fetch <backup-remote>
git switch <delivery-branch>
git rebase <backup-remote>/<delivery-branch>
git merge --ff-only wip/20260730-feature-name
```

完成版本、依赖和测试检查后，只推送一次：

```bash
git push <backup-remote> <delivery-branch>
```

如果不能 `--ff-only`，说明两条历史已经分叉。应先检查差异并显式 rebase 或解决冲突，
不能强推、不能用 reset 丢弃一边。

## 4. 推送决策表

| 当前情况 | 正确动作 | 对正在运行的候选构建 |
|---|---|---|
| 只是文档更新，workflow 已确认忽略文档路径 | 提交并推送交付分支 | 不创建新 run，不取消旧 run |
| 功能未完成，但必须异地备份 | 推送 `wip/*` | 不影响 |
| 功能完成，准备生成下一版客户端 | 等旧 run 记录完成后推交付分支 | 启动新 run |
| 旧 run 已确定失败，新提交正是修复 | 推交付分支，并记录主动替换原因 | 允许取消旧 run |
| 只想重试网络或 runner 临时故障 | 对同一 commit 重跑失败 job | 不产生新源码身份 |
| workflow 本身需要修改 | 先在 WIP/PR 验证；确认是否值得替换旧 run | 默认不立即推交付分支 |
| 版本号、锁文件或生成代码变化 | 作为新的完整候选提交 | 必须重新构建相关平台 |

“纯文档不影响构建”成立的前提是触发器确实配置了 `paths-ignore`，并且此次提交没有混入
源码、锁文件、构建脚本、资源或 workflow。不能只凭提交信息写了 `docs:` 就认为安全。

## 5. CI 并发和触发器设计

通用候选构建可采用：

```yaml
on:
  push:
    branches:
      - <delivery-branch>
    paths-ignore:
      - "docs/**"
      - "*.md"
  workflow_dispatch:

concurrency:
  group: client-candidate-${{ github.ref }}
  cancel-in-progress: true
```

设计含义：

1. 只有交付分支启动常规客户端构建；
2. WIP 分支只负责 GitHub 备份；
3. 纯文档提交不会创建 run，因此不会触发并发取消；
4. 交付分支出现新的代码候选时，旧 run 才被有意识地替换；
5. 手动构建仍绑定所选择的 ref，必须记录 ref 和 commit。

如果项目要求每个候选都必须完整保留，应把 `cancel-in-progress` 改为 `false`；代价是旧候选
继续占用 runner。不要在没有明确产品策略时频繁切换该值。

WIP分支是否安全不能只看一个workflow。接入其他项目时必须审计`.github/workflows/`下所有文件：

- `on: push`没有`branches`过滤时，WIP推送也会触发该workflow；
- 不同workflow如果复用同一个concurrency group，可能互相取消；
- 文档路径可能被一个workflow忽略，却仍触发另一个workflow；
- WIP可以触发快速lint，但不应触发昂贵的全平台打包；
- 并发组应包含稳定的产品/工作流前缀和`${{ github.ref }}`，避免跨分支误取消。

因此，“推WIP不会影响候选构建”必须由真实触发器和并发配置证明，不能只靠分支命名推断。

## 6. 一个候选版本的唯一身份

每次构建至少记录：

```text
repository
delivery branch
full commit SHA
product version/build number
workflow name
run ID / run attempt
toolchain and lockfile state
target platform/architecture
artifact filename/size/SHA-256/signature
target-system launch and function result
```

界面版本号相同但 commit 不同，仍是两个不同候选。文件名相同但 SHA-256 不同，也不能互换。
构建报告禁止只写“最新版”“刚推的版本”或“应该成功”。

## 7. 本地和云端如何并行

通用分工：

| 责任线 | 工作 | 完成标准 |
|---|---|---|
| 本地预检 | 格式、静态检查、单元测试、版本和锁文件检查 | 候选提交可推送 |
| 本地构建 | 本机能可靠构建的平台 | 包、签名、目标设备验收 |
| 云端构建 | 本机缺少工具链或目标系统的平台 | 对应 commit 的平台制品 |
| 持续监控 | 查询 run/job/失败步骤，不盲等 | 状态和首个有效错误 |
| 制品集成 | 下载、哈希、签名、格式、版本、功能检查 | 可进入最终分发包 |

推荐顺序：

```text
本地 preflight
  → 形成唯一候选 commit
  → 推送交付分支并记录 run ID
  → 云端构建与本地平台构建并行
  → 任一平台失败立即分析首个有效错误
  → 所有目标平台成功后汇总 manifest/hash
  → 在真实目标系统验收
  → 才能标记交付完成
```

## 8. 失败、重试和替换规则

| 类型 | 处理 |
|---|---|
| 下载超时、服务 5xx、runner 临时故障 | 同一 commit 最多重跑失败 job 一次 |
| cache 恢复/保存失败，但编译仍继续 | 记录性能影响，不立即当成源码失败 |
| 编译器明确报错 | 修源码或构建配置，形成新候选；禁止盲目重跑 |
| 公共生成步骤失败 | 先修公共步骤，不浪费下游平台 runner |
| 单一平台失败 | 其他平台继续；只处理失败平台 |
| 汇总/发布失败 | 保留已成功的平台 artifacts，只修汇总，不重编客户端 |

只有两种情况可以主动替换未完成 run：

1. 已确认旧 run 不可能产生有效制品；
2. 用户明确决定放弃旧候选，转向新候选。

主动替换时必须记录旧 run ID、停止原因和新 commit，不能让旧 run 无声消失。

## 9. 完成状态

建议统一使用以下状态：

| 状态 | 含义 |
|---|---|
| `Backed Up` | 源码已存在远端分支，但尚未作为候选构建 |
| `Candidate Submitted` | 候选 commit 已推交付分支并创建 run |
| `Cloud Ready` | 所需云端平台制品、manifest 和哈希已生成 |
| `Imported` | 制品已核验并导入最终分发工程 |
| `Delivery Done` | 最终包已在真实目标系统安装、启动和功能验收 |

这五个状态不能互相替代。尤其是 `Backed Up` 不能写成“已发布”，`Cloud Ready` 不能写成
“用户端已经更新”。

## 10. 容易遗漏的特殊情况

### 10.1 默认分支与实际开发分支不一致

GitHub 网页和普通 `git clone` 默认使用仓库的 default branch。如果实际开发分支不同，会出现：

- 网页看不到刚备份的文档；
- 新同事 clone 到旧源码；
- README、workflow 和源码版本互相错位。

优先把 GitHub default branch 改为真实交付分支。暂时不能修改时，所有文档和克隆命令必须
显式写 `--branch <delivery-branch>`。

### 10.2 子模块和 Git LFS

源码备份成功不代表子模块和 LFS 对象完整。候选构建前必须核验：

```bash
git submodule status --recursive
git lfs status
```

使用子模块的仓库应 clone：

```bash
git clone --recursive --branch <delivery-branch> <repository>
```

### 10.3 大型安装包和本地 BIN

普通 Git 源码仓不适合反复提交 APK、EXE、AppImage、App bundle 和完整依赖目录。应使用：

- Actions artifact：短期构建中间件和候选包；
- prerelease/release：可追溯交付包；
- 专用制品仓或对象存储：长期内部包；
- Git LFS：只有团队已经明确采用时使用。

“本地 BIN 中存在”不等于“已经备份到 GitHub”。必须另外记录它由哪个 commit/run 生成、
保存在哪里、保留多久以及 SHA-256。

### 10.4 workflow 或生成器变更

workflow、版本脚本、依赖锁文件、bridge/codegen 输出都会改变制品，不能当成纯文档提交。
如果当前 run 仍有价值，应先推 WIP 分支；如果变更就是修复当前失败，才作为新候选推交付分支。

### 10.5 紧急修复

紧急并不意味着跳过身份记录。最少仍要：

- 保存旧候选 commit/run；
- 说明为什么允许取消；
- 新修复独立提交；
- 重新生成所有受影响平台；
- 禁止把旧平台包和新平台包拼成同一版本发布。

### 10.6 多项目或 monorepo

路径过滤必须与实际项目边界一致。一个子项目的文档忽略规则不能误伤另一个子项目的构建脚本。
建议并发组包含“产品名 + ref”，制品名包含“产品名 + 平台 + 架构 + commit”。

### 10.7 密钥、隐私数据和本机配置

WIP也是远端Git历史，不能因为叫“临时备份”就提交`.env`、签名私钥、API token、账号配置、
用户数据或生产日志。推送前必须检查暂存差异；敏感数据应进入密钥管理或经批准的加密备份，
不能进入普通源码分支。

### 10.8 受保护分支和PR合并

如果交付分支只能通过PR进入，真正的候选身份是“合并后交付分支上的commit”，不是PR分支
最后一个commit。CI可先验证PR，但用于发布的安装包必须绑定合并后的完整SHA。merge commit、
squash和rebase merge会产生不同SHA，制品不得混用。

### 10.9 制品过期和取消残留

Actions artifact通常有保留期限。重要候选必须在过期前转入prerelease或制品仓，并保存哈希。
已取消run留下的部分文件、公共bridge或辅助DLL只能用于诊断，不能与其他run的文件拼装发布。

---

# 第二部分：KEMI-远程桌面特例

## 11. 仓库与分支

| 项目 | 当前值 |
|---|---|
| 本地客户端仓 | `/Users/newlink/kemi/RustDesk/client` |
| KEMI 远端 | `git@github.com:caucy2026/rust-desk.git`，remote 名 `backup` |
| 当前交付分支 | `master` |
| 上游 RustDesk | remote `origin`，禁止推送 KEMI 代码 |
| 常规云端入口 | `.github/workflows/kemi-distribution.yml` |
| workflow 名 | `KEMI Focused Client Artifacts` |

当前 GitHub default branch 仍是旧的 `main`，而真实客户端开发与 `kemi-docs` 在 `master`。
在仓库设置改为 `master` 之前，新同事必须使用：

```bash
git clone --recursive --branch master https://github.com/caucy2026/rust-desk.git
```

不能使用不带 `--branch master` 的普通 clone，否则会进入旧 monorepo `main`。

## 12. KEMI 平台分工

| 目标 | 默认位置 | 约束 |
|---|---|---|
| PAD / Android debug | 本地 Mac | Flutter/Kotlin 包装与 ADB 验收；Rust 核心变化必须重编 Android JNI |
| PAD / Android release | 本地优先 | Apple Silicon release AOT、Rosetta、NDK必须完整 |
| macOS arm64 | 本地 Mac | 固定证书/私钥可用并通过深度签名后才能交付 |
| Windows x64 | Windows 本机优先，否则 GitHub | 需要 MSVC、Windows SDK、固定 Flutter/Rust/vcpkg |
| Linux x86_64 AppImage | Linux 本机优先，否则 GitHub | Mac 的服务端交叉编译不能代替 Flutter Linux GUI |

本地能可靠完成的目标不等待 GitHub。本地没有对应操作系统/工具链时，才交给云端。

## 13. KEMI focused workflow

触发规则：

```text
master 的非纯文档 push
或手动 workflow_dispatch
```

自动忽略：

```text
docs/**
README.md
kemi-docs/**
```

已审计当前`.github/workflows/`触发器：focused和基础`CI`的push都只监听`master`，完整Flutter
CI只在PR或手动触发，nightly/tag也不监听普通WIP push。因此当前KEMI推送`wip/*`不会启动
或取消正在运行的`master`候选构建。以后新增workflow时必须重新审计，不能永久假设安全。

只运行：

```text
default bridge（Flutter 3.22.3）
        ├── Windows x64 TopMostWindow ─┐
        │                              ├── Windows x64 EXE ─┐
        └── Linux x86_64 → DEB → AppImage ─────────────────┤
                                                           ↓
                                     manifest + SHA256SUMS + 单一候选 prerelease
```

明确跳过 Windows ARM64/x86、Linux ARM、macOS、Android、iOS、Flatpak 和其他非当前交付目标。
同一 `master` 出现新的非文档候选时，`cancel-in-progress: true` 会取消旧 run。

成功时保留14天的Actions artifact命名为：

```text
kemi-windows-x86_64-<完整commit>
kemi-linux-x86_64-<完整commit>
kemi-client-manifest-<完整commit>
```

Windows本地工具链、bridge生成和便携EXE打包细节单独维护在
`kemi-docs/windows-vscode-build-prompt.md`，不在协调文档重复维护版本命令。

因此 KEMI 的构建期间备份规则是：

```bash
# 未完成代码只备份，不触发新客户端构建
git switch -c wip/20260730-topic
git add <明确文件列表>
git commit -m "wip: back up topic"
git push -u backup HEAD

# 纯 kemi-docs 修改可以推 master，不会创建 focused run
git push backup master
```

绝不能把功能代码和文档混在同一个“docs”提交里绕过构建。

## 14. KEMI 推送前检查

```bash
cd /Users/newlink/kemi/RustDesk/client

git diff --check
git status --short
git rev-parse HEAD
git submodule status --recursive

rg -n '^version:' flutter/pubspec.yaml
rg -n '^version = ' Cargo.toml
rg -n 'VERSION:' .github/workflows/flutter-build.yml

ruby -e "require 'yaml'; Dir['.github/workflows/*.yml'].each { |f| YAML.load(File.read(f)); puts f }"
```

按改动追加：

- Dart/Kotlin：针对性 `flutter analyze` 和最小构建；
- Rust 公共逻辑：`cargo check/build --locked`；
- Android Rust 核心：重编对应 ABI 的 `librustdesk.so`；
- macOS：检查固定 identity 并执行固定签名、深度验证；
- workflow：检查 focused profile 只展开目标矩阵。

## 15. KEMI 云端查询

当前机器未安装 `gh`，可通过 GitHub API 查询。只认与候选完整 SHA 一致的 run：

```bash
repo='caucy2026/rust-desk'
commit="$(git rev-parse HEAD)"

curl --http1.1 --connect-timeout 10 --max-time 30 -fsSL \
  "https://api.github.com/repos/$repo/actions/runs?branch=master&per_page=20" \
  | jq -r --arg commit "$commit" '
      .workflow_runs[]
      | select(.head_sha == $commit)
      | [.id,.name,.status,(.conclusion // "-"),.created_at,.html_url]
      | @tsv'
```

查询 job：

```bash
curl --http1.1 --connect-timeout 10 --max-time 30 -fsSL \
  "https://api.github.com/repos/$repo/actions/runs/<run-id>/jobs?per_page=100" \
  | jq -r '.jobs[] | [.id,.name,.status,(.conclusion // "-"),.html_url] | @tsv'
```

查询本次run真实上传的artifacts：

```bash
curl --http1.1 --connect-timeout 10 --max-time 30 -fsSL \
  "https://api.github.com/repos/$repo/actions/runs/<run-id>/artifacts?per_page=100" \
  | jq -r '.artifacts[] | [.id,.name,.size_in_bytes,.expired,.archive_download_url] | @tsv'
```

查询失败步骤和注释：

```bash
curl --http1.1 --connect-timeout 10 --max-time 30 -fsSL \
  "https://api.github.com/repos/$repo/actions/runs/<run-id>/jobs?per_page=100" \
  | jq -r '
      .jobs[]
      | select(.conclusion == "failure")
      | "JOB\t\(.id)\t\(.name)",
        (.steps[] | select(.conclusion == "failure") | "STEP\t\(.number)\t\(.name)")'

curl --http1.1 --connect-timeout 10 --max-time 30 -fsSL \
  "https://api.github.com/repos/$repo/check-runs/<job-id>/annotations?per_page=100" \
  | jq -r '.[] | [.annotation_level,.path,.start_line,.message] | @tsv'
```

公开 API 下载完整日志可能返回 `403`。此时打开 job URL，或在授权环境执行：

```bash
gh run view <run-id> --log-failed
```

不能把“exit code 1”当作根因，必须取得首个有效编译错误。

监控频率以项目成本和run时长配置。KEMI候选run创建后每2–3分钟查询一次；`queued`超过
10分钟报告runner等待，任一平台失败立即分析，不等待另一个平台结束。新候选推送后还要
核对旧run是否按预期取消，并记录替换原因。

## 16. KEMI 三阶段交付

### Cloud Ready

- Windows x64 EXE job 成功；
- Linux x86_64 AppImage job 成功；
- manifest 汇总 job成功；
- 候选 prerelease 包含 Windows、Linux、`manifest.json`、`SHA256SUMS.txt`；
- commit、版本、文件哈希一致。

### Imported

核验后导入：

```text
flutter/android/app/src/main/assets/client-dist/
├── KEMI-remote-desktop-windows-x64.exe
└── KEMI-remote-desktop-linux-x86_64.AppImage
```

不得把 bridge、TopMostWindow 或 DEB 中间 artifact 冒充最终客户端。

### Delivery Done

1. Windows/Linux 与本地 Mac ZIP一起编入最终 PAD APK；
2. 卸载 PAD 旧包后安装最终包；
3. 从 PAD“客户端”页下载四个平台文件；
4. 下载文件与导入文件 SHA-256一致；
5. Windows、Linux、macOS分别启动并验收远控与文件传输；
6. 记录版本、commit、run ID、哈希、签名和目标系统结果。

## 17. 当前 KEMI 云端状态

候选源码提交：

```text
98d3a9735b46cfebe5954ecac55b2f6e222fee48
```

focused run：

```text
30541481850
```

结果：

- default bridge：成功；
- TopMostWindow x64：成功；
- Windows x64：`Build rustdesk` 失败；
- Linux x86_64：`Build rustdesk` 失败；
- 只有 bridge 和 TopMostWindow 中间 artifacts；
- 没有 Windows/Linux最终客户端，没有进入 manifest/prerelease 汇总。

后续纯 `kemi-docs/**` 提交没有触发新的 focused run，也没有影响该 run。下一轮功能候选进入
`master` 前，必须先取得两个失败 job 的首个有效错误并完成对应修复。

## 18. KEMI 汇报模板

```text
源码备份：
- 分支：
- commit：
- Backed Up：

本地：
- PAD：构建/安装/版本/签名状态
- Mac：构建/固定签名/安装状态

云端：
- 候选 commit：
- focused run ID / URL：
- default bridge：
- Windows x64：
- Linux x64：
- manifest / candidate prerelease：
- 当前失败 step 与首个有效错误：

交付：
- Windows SHA-256 / 启动结果：
- Linux SHA-256 / 启动结果：
- Mac SHA-256 / 签名结果：
- 最终 PAD 版本 / APK SHA-256：
- 四端从 PAD 下载验收：
```

任何未知项明确写“未完成/待验证”，不能用“正在构建”“应该可以”代替真实状态。
## 19. KOffice Android 云端构建加速

本节只适用于当前 `.github/workflows/koffice-build.yml`：在 Ubuntu 上交叉编译
LibreOffice/Collabora Android arm64-v8a 引擎，再构建 Debug APK。此前关于 RustDesk、
Flutter、Cargo、vcpkg、Windows/Linux 客户端的缓存方案不适用于此仓库，不能直接复制。

### 19.1 先满足可构建性

当前工作流访问 `koffice/upstream`、`koffice/patches` 和 `koffice/scripts`。候选提交必须
包含这些目录，或以已初始化的 Git submodule/明确 checkout 步骤提供它们；仅设置
`actions/checkout` 的 `submodules: true` 不会凭空取得未登记在 `.gitmodules` 的源码。

触发器必须覆盖子目录，而不是只匹配目录名：

```yaml
on:
  push:
    paths:
      - 'koffice/patches/**'
      - 'koffice/scripts/**'
      - 'koffice/upstream/**'
      - '.github/workflows/koffice-build.yml'
```

在这个前提未满足前，缓存和 runner 调优没有实际收益。

### 19.2 GitHub Pro 的性能边界

公开仓库的标准 `ubuntu-24.04` 是 4 vCPU、16 GB RAM、14 GB SSD；因此当前
`make -j$(nproc)` 最多使用四条编译任务。私有仓库使用同一标签时则是 2 vCPU、8 GB RAM。
不要把标准公开 runner 误认为 8 核；实际 run 应记录 `nproc` 输出。GitHub Pro 个人账户不能
开通 GitHub-hosted larger runners；该功能仅向 GitHub Team 或 Enterprise Cloud 的组织提供。

目标是缩短单次完整 LibreOffice 构建时，优先级如下：

1. 先完成本节的零下载、非调试、ccache 和 Gradle 缓存优化；
2. 仍不满足时，迁移仓库到 GitHub Team 组织并配置 larger runner；
3. 或使用预装 NDK、SDK 和依赖的自托管云 runner（建议至少 8 vCPU / 32 GB RAM）。

larger runner 按运行分钟额外计费，且不能使用私有仓库的包含分钟数。不要为了轻量检查
使用 `ubuntu-slim`，它只有 1 vCPU，且不适合重型构建。

### 19.3 消除每次下载的 NDK 与 SDK

截至本文更新时，GitHub `ubuntu-24.04` 镜像已预装 NDK `28.2.13676358`、
`platforms;android-36` 和 `build-tools;36.0.0`。现有 workflow 先删除所有 NDK 的
`toolchains`，再下载并解压同一 NDK，这是最直接的性能损失。

常规构建不得删除 `/usr/local/lib/android/sdk/ndk/*/toolchains`，也不应对已预装的
Android 36 运行 `sdkmanager --install`。改为显式使用并验证预装路径：

```yaml
env:
  ANDROID_SDK_ROOT: /usr/local/lib/android/sdk
  ANDROID_NDK_HOME: /usr/local/lib/android/sdk/ndk/28.2.13676358

# 在 build 前验证；镜像变更时立即失败并记录，不静默下载不同工具链
- name: Verify Android toolchain
  run: |
    set -euo pipefail
    test -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
    test -d "$ANDROID_SDK_ROOT/platforms/android-36"
    test -d "$ANDROID_SDK_ROOT/build-tools/36.0.0"
```

若固定版本将来从镜像移除，先更新版本并验证构建；只有确实必须使用非预装 NDK 时才单独
缓存用户可写目录中的 NDK。不要缓存或覆盖 `/usr/local/lib` 下的系统 SDK。

### 19.4 编译模式：候选构建不能默认调试引擎

`--enable-dbgutil` 和 `--enable-debug` 会明显拉长 LibreOffice 编译时间。常规候选 APK
应使用发布型引擎配置；只有诊断问题时，才由 `workflow_dispatch` 输入显式开启这两个选项。
Debug APK 的 Gradle variant 与 LibreOffice 的 `dbgutil` 引擎不是同一概念，不能因为需要
`assembleDebug` 就默认启用后者。

每次修改该策略都必须对产物执行启动和核心功能验收；若调试选项被关闭后出现问题，建立
独立的诊断 run，不要把慢速配置放回所有候选构建。

### 19.5 ccache：必须命中，且缓存必须可更新

ccache 的收益取决于真实编译器调用经过 ccache，而不是仅安装了 `ccache` 或执行
`ccache -s`。构建前应设置适合当前构建系统的 compiler launcher/wrapper，并在构建结束后
记录 `cache hit (direct)`、`cache hit (preprocessed)` 和 `cache miss`。命中数为零时，先修复
wrapper，再讨论缓存容量。

GitHub Actions cache 不可覆盖。现有固定 key
`Linux-koffice-arm64-ndk28-ccache-v1` 只会保存第一次成功构建的内容，之后即使产生新的
对象也无法更新同一个缓存。应改用专门维护轮换 key 的 ccache action，或使用“本次 run 的
唯一保存 key + 稳定 restore 前缀”的策略，并将 ccache 上限控制在 1–2 GB。

不要把 5 GB ccache 与其他大型缓存同时放入默认 10 GB 仓库配额；缓存超过配额会驱逐旧项，
导致反复冷启动。只允许默认分支写缓存，PR/工作分支仅恢复。

### 19.6 Gradle、checkout 与系统依赖

1. 使用 `gradle/actions/setup-gradle` 管理 Gradle User Home，并以 `cache-read-only` 让
   非默认分支只读。构建命令先验证兼容性后启用 `--build-cache`；configuration cache 需由
   Gradle 报告确认兼容后再启用。
2. 若版本号或构建脚本不依赖完整 Git 历史，把 `actions/checkout` 的 `fetch-depth` 从 `0`
   改为 `1`。使用 submodule 时同时确认其 shallow checkout 与构建脚本兼容。
3. 先做依赖探测，仅对确实缺失的 apt 包执行 `apt-get update/install`。预装镜像已包含
   autoconf、automake、bison、flex、libtool、gcc/g++ 等常用工具；不可假设所有库都存在。
4. 把“释放磁盘”限制为确认无用的目录；不要删除随后要使用的 Android 工具链。若完整构建
   仍接近磁盘上限，先记录 `df -h` 和最大目录，再决定删除目标。

### 19.7 制品与发布链路

build workflow 上传的 artifact 名为 `koffice-native-libs`，而 promote workflow 下载的是
`koffice-debug-apk`。这两个名字必须统一，并且 build 必须实际上传 APK，promote 才能不重编
而发布。该修复不直接缩短编译时间，但能避免“编译成功后因制品不存在而重新构建”的浪费。

### 19.8 验证与量化标准

一次变更只做一项，并记录以下字段：

| 项目 | 必须记录 |
|---|---|
| runner | 标签、vCPU、RAM、镜像版本 |
| setup | checkout、apt、NDK/SDK 各自耗时 |
| 编译 | engine、app layer、Gradle 各自耗时 |
| ccache | direct/preprocessed hit、miss、缓存大小 |
| Gradle | 依赖缓存、build cache、configuration cache 命中情况 |
| 产物 | APK SHA-256、大小、安装与启动验收 |

只有在相同 commit 或重复的无源码变更 run 中对比，才能认定优化有效。缓存冷启动、工具链
变更和源码大改不能与热缓存结果混为一谈。

### 19.9 实施顺序

1. 补齐源码取得方式、路径触发器和 artifact 名称；
2. 移除 NDK/SDK 的删除与重复下载；
3. 将 dbgutil/debug 改为仅手动诊断；
4. 验证 ccache wrapper，替换不可更新的固定 cache key；
5. 接入 Gradle 缓存并测量；
6. 仅在上述优化后的完整构建仍不可接受时，采用 Team larger runner 或自托管云 runner。

参考：GitHub-hosted runner 规格与 larger runner 限制：
https://docs.github.com/en/actions/reference/runners/github-hosted-runners 、
https://docs.github.com/en/actions/concepts/runners/larger-runners 。Ubuntu 24.04 预装软件：
https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md 。缓存行为和
配额：https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching 。
