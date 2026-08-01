# GitHub 作为免费云资源服务器 — 标准方案文档

> 版本: V1.0 | 日期: 2026-07-31
> 适用场景: 开放数据分发（JSON/图片/配置文件），无需账号密码，不限请求次数
> 案例: Go3DGlobe 全球摄像头数据 300条目，日均调用数千次，零成本

---

## 一、架构总览

```
┌─────────────────────────────────────────────────────┐
│  go3dglobe-data (独立数据仓库)                       │
│  ├── webcams.json          ← 摄像头注册表(300条目)   │
│  ├── version.txt           ← 版本号 + 统计说明       │
│  └── ...其他资源                                    │
└──────────────┬──────────────────────────────────────┘
               │ git push
               ▼
┌─────────────────────────────────────────────────────┐
│  GitHub.com (源)                                    │
│  raw.githubusercontent.com/.../main/webcams.json    │
│  ✅ 实时、无CDN缓存                                 │
│  ❌ 国内部分网络超时(>4s)                           │
└──────────────┬──────────────────────────────────────┘
               │ jsDelivr 自动镜像
               ▼
┌─────────────────────────────────────────────────────┐
│  jsDelivr CDN (分发层)                              │
│  cdn.jsdelivr.net/gh/{user}/{repo}@{hash}/...       │
│  ✅ 全球加速、国内可达(<1s)                          │
│  ⚠️ @main 有3分钟缓存延迟                           │
│  ✅ @commit-hash 永久缓存、即时生效                  │
└──────────────┬──────────────────────────────────────┘
               │ HTTP GET
               ▼
┌─────────────────────────────────────────────────────┐
│  Android App (Go3DGlobe)                            │
│  WebcamLayer.kt                                     │
│  ├── checkRemoteVersion()  ← 版本比对               │
│  ├── httpGet()              ← 双源策略              │
│  ├── parseAndRender()       ← JSON解析+3D渲染       │
│  ├── cache (磁盘)           ← 离线兜底              │
│  └── assets fallback        ← APK内置兜底           │
└─────────────────────────────────────────────────────┘
```

---

## 二、核心设计原则

### 2.1 双源策略 — 永远有 B 计划

```kotlin
// 源1: raw GitHub（实时，无缓存）
private const val RAW_REPO = "https://raw.githubusercontent.com/caucy2026/go3dglobe-data/main"
// 源2: jsDelivr CDN（经commit hash固定，全球加速）
private const val CDN_REPO = "https://cdn.jsdelivr.net/gh/caucy2026/go3dglobe-data@a8ba108"
```

请求顺序：
1. **先试 raw GitHub**（实时数据，无CDN延迟）— 超时阈值 4 秒
2. **超时则换 jsDelivr**（全球 CDN，国内稳定）— 超时阈值 8 秒
3. **JSON 下载也同源优先**，失败则换另一个源
4. **全部失败用磁盘缓存**
5. **缓存也没有用 APK 内置 assets**

### 2.2 Commit Hash 固定 — 破解 CDN 缓存延迟

```
❌ cdn.jsdelivr.net/gh/.../go3dglobe-data@main/version.txt
   → 3分钟CDN缓存，push后不立即生效

✅ cdn.jsdelivr.net/gh/.../go3dglobe-data@a8ba108/version.txt
   → commit hash固定，永久有效，push后更新hash即刷新
```

```kotlin
// 每次推送新数据后，更新这个常量
private const val COMMIT = "a8ba108"  // v7: 300条目
```

**关键流程**：
1. 编辑 `webcams.json` → `git push`
2. 复制最新 commit hash → 更新 `WebcamLayer.kt` 中的 `COMMIT` 常量
3. 编译发布新 APK 或热更新配置

### 2.3 版本号机制 — 避免无效下载

```
version.txt:
7                   ← 纯数字版本号
v7: 300 entries...  ← 第二行统计描述(可选)
```

```kotlin
val localVer = cacheVer.readText()...toIntOrNull() ?: 0
val remoteVer = httpGet(VERSION_URL)...toIntOrNull() ?: 0

if (remoteVer > localVer) {
    downloadJSON()       // 仅版本变化时才下载完整 JSON
} else {
    // 数据已是最新，跳过
}
```

**优势**：version.txt 只有几十字节，每次启动仅需一次 HTTP HEAD/GET，不消耗配额。

---

## 三、实现细节

### 3.1 目录结构

