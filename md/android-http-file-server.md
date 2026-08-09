# Android 内置 HTTP 文件分发服务 — 完整实现文档

> 项目: kemi-bt-board | 版本: v1.1.2 | 整理日期: 2026-07-28
>
> **目的**: Android App 内置一个轻量级 HTTP 服务器，让同一局域网的 Windows 客户端通过浏览器访问并下载平台相关文件（EXE / 脚本），实现"零配置"分发。
>
> **2026-08-09 HTTPS补充**：PAD私网IP仍使用短生命周期HTTP；跨网下载和所有账号接口使用HBBC云端HTTPS `https://kemi-chat.newlinksz.com:21121`。完整分工见 `hbbc-http-https-service-guide.md`。

---

## 目录

1. [功能概述](#1-功能概述)
2. [架构设计](#2-架构设计)
3. [源码详解](#3-源码详解)
4. [集成步骤（其他项目复用指南）](#4-集成步骤其他项目复用指南)
5. [踩坑记录](#5-踩坑记录)
6. [部署与配置](#6-部署与配置)
7. [Windows 端配套程序](#7-windows-端配套程序)

---

## 1. 功能概述

### 1.1 解决了什么问题

传统方式下，Windows 用户需要手动拷贝 EXE、配置注册表才能使用 KEMI 语音键盘的 Win 直投模式。这个 HTTP 服务让用户：

1. 打开 Android App，切换到"Win 直投"模式
2. 点击"如何安装 Windows U+ 助手"
3. 看到类似 `http://192.168.1.6:8686` 的地址
4. 在同一局域网的 Windows 浏览器中输入该地址
5. 下载 `install.bat` → 双击运行 → 自动完成安装和开机自启

**全程无需 U 盘、无需手动传文件、无需配置。**

### 1.2 服务内容

| 路由 | 说明 | 文件来源 |
|------|------|---------|
| `/` | HTML 安装说明页（带样式） | 代码内嵌生成 |
| `/install.bat` | 一键安装批处理脚本 | 代码内嵌生成 |
| `/install.ps1` | PowerShell 安装脚本 | 代码内嵌生成 |
| `/WinUnicodeIME.exe` | Windows U+ 助手可执行文件 | `assets/win-unicode-ime/WinUnicodeIME.exe`（编译时打入 APK） |
| `/health` | 健康检查端点 | 返回 `ok` |

---

## 2. 架构设计

```
┌──────────────────────────────────────────────────┐
│                 Android App                       │
│                                                   │
│  MainActivity.kt                                  │
│  ├─ onCreate() → WinInstallServer(context)        │
│  ├─ 切 Win 直投 → ensureStarted()                 │
│  ├─ 点击安装说明 → getPrimaryAccessUrl()           │
│  ├─ onPause/onStop → stop()                       │
│  └─ onDestroy → stop()                            │
│          │                                        │
│  ┌───────▼────────────────────────────────────┐  │
│  │  WinInstallServer.kt                       │  │
│  │  ├─ ServerSocket(FIXED_PORT=8686)           │  │
│  │  ├─ acceptLoop() → thread per client        │  │
│  │  ├─ handleClient(): HTTP 1.1 解析           │  │
│  │  ├─ buildInstallHtml() → HTML 安装页        │  │
│  │  ├─ buildInstallBat() → install.bat 脚本    │  │
│  │  ├─ buildInstallPowerShell() → install.ps1  │  │
│  │  ├─ writeBinaryDirect() → 读 assets 发 EXE  │  │
│  │  └─ getLocalIpv4Addresses() → 局域网 IP     │  │
│  └────────────────────────────────────────────┘  │
│                                                   │
│  assets/win-unicode-ime/WinUnicodeIME.exe          │
│  (编译时从 win-unicode-ime/ 目录打入 APK)          │
└──────────────────────────────────────────────────┘
         │  局域网 HTTP (端口 8686)
         ▼
┌──────────────────────────────────────────────────┐
│           Windows 客户端（浏览器）                │
│                                                   │
│  1. 浏览器打开 http://192.168.x.x:8686            │
│  2. 下载 install.bat                              │
│  3. 双击运行 → 自动下载 EXE + 注册开机自启        │
└──────────────────────────────────────────────────┘
```

### 2.1 关键设计决策

| 决策 | 理由 |
|------|------|
| 固定端口 8686，含 fallback | 优先使用固定端口方便记忆；被占用时 fallback 到系统分配 |
| 纯 Java `ServerSocket`，不用框架 | 零依赖，APK 体积无增加 |
| 每个请求一个线程 | 并发量极低（同一时刻只有 1 个用户在安装），简单可控 |
| HTML/脚本代码内嵌生成 | 不需要额外资源文件管理，内容是动态的（IP、端口、Wi-Fi 名） |
| EXE 放 `assets/` 目录 | APK 编译时自动打包，运行时通过 `context.assets.open()` 读取 |
| `Content-Disposition: attachment` | 强制浏览器下载而非预览 |

### 2.2 与云端HTTPS的互补

本地HTTP只解决“同一局域网内从PAD高速下载”。遇到访客Wi-Fi、AP隔离、多路由级联、手机热点或不同网络时，即使两台设备都能上网，也可能无法直接访问PAD IP。

此时客户端应提供第二入口：

```text
同局域网下载：http://<PAD-IP>:8686
云端HTTPS下载：https://kemi-chat.newlinksz.com:21121/<site-path>
```

选择逻辑：

1. PAD地址可达时优先本地HTTP。
2. 本地超时或不在同一局域网时，立即切换云端HTTPS。
3. 手机号、密码、验证码、Token、支付和管理功能一律禁止放在本地HTTP。
4. 云端下载新项目默认使用HTTPS `21121`；HTTP `21120`只为旧客户端保留兼容。

---

## 3. 源码详解

### 3.1 文件结构

```
app/src/main/java/com/kboard/net/WinInstallServer.kt   ← 核心实现（~530 行）
app/src/main/java/com/kboard/MainActivity.kt            ← 集成调用点
app/src/main/assets/win-unicode-ime/WinUnicodeIME.exe   ← 分发的 EXE 文件
win-unicode-ime/Program.cs                              ← EXE 的 C# 源码
win-unicode-ime/build.bat                               ← EXE 的编译脚本
```

### 3.2 WinInstallServer 类结构

```kotlin
class WinInstallServer(private val context: Context) {

    // ========== 常量 ==========
    companion object {
        const val FIXED_PORT = 8686           // 固定端口
        const val ASSET_HELPER_PATH = "win-unicode-ime/WinUnicodeIME.exe"
    }

    // ========== 状态 ==========
    @Volatile private var running = false     // 服务运行标志
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null

    // ========== 公开 API ==========
    fun ensureStarted(): Boolean     // 启动服务（幂等）
    fun stop()                       // 停止服务
    fun getAccessUrls(): List<String>  // 获取所有访问 URL
    fun getPrimaryAccessUrl(): String? // 获取首选 URL
    fun getCurrentWifiName(): String?  // 获取当前 Wi-Fi 名称
}
```

### 3.3 核心流程

#### 3.3.1 启动服务 `ensureStarted()`

```
ensureStarted()
│
├─ 检查状态: 已运行 → return true（幂等）
│
├─ synchronized 双重检查
│
├─ 尝试端口: [8686, 0]  // 固定端口优先，0=系统分配
│   ├─ ServerSocket(port).reuseAddress = true
│   ├─ acceptThread = thread(name="win-install-server")
│   │   └─ acceptLoop(ss)
│   └─ 成功 → return true
│
└─ 都失败 → return false
```

**设计要点:**
- 双重检查 + `synchronized` 保证线程安全
- `reuseAddress = true` 避免快速重启时端口占用
- `@Volatile` 确保跨线程可见性
- 线程命名 `"win-install-server"` 便于调试

#### 3.3.2 接收连接 `acceptLoop()`

```
acceptLoop(ss)
│
└─ while (running)
    ├─ ss.accept() → client Socket
    ├─ thread(name="win-install-client") { handleClient(client) }
    │   └─ 每个客户端一个独立线程
    └─ SocketException → break（stop() 关闭 socket 触发）
```

#### 3.3.3 处理请求 `handleClient()`

```
handleClient(client)
│
├─ 1. client.soTimeout = 7000ms   // 防止僵死连接
│
├─ 2. 读取 HTTP 请求行: "GET / HTTP/1.1"
│   └─ 解析 method, path, 读取 headers
│
├─ 3. 仅处理 GET 请求
│
├─ 4. 路由分发:
│   ├─ "/"              → buildInstallHtml(host) → 返回 HTML
│   ├─ "/install.bat"   → buildInstallBat(host)  → 返回 .bat 脚本
│   ├─ "/install.ps1"   → buildInstallPowerShell(host) → 返回 .ps1
│   ├─ "/WinUnicodeIME.exe" → writeBinaryDirect() → 读 assets 发二进制
│   ├─ "/health"        → 返回 "ok"
│   └─ else             → 404
│
├─ 5. output.flush() + shutdownOutput()
│
└─ 6. finally: client.close()
```

### 3.4 HTTP 响应构建

#### 3.4.1 响应头 `writeHeadersDirect()`

手动构建 HTTP/1.1 响应头，不使用任何 HTTP 库：

```kotlin
private fun writeHeadersDirect(
    output: OutputStream,
    statusCode: Int,       // 200/400/404/405
    contentType: String,   // text/html, application/octet-stream 等
    contentLength: Long,
    attachmentName: String? = null  // 可选文件下载名
) {
    StringBuilder()
        .append("HTTP/1.1 ").append(statusCode).append(' ').append(reason)
        .append("\r\n")
        .append("Content-Type: ").append(contentType).append("\r\n")
        .append("Content-Length: ").append(contentLength).append("\r\n")
        .append("Connection: close\r\n")              // ← 关键：不保持连接
        .apply {
            attachmentName?.let {
                append("Content-Disposition: attachment; filename=\"$it\"\r\n")
            }
        }
        .append("\r\n")
        .let { output.write(it.toString().toByteArray(US_ASCII)) }
}
```

**关键点:**
- `Connection: close` — 每次请求后关闭连接，简化实现
- `Content-Disposition: attachment` — 强制浏览器下载，不预览
- 仅 `US_ASCII` 编码写 header（HTTP 协议规定）
- Body 使用 `UTF-8` 编码

#### 3.4.2 二进制文件发送 `writeBinaryDirect()`

```kotlin
private fun writeBinaryDirect(output: OutputStream, assetPath: String) {
    try {
        context.assets.open(assetPath).use { ins ->
            val bytes = ins.readBytes()
            // ⚠️ 必须一次性全部读取再写入，不要用 BufferedOutputStream 分段写！
            writeHeadersDirect(output, 200, "application/x-msdownload",
                bytes.size.toLong(), attachmentName = "WinUnicodeIME.exe")
            output.write(bytes)     // ← 一次性写入全部字节
            output.flush()
        }
    } catch (e: Exception) {
        // 404: 文件未打包进 APK
        writeTextDirect(output, 404, "not bundled: ${e.message}", "text/plain")
    }
}
```

> ⚠️ **关键坑**: 必须 `ins.readBytes()` 一次性读完全部字节，再用 `output.write(bytes)` 一次性写。详见 §5.3。

### 3.5 IP 地址获取

```kotlin
private fun getLocalIpv4Addresses(): List<String> {
    NetworkInterface.getNetworkInterfaces()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { intf -> intf.inetAddresses.asSequence() }
        .filter { isUsableIpv4(it) }
        .map { it.hostAddress!! }
        .distinct().sorted()
}

private fun isUsableIpv4(addr: InetAddress): Boolean {
    if (addr !is Inet4Address) return false
    if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress)
        return false
    val host = addr.hostAddress ?: return false
    return host.isNotBlank() && host != "0.0.0.0"
}
```

**过滤规则:**
- 仅 IPv4
- 排除 127.x.x.x (回环)
- 排除 0.0.0.0 (any)
- 排除 169.254.x.x (链路本地)
- `distinct().sorted()` 去重排序

### 3.6 Wi-Fi 名称获取

```kotlin
private fun readCurrentWifiSsid(): String? {
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val info = wifiManager?.connectionInfo
    val rawSsid = info?.ssid
    // 去掉 Android 自动包裹的双引号
    return rawSsid?.removePrefix("\"")?.removeSuffix("\"")?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
}
```

> **注意**: Android 的 `WifiInfo.ssid` 返回的字符串**自带双引号包裹**（如 `"MyWiFi"` 而不是 `MyWiFi`），必须去掉。

### 3.7 MainActivity 集成点

```kotlin
// ===== 1. 初始化 (onCreate) =====
winInstallServer = WinInstallServer(applicationContext)

// ===== 2. 切到 Win 直投模式时自动启动 =====
if (::winInstallServer.isInitialized) {
    winInstallServer.ensureStarted()
}

// ===== 3. 用户点击「如何安装」按钮 =====
winInstallServer.ensureStarted()
val primaryUrl = winInstallServer.getPrimaryAccessUrl()  // http://192.168.1.6:8686
val wifiName = winInstallServer.getCurrentWifiName()      // "MyWiFi"
// → 显示在 UI 上

// ===== 4. 退出/生命周期结束时停止 =====
if (::winInstallServer.isInitialized) {
    winInstallServer.stop()
}
```

---

## 4. 集成步骤（其他项目复用指南）

### 第 1 步：拷贝源码文件

将 `WinInstallServer.kt` 复制到你的项目，修改 `package` 声明。

### 第 2 步：准备分发的文件

将需要分发的文件放入 `app/src/main/assets/` 目录：

```
app/src/main/assets/
├── my-dist/
│   ├── my-tool.exe          ← 你要分发的 EXE
│   └── my-config.json       ← 其他配置文件
```

修改 `WinInstallServer.kt` 中的常量：
```kotlin
companion object {
    private const val FIXED_PORT = 8686          // 可以改端口
    private const val ASSET_HELPER_PATH = "my-dist/my-tool.exe"  // 你的文件路径
}
```

### 第 3 步：修改 HTML 页面和脚本

修改 `buildInstallHtml()`、`buildInstallBat()`、`buildInstallPowerShell()` 三个方法：

- **HTML 页面**: 改标题、样式、安装步骤说明
- **install.bat**: 改 EXE 文件名、目标安装目录、注册表键名
- **install.ps1**: 同理

注意事项：
- `$baseUrl` 是服务自动拼好的 `http://IP:PORT`，脚本里用它拼接下载 URL
- `.bat` 文件用 `US_ASCII` 编码输出（Windows 控制台兼容）
- `.ps1` 里的 `$` 变量需要转义为 `${'$'}`（Kotlin 字符串模板冲突）

### 第 4 步：在 Activity 中集成

```kotlin
// 初始化
private lateinit var distServer: WinInstallServer

override fun onCreate(savedInstanceState: Bundle?) {
    distServer = WinInstallServer(applicationContext)
}

// 需要分发时启动
fun onStartDistribution() {
    distServer.ensureStarted()
    val url = distServer.getPrimaryAccessUrl()
    // 显示给用户: "请在电脑浏览器打开: $url"
}

// 停止
override fun onDestroy() {
    if (::distServer.isInitialized) {
        distServer.stop()
    }
    super.onDestroy()
}
```

### 第 5 步：AndroidManifest 权限

```xml
<!-- 需要 ACCESS_WIFI_STATE 获取 Wi-Fi 名称（非必须，可选功能） -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

<!-- 需要 INTERNET 权限已在大部分 App 中默认拥有 -->
<uses-permission android:name="android.permission.INTERNET" />
```

### 第 6 步：ProGuard 规则（如需混淆）

```
-keep class com.kboard.net.WinInstallServer { *; }
```

---

## 5. 踩坑记录

### 5.1 坑 1：BufferedOutputStream 导致 EXE 二进制截断

**现象**: 通过 HTTP 下载的 `WinUnicodeIME.exe` 文件不完整，Windows 提示"不是有效的 Win32 应用程序"。

**根因**: 早期实现使用 `BufferedOutputStream` 包装 `socket.getOutputStream()`：

```kotlin
// ❌ 错误写法 — BufferedOutputStream 不可靠
val bos = BufferedOutputStream(socket.getOutputStream())
bos.write(bytes, 0, bytes.size)
bos.flush()
// BufferedOutputStream 在某些 JVM 实现中 flush() 不保证全部写入
```

**修复**: 直接使用 `socket.getOutputStream()` 的 `write(byte[])` 一次性写：

```kotlin
// ✅ 正确写法
val output = socket.getOutputStream()
output.write(bytes)    // 直接 write(byte[])，不用 Buffered 包装
output.flush()
```

**教训**: Android 的 `ServerSocket` 场景下不要用 `BufferedOutputStream` 包装 `OutputStream`，直接调用 `write(byte[])` + `flush()`。

### 5.2 坑 2：install.ps1 右键运行闪退

**现象**: 用户从浏览器下载 `install.ps1` 后，右键 → "使用 PowerShell 运行"，窗口一闪而过。

**三重根因:**

| 原因 | 说明 |
|------|------|
| **Mark of the Web (MotW)** | 浏览器从互联网下载的文件会被 Windows 标记为"来自互联网"，PowerShell 默认拒绝执行 |
| **执行策略 Restricted** | 默认 PowerShell 执行策略阻止所有脚本 |
| **`$` 变量展开** | Kotlin 字符串模板中 `$baseUrl` 被错误解释为 Kotlin 变量，生成的 ps1 中 `$` 消失 |

**修复方案（install.bat 替代）:**
- 采用 `install.bat` 方案替代 `.ps1`，不受 MotW 和执行策略限制
- `.bat` 内部调用 `powershell -ExecutionPolicy Bypass -Command "..."` 来执行下载
- `.ps1` 保留作为备选方案，但主推 `.bat`

**PowerShell 脚本中 `$` 的正确写法:**
```kotlin
// ❌ 错误 — Kotlin 把 $baseUrl 当成模板变量
"${'$'}baseUrl = '$base'"   // 生成的 ps1: baseUrl = 'http://...'  ← $ 丢了

// ✅ 正确 — 用 ${'$'} 转义
"${'$'}baseUrl = '$base'"   // 生成的 ps1: $baseUrl = 'http://...'
```

### 5.3 坑 3：Android WifiInfo.ssid 自带双引号

**现象**: Wi-Fi 名称显示为 `"MyWiFi"` 而不是 `MyWiFi`，导致 HTML 页面显示异常。

**根因**: `WifiInfo.ssid` 返回的字符串**自动包裹在双引号中**。

**修复:**
```kotlin
val rawSsid = info.ssid
val normalized = rawSsid.removePrefix("\"").removeSuffix("\"").trim()
```

### 5.4 坑 4：端口 8686 被占用

**场景**: App 被杀死后立即重启，系统可能还没释放端口，导致 `ServerSocket(8686)` 失败。

**修复:**
1. `reuseAddress = true` — 允许立即重用端口
2. `fallback` — 如果 8686 被占，自动降到系统分配端口（port=0）
3. `stop()` 中 `serverSocket?.close()` — 确保关闭

```kotlin
val ports = intArrayOf(FIXED_PORT, 0)   // 0 = OS 分配
for (port in ports) {
    try {
        val ss = ServerSocket(port)
        ss.reuseAddress = true          // ← 关键
        // ...
    } catch (e: IOException) { /* try next */ }
}
```

### 5.5 坑 5：线程泄漏

**现象**: 多次切换 Win 直投模式后，后台线程越来越多。

**修复:**
- `ensureStarted()` 加入双重检查 + `synchronized`，避免重复创建
- `stop()` 中关闭 `serverSocket`，`acceptLoop` 会收到 `SocketException` 后退出
- `handleClient` 中 `finally { client.close() }`

### 5.6 坑 6：局域网 HTTP 被浏览器标记"不安全"

**现象**: Chrome 等浏览器在 HTTP 页面上显示"不安全"警告。

**说明**: 这是正确行为 — 局域网动态IP无法使用与公网域名匹配的 HTTPS 证书。在 HTML 页面中添加说明让用户知道这是正常的，并同时给出云端HTTPS入口：

```html
<div class="note">
  ⚠️ 本页面通过局域网 HTTP 传输，浏览器提示"不安全"是正常现象。
  所有文件由手机直接提供，不会经过互联网。
  如果不在同一局域网，请使用云端 HTTPS 下载。
</div>
```

### 5.7 坑 7：Content-Type 不对导致浏览器行为异常

| 文件 | 正确的 Content-Type | 错误的后果 |
|------|---------------------|-----------|
| EXE | `application/x-msdownload` | 浏览器可能尝试显示二进制乱码 |
| .bat | `application/octet-stream` | 浏览器可能直接显示文本内容 |
| .ps1 | `application/octet-stream` | 同上 |
| HTML | `text/html; charset=utf-8` | 中文乱码 |

**关键**: 所有文件下载**必须加 `Content-Disposition: attachment; filename="..."`**，否则浏览器可能尝试渲染。

---

## 6. 部署与配置

### 6.1 Windows 客户端部署架构

```
┌─────────────────────────────────────────────────────┐
│                  install.bat 流程                     │
│                                                       │
│  [1/4] 创建目录 %LOCALAPPDATA%\KemiUnicodeIME\        │
│  [2/4] PowerShell 下载 EXE 来自 http://IP:8686/       │
│  [3/4] 注册表 HKCU\...\Run 添加开机自启               │
│  [4/4] 启动 WinUnicodeIME.exe                         │
│                                                       │
│  安装位置: %LOCALAPPDATA%\KemiUnicodeIME\              │
│  自启注册: HKCU\Software\Microsoft\Windows\           │
│             CurrentVersion\Run\KemiWinUnicodeIME       │
└─────────────────────────────────────────────────────┘
```

### 6.2 网络安全

- 服务**仅在局域网内可用**（监听所有网络接口的 IPv4 地址）
- 没有认证机制（局域网信任模型）
- 没有 TLS（局域网 HTTP 不需要）
- 所有文件直接来自手机，不经过任何云端服务器

---

## 7. Windows 端配套程序

### 7.1 WinUnicodeIME.exe

- **源码**: `win-unicode-ime/Program.cs`
- **编译**: `win-unicode-ime/build.bat`（需要 .NET Framework 4.x SDK）
- **功能**: WH_KEYBOARD_LL 全局键盘钩子，拦截 `Alt + Numpad+ + 4位Hex` 并输出 Unicode 中文
- **运行方式**: 系统托盘常驻，无窗口

### 7.2 文件分发清单

| 文件 | 类型 | 生成方式 |
|------|------|---------|
| `WinUnicodeIME.exe` | 二进制 | C# 编译产物，放入 `assets/win-unicode-ime/` |
| `install.bat` | 脚本 | `buildInstallBat()` 运行时动态生成 |
| `install.ps1` | 脚本 | `buildInstallPowerShell()` 运行时动态生成 |
| HTML 安装页 | 页面 | `buildInstallHtml()` 运行时动态生成 |

---

## 附录 A：关键日志标签

| TAG | 来源 | 关注内容 |
|-----|------|---------|
| `WinInstallServer` | WinInstallServer.kt | 服务启动/停止、客户端连接、二进制文件服务 |
| `MainActivity` | MainActivity.kt | 服务启停调用、URL 显示 |

---

## 附录 B：复用检查清单

其他项目要复用这套方案，确认以下事项：

- [ ] 拷贝 `WinInstallServer.kt`，修改 package
- [ ] 修改 `FIXED_PORT`（可选）
- [ ] 修改 `ASSET_HELPER_PATH` 指向你的文件
- [ ] 将分发的 EXE/文件放入 `assets/` 目录
- [ ] 修改 `buildInstallHtml()` — 标题、说明文字、下载按钮文字
- [ ] 修改 `buildInstallBat()` — 目标目录、EXE 文件名、注册表键名
- [ ] 修改 `buildInstallPowerShell()` — 同上
- [ ] Kotlin 字符串中的 `$` 用 `${'$'}` 转义
- [ ] 在 Activity 中调用 `ensureStarted()` / `stop()`
- [ ] 测试 EXE 二进制下载完整性（检查文件大小是否匹配）
- [ ] 测试 .bat 右键运行是否正常
- [ ] 检查 Wi-Fi SSID 是否去掉多余双引号
- [ ] HTML 页面添加"不安全提示"说明
- [ ] 确认 `Content-Disposition: attachment` 生效
- [ ] AndroidManifest 添加 `ACCESS_WIFI_STATE` 权限（如需显示 Wi-Fi 名）
