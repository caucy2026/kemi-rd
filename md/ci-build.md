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
## 19. 云端构建加速

KEMI 技术栈涉及 Flutter (Dart)、Rust (Cargo)、Android (Gradle/NDK) 和 vcpkg C++ 依赖，
云端 Linux runner 只有 2 核 7GB RAM。以下缓存策略按效果从大到小排列，每个都有可复制的
YAML 片段。

### 19.1 缓存分层模型

| 层级 | 缓存内容 | key 策略 | 典型命中率 | 节省时间 |
| :--- | :--- | :--- | :--- | :--- |
| L1 | Rust Cargo registry + target | `hashFiles(Cargo.lock)` | ~95% | 20–40 min |
| L2 | Gradle wrapper + 依赖 | `hashFiles(gradle-wrapper.properties, **/*.gradle*)` | ~95% | 3–8 min |
| L3 | Flutter pub cache | `hashFiles(pubspec.yaml, pubspec.lock)` | ~95% | 2–5 min |
| L4 | vcpkg 预编译包 | 固定版本号 | ~100%（7 天过期） | 5–15 min |
| L5 | NDK 工具链 | 固定版本号 | ~100% | 2–3 min |
| L6 | ccache C/C++ 编译对象 | `hashFiles(**/*.rs, **/*.cpp, **/Cargo.toml)` | ~80% | 10–30 min |

GitHub Actions 缓存总配额为 10 GB / 仓库。超过后旧缓存会被自动驱逐，因此各层必须
控制大小：L1 Cargo target 只缓存依赖 crate 产物，不缓存自身项目的频繁增量编译输出。

### 19.2 Rust / Cargo

最重的缓存项。每个平台（Windows/Linux）至少下载和编译 200+ crate。

**swatinem/rust-cache（推荐，零配置首选）：**

```yaml
- uses: swatinem/rust-cache@v2
  with:
    workspaces: |
      src-tauri -> src-tauri/target
    cache-directories: |
      ~/.cargo/registry
    shared-key: ${{ runner.os }}-${{ hashFiles('**/Cargo.lock') }}
```

它自动识别 `Cargo.toml`、`Cargo.lock` 和 `rust-toolchain.toml` 变化，精准决定是否
重建缓存。比手动 `actions/cache` 少很多边缘情况。

**手动方案（需要更细粒度控制时）：**

```yaml
- uses: actions/cache@v4
  with:
    path: |
      ~/.cargo/registry/index
      ~/.cargo/registry/cache
      ~/.cargo/git
    key: cargo-registry-${{ runner.os }}-${{ hashFiles('**/Cargo.lock') }}
    restore-keys: cargo-registry-${{ runner.os }}-

- uses: actions/cache@v4
  with:
    path: src-tauri/target
    key: cargo-target-${{ runner.os }}-${{ hashFiles('**/Cargo.lock', '**/*.rs') }}
    restore-keys: cargo-target-${{ runner.os }}-
```

注意：`registry` 和 `target` 必须分两个 key。改一行 `.rs` 不应该让已下载的 crate 源码
重新下载。

### 19.3 Flutter / Dart

```yaml
- uses: subosito/flutter-action@v2
  with:
    flutter-version: '3.22.3'
    cache: true
    cache-key: flutter-${{ runner.os }}-${{ hashFiles('**/pubspec.lock') }}
```

`subosito/flutter-action` 的 `cache: true` 会自动缓存 `$FLUTTER_ROOT` 和
`~/.pub-cache`。如果没有用这个 action，手动缓存：

```yaml
- uses: actions/cache@v4
  with:
    path: |
      /opt/hostedtoolcache/flutter
      ~/.pub-cache
    key: flutter-${{ runner.os }}-${{ hashFiles('**/pubspec.lock') }}
```

### 19.4 Android / Gradle

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    cache-read-only: ${{ github.ref != 'refs/heads/master' }}
```

`setup-gradle@v4` 同时缓存 `~/.gradle/caches` 和 `~/.gradle/wrapper`，远比
`actions/setup-java` 自带的 `cache: gradle` 更完整。

如果项目没有迁移到这个 action，用 `actions/cache` 手动替代：

```yaml
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: gradle-${{ runner.os }}-
```

### 19.5 NDK 工具链

NDK 完整包约 1.5 GB，每次下载非常慢。两种方案：

**方案 A — 用 runner 预装的 NDK（零下载）：**

```bash
ls "$ANDROID_HOME/ndk/"
```

如果命中了项目使用的版本，直接在 `local.properties` 或环境变量引用即可。

**方案 B — 缓存自定义 NDK 路径：**

```yaml
- uses: actions/cache@v4
  with:
    path: ${{ env.ANDROID_NDK_HOME }}
    key: ndk-${{ runner.os }}-r27c
```

version 固定、不随源码变化，命中率接近 100%。

### 19.6 ccache / sccache（C/C++/Rust 编译对象缓存）

RustDesk 的 `lib.rs` 或 C++ bridge 层在未改动时不需重编译。ccache 把编译输出按
预处理后的源文件哈希保存，未变化的翻译单元直接跳过：

```yaml
- name: Setup ccache
  uses: hendrikmuhs/ccache-action@v1
  with:
    key: ccache-${{ runner.os }}-${{ github.ref_name }}
    restore-keys: ccache-${{ runner.os }}-
    max-size: 2G