```
go3dglobe-data/          ← 独立数据仓库（不与代码混在一起）
├── webcams.json         ← 核心数据（300条，~45KB）
├── version.txt          ← 版本号（<100B）
└── README.md            ← 说明文档

Go3DGlobe/               ← 主项目仓库
├── app/.../WebcamLayer.kt  ← 数据获取+渲染
├── app/.../assets/webcams.json ← APK内置兜底
└── github-cloud-resource-guide.md ← 本文档
```

**为什么数据要独立仓库？**
- 主仓库 `Go3DGlobe` 已有 100MB+ 代码和瓦片
- 数据仓库 `go3dglobe-data` 轻量（<50KB），方便 jsDelivr 加速
- 数据更新不影响主项目版本号
- 数据仓库可以单独管理提交历史

### 3.2 HTTP 请求规范

```kotlin
private fun httpGet(url: String, timeoutMs: Int = 8000): String? {
    var conn: HttpURLConnection? = null
    return try {
        // CDN 请求加时间戳绕过缓存（分钟级精度即可）
        val cacheBustUrl = if (url.contains("jsdelivr.net"))
            "${url}?_t=${System.currentTimeMillis() / 60000}"
        else url

        conn = (URL(cacheBustUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs + 5000
            setRequestProperty("User-Agent", "Go3DGlobe/3.6")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }

        if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else null
    } catch (e: Exception) {
        Log.w(TAG, "httpGet fail: ${e.message}")
        null
    } finally { conn?.disconnect() }
}
```

**关键点**：
- `connectTimeout` 和 `readTimeout` 分开设置（连接超时短，读取超时长）
- `User-Agent` 标识自己（虽非必须，但是好习惯）
- jsDelivr 用分钟级时间戳绕过 CDN 边缘缓存
- 失败不崩溃，返回 null 走下一级回退

### 3.3 数据格式约定

```json
[
  {
    "id": "us-rainier",
    "name": "Mount Rainier",
    "city": "Ashford",
    "country": "United States",
    "lat": 46.85,
    "lng": -121.76,
    "url": "https://.../image.jpg",
    "refreshSec": 10,
    "width": 1280,
    "height": 720,
    "streamType": "jpeg"
  }
]
```

**字段约定**：
- `id`: 全局唯一，格式 `{国家码}-{标识}`，如 `cn-beijing`
- `url`: 直接可 HTTP GET 获取，无需认证
- `refreshSec`: 建议刷新间隔，前端据此控制刷新频率
- `streamType`: `"jpeg"`(图片) 或 `"hls"`(视频流)

### 3.4 多级缓存策略

| 层级 | 存储 | 更新时机 | 作用 |
|------|------|---------|------|
| L0 | APK assets/ | 发布时 | 首次安装兜底 |
| L1 | 磁盘 cache/ | 远程版本更新时 | 离线可用 |
| L2 | 内存 webcams list | 解析JSON后 | 实时渲染 |
| L3 | 远程 raw/CDN | 每次启动检查 | 最新数据 |

```
启动流程:
  ┌─ L1 磁盘缓存存在 → 立即渲染（0ms延迟）
  ├─ 延迟3秒 → 检查远程版本
  │   ├─ 版本更新 → 下载JSON → 写入L1 → 重新渲染
  │   └─ 版本一致 → 跳过
  └─ L1不存在 → 尝试L0 assets → 渲染
```

---

## 四、遇到的问题与解决方案

### 问题 1: 国内设备访问 raw.githubusercontent.com 超时

**现象**：部分 Wi-Fi/4G 网络下，HTTP 请求 4 秒超时。

**原因**：raw.githubusercontent.com 的 CDN 节点在国内不稳定。

**解决**：双源策略 — raw GitHub 设 4 秒超时，超时自动切 jsDelivr CDN。

```kotlin
// 1. 先试 raw GitHub（短超时 4s）
var rawVer = httpGet(VERSION_URL, 4000)
if (rawVer == null) {
    // 2. 超时则用 jsDelivr commit-hash URL（长超时 8s）
    rawVer = httpGet(VERSION_CDN, 8000)
}
```

### 问题 2: jsDelivr @main 有 3 分钟缓存延迟

**现象**：Push 新数据后，app 仍用旧数据，等 3 分钟才生效。

**原因**：jsDelivr 对 `@main`（分支名）有边缘缓存，默认 3 分钟刷新。

**解决**：用 `@{commit-hash}` 替代 `@main`。

```
❌ cdn.jsdelivr.net/gh/user/repo@main/file.json   → 3分钟缓存
✅ cdn.jsdelivr.net/gh/user/repo@a8ba108/file.json → 永久唯一、即时生效
```

每次 push 后更新代码中的 commit hash 常量即可。

### 问题 3: CDN 返回旧数据（中间节点缓存）

**现象**：同一 URL 多次请求返回相同内容，即使源已更新。

**原因**：CDN 中间节点缓存了响应。

**解决**：URL 加分钟级时间戳参数。

```kotlin
val cacheBustUrl = "${url}?_t=${System.currentTimeMillis() / 60000}"
```

注意用**分钟级**而非秒级时间戳 — 秒级会导致 CDN 缓存命中率为 0，分钟级平衡了新鲜度和缓存效率。

### 问题 4: GitHub 文件大小限制

**现象**：单个文件超过 100MB 无法 push。

**原因**：GitHub 对单个文件限制 100MB。

**解决**：数据拆分为多文件（如按地区分片），或压缩数据格式。

对于 webcams.json（~45KB），完全在限制内，无需处理。

### 问题 5: 网络全部不可用时的兜底

**现象**：无网络时 app 无法获取任何摄像头数据。

**解决**：三级兜底：
1. 磁盘缓存 `webcams_cache.json`（上次成功下载的版本）
2. APK 内置 `assets/webcams.json`（编译时打包的版本）
3. 空列表（至少不崩溃）

```kotlin
// 兜底逻辑
if (!updated && webcams.isEmpty()) {
    // 从 APK assets 加载内置版本
    val json = ctx.assets.open("webcams.json").bufferedReader().readText()
    parseAndRender(json)
}
```

---

## 五、合规性说明

### 5.1 GitHub 服务条款

**GitHub 允许的行为**：
- ✅ 公开仓库的 raw 内容通过 raw.githubusercontent.com 免费访问
- ✅ jsDelivr 等第三方 CDN 镜像 GitHub 公开仓库（jsDelivr 官方支持）
- ✅ 非商业/商业项目的合理数据分发

**GitHub 不允许的行为**：
- ❌ 用 raw.githubusercontent.com 作为生产 API（高频率轮询可能被限流）
- ❌ 存储用户生成内容（无审核机制）
- ❌ 用 GitHub Actions 做加密货币挖矿等滥用

**我们的合规做法**：
- 数据是**公开的静态 JSON**，不是动态 API
- 版本号机制确保只在数据更新时才下载完整 JSON（不是每次轮询）
- version.txt 仅几十字节，高频检查不影响 GitHub
- 主要流量走 jsDelivr CDN（不是直接打 GitHub）

### 5.2 jsDelivr 使用规范

jsDelivr 是 GitHub 官方推荐的 CDN，免费无限制。
- ✅ 公开仓库内容自动可用
- ✅ 无带宽/请求次数限制
- ✅ 支持 commit hash / tag / branch 三种引用方式
- ⚠️ 单个文件建议 < 50MB（更大文件可能被限制）

### 5.3 数据安全

因为是**公开数据**（无用户隐私、无 API 密钥）：
- GitHub 仓库设为 Public 即可
- 无需 token、无需 OAuth、无需账号密码
- 访问者只需要知道仓库名和文件路径

---

## 六、标准化操作流程 (SOP)

### 6.1 创建数据仓库

```bash
# 1. 创建独立仓库
mkdir my-app-data
cd my-app-data
git init
git remote add origin git@github.com:YOUR_USER/my-app-data.git

# 2. 建立目录结构
echo "1" > version.txt
echo "# My App Data" > README.md
# 放入你的数据文件 (JSON/CSV/图片等)

# 3. 首次提交
git add -A
git commit -m "v1: initial data"
git push -u origin main
```

### 6.2 更新数据流程

```bash
# 1. 修改数据文件
vim data.json

# 2. 递增版本号
echo "2" > version.txt

# 3. 提交并记录 commit hash
git add -A
git commit -m "v2: 新增XXX数据"
git push

# 4. 复制最新 commit hash
git rev-parse --short HEAD
# 输出: a1b2c3d

# 5. 更新客户端代码中的 COMMIT 常量
# private const val COMMIT = "a1b2c3d"
```

### 6.3 客户端代码模板

```kotlin
class CloudDataLayer(private val ctx: Context) {

    companion object {
        // ⚠️ 每次推送新数据后更新此 hash
        private const val COMMIT = "a1b2c3d"
        private const val RAW_BASE = "https://raw.githubusercontent.com/USER/REPO/main"
        private const val CDN_BASE = "https://cdn.jsdelivr.net/gh/USER/REPO@$COMMIT"

        private const val VERSION_RAW = "$RAW_BASE/version.txt"
        private const val VERSION_CDN = "$CDN_BASE/version.txt"
        private const val DATA_RAW = "$RAW_BASE/data.json"
        private const val DATA_CDN = "$CDN_BASE/data.json"

        private const val CACHE_DATA = "cloud_data_cache.json"
        private const val CACHE_VER = "cloud_data_version.txt"
    }

    private val handler = Handler(Looper.getMainLooper())

    /** 启动时调用：缓存优先 → 3秒后检查更新 */
    fun load() {
        // 1. 磁盘缓存立即渲染
        val cacheFile = File(ctx.cacheDir, CACHE_DATA)
        if (cacheFile.exists()) {
            parse(cacheFile.readText())
        }

        // 2. 3秒后检查远程版本
        handler.postDelayed({ checkUpdate() }, 3000)
    }

    private fun checkUpdate() {
        Thread {
            try {
                val localVer = File(ctx.cacheDir, CACHE_VER)
                    .let { if (it.exists()) it.readText().trim().toIntOrNull() ?: 0 else 0 }

                // 双源版本检查
                var remoteVerStr = httpGet(VERSION_RAW, 4000)
                if (remoteVerStr == null) {
                    remoteVerStr = httpGet(VERSION_CDN, 8000)
                }
                val remoteVer = remoteVerStr?.trim()?.toIntOrNull() ?: 0

                if (remoteVer > localVer) {
                    // 下载数据（同源优先）
                    var data = httpGet(DATA_RAW, 10000)
                    if (data == null) data = httpGet(DATA_CDN, 15000)

                    if (data != null && data.trim().startsWith("[")) {
                        File(ctx.cacheDir, CACHE_DATA).writeText(data)
                        File(ctx.cacheDir, CACHE_VER).writeText(remoteVer.toString())
                        handler.post { parse(data) }
                    }
                }
            } catch (e: Exception) {
                Log.w("CloudData", "Update check failed: ${e.message}")
            }
        }.start()
    }

    private fun httpGet(url: String, timeoutMs: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            val finalUrl = if (url.contains("jsdelivr.net"))
                "${url}?_t=${System.currentTimeMillis() / 60000}" else url
            conn = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs + 5000
                setRequestProperty("User-Agent", "MyApp/1.0")
            }
            if (conn.responseCode == 200)
                conn.inputStream.bufferedReader().readText()
            else null
        } catch (e: Exception) { null }
        finally { conn?.disconnect() }
    }

    private fun parse(json: String) {
        // 你的数据解析逻辑
    }
}
```

---

## 七、适用场景与限制

### ✅ 适合用 GitHub 做云资源

| 场景 | 示例 |
|------|------|
| 静态配置分发 | 摄像头列表、城市坐标、颜色主题 |
| 开放数据集 | 地理信息、分类标签、元数据 |
| 小体积图片资源 | 图标、缩略图（<1MB/张） |
| 版本控制的资源 | 需要历史追溯的数据 |
| 全球分发 | 借助 jsDelivr 全球 750+ 节点 |

### ❌ 不适合

| 场景 | 原因 | 替代方案 |
|------|------|---------|
| 用户上传内容 | GitHub 不是 UGC 平台 | 云存储 (OSS/S3) |
| 实时高频写入 | Git 不是数据库 | Firebase/Supabase |
| 大文件 (>50MB) | CDN 限制 | 分段下载或专用 CDN |
| 需要认证的数据 | 公开仓库无权限控制 | 私有仓库 + Token |
| 毫秒级实时性 | CDN 有分钟级缓存 | WebSocket/长连接 |

---

## 八、成本分析

以 Go3DGlobe 为例：

| 项目 | 成本 |
|------|------|
| GitHub 仓库托管 | **免费**（公开仓库，<1GB） |
| jsDelivr CDN 流量 | **免费**（无限制） |
| raw.githubusercontent.com | **免费**（合理使用） |
| 日均请求量 | ~3000 次（27 个摄像头 × 每次刷新） |
| 月均流量 | ~500MB（version.txt 70B × 3K/天 + 偶尔 JSON 45KB） |
| **总成本** | **$0.00** |

---

## 九、参考链接

- [jsDelivr GitHub CDN 文档](https://www.jsdelivr.com/?docs=gh)
- [GitHub Raw Content](https://docs.github.com/en/repositories/working-with-files/using-files/viewing-a-file#viewing-or-copying-the-raw-file-content)
- [GitHub 可接受使用政策](https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies)
- Go3DGlobe 实现代码: `app/src/main/java/com/globe/dualscreen/WebcamLayer.kt`
- 数据仓库: `github.com/caucy2026/go3dglobe-data`