```

然后在构建前确保编译器经 ccache 包装：

```bash
export CC="ccache gcc"
export CXX="ccache g++"
```

`sccache` 对 Rust 更友好，支持 Cargo 增量编译 + 远程存储：

```yaml
- name: Setup sccache
  uses: mozilla-actions/sccache-action@v0
```

在 `Cargo.toml` 同级创建 `.cargo/config.toml`：

```toml
[build]
rustc-wrapper = "sccache"
```

### 19.7 vcpkg C++ 依赖

RustDesk 的 Windows 构建可能通过 vcpkg 拉取 C++ 库。缓存 vcpkg 的已编译包：

```yaml
- uses: actions/cache@v4
  with:
    path: |
      ${{ github.workspace }}/vcpkg/installed
      ${{ github.workspace }}/vcpkg/downloads
    key: vcpkg-${{ runner.os }}-${{ hashFiles('vcpkg.json') }}
```

### 19.8 构建矩阵并行化

把不同平台和 ABI 拆成独立 job 并行执行。KEMI 当前 focused workflow 已拆分
Windows 和 Linux 两个 job，可以进一步拆：

```yaml
strategy:
  matrix:
    platform: [windows-x64, linux-x86_64]
    include:
      - platform: windows-x64
        runs-on: windows-2022
      - platform: linux-x86_64
        runs-on: ubuntu-24.04
```

每增加一个并行 job 就增加一份 runner 并发配额消耗。应先确保单 job 缓存命中后仍
超过 20 分钟，再拆分；否则拆分带来的并行收益会被冷缓存抵消。

### 19.9 缓存只读策略

只有 `master` 分支保存（save）缓存。WIP 分支和 PR 只恢复（restore）但不写回，
防止几十个 WIP 分支撑爆 10 GB 配额：

| 动作 | `setup-gradle@v4` | `actions/cache@v4` |
| :--- | :--- | :--- |
| 只恢复 | `cache-read-only: true` | 用 `actions/cache/restore@v4` 替代 `actions/cache@v4` |
| 保存 | 仅在 `master` 的 `cache-read-only: false` | 仅在 `master` 用 `actions/cache/save@v4` 显式保存 |

```yaml
- name: Restore caches (read-only on non-master)
  if: github.ref != 'refs/heads/master'
  uses: actions/cache/restore@v4
  with:
    path: ~/.cargo/registry
    key: cargo-registry-${{ runner.os }}-${{ hashFiles('**/Cargo.lock') }}

- name: Restore and save cache (master only)
  if: github.ref == 'refs/heads/master'
  uses: actions/cache@v4
  with:
    path: ~/.cargo/registry
    key: cargo-registry-${{ runner.os }}-${{ hashFiles('**/Cargo.lock') }}
```

### 19.10 其他零成本提速

- **`actions/checkout` 浅克隆**：`fetch-depth: 1`，关闭 `show-progress` 和
  `lfs: false`（如果不依赖 Git LFS）。
- **锁定工具版本，不要每次 `latest`**：`flutter-version: '3.22.3'` 而非
  `'stable'`，避免 runner 每周下载新版 SDK。
- **`paths-ignore` 加全**：当前 focused workflow 已忽略 `docs/**` 和
  `kemi-docs/**`，还可以加 `*.md`、`.github/ISSUE_TEMPLATE/**` 等非构建路径。
- **避免 `npm install` 在关键路径**：如果 bridge 构建不需要每次都跑 `npm ci`，
  把 web 前端静态资源放在预构建 artifact 中复用。
- **Job 超时设上限**：`timeout-minutes: 45` 防止 hung 进程吃掉全部配额。

### 19.11 实施优先级

按投入产出比排序，建议分三批：

**第一批（零风险，直接加）：**

1. `swatinem/rust-cache@v2` — 收益最明显，Rust crate 下载+编译占总时长 60%+
2. `gradle/actions/setup-gradle@v4` — 解决 Gradle 依赖反复下载
3. `subosito/flutter-action@v2` + `cache: true` — Pub 依赖缓存
4. `fetch-depth: 1` — checkout 提速

**第二批（需要验证后加）：**

5. `hendrikmuhs/ccache-action@v1` 或 `sccache` — 跳过未改动的 C/C++/Rust 编译单元
6. NDK 缓存 — 先确认 runner 是否已预装目标版本
7. vcpkg 缓存 — 仅 Windows job 需要

**第三批（缓存稳定后再做）：**

8. 缓存只读策略 — 防止 WIP 分支撑爆配额
9. 构建矩阵拆分 — 仅在单 job 缓存命中后仍超 20 min 时做

每批完成后记录典型 run 的时长和缓存命中率，再做下一批。不要一次性加完所有
缓存步骤后盲目合并——每种缓存都有 key 设计错误或路径遗漏的可能，必须分开验证。