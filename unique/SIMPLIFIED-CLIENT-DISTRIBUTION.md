# 多平台客户端下载与局域网分发——客户端精简版

> 文档定位：供 KEMI 快传、KEMI 远程办公及其他跨平台产品复用。
>
> 本版只实现“云端四个固定资源 → PAD 本地缓存 → 一个局域网 HTTP 页面”。不建设 HBBC 下载网页，不新建云端 HTTP 服务，不使用 release-manifest、SHA256SUMS 或额外版本文件。

## 1. 最终结论

管理员只需把 Android/PAD、Windows、macOS、Linux 四个正式客户端上传到新智联现有云后台。云后台的每个平台接口已经返回文件实际下载地址、版本号和 MD5，因此不再另外上传版本文件或校验文件。

PAD 完成以下工作：

1. 访问四个固定 `plugData` 接口，解析各平台的 `url`、`version`、`md5` 和 `nickname`。
2. 将云端版本与 PAD 本地已校验缓存比较，只下载新增或变化的客户端。
3. 下载完成后校验 MD5，校验成功才替换正式缓存。
4. 用户进入“客户端下载”页时，只启动一个 PAD 本地 HTTP 服务。
5. 同一局域网内的其他设备打开 PAD 页面，下载 PAD 已缓存的四个平台客户端。
6. 用户离开该页面时关闭本地 HTTP 服务。
7. 如果云端 Android/PAD 版本高于当前安装版本，在 Android 一行显示“更新”按钮，由用户确认升级。

完整链路只有一条：

```text
管理员上传四端正式文件
        ↓
新智联云后台四个固定 plugData 接口
        ↓  返回 version / md5 / nickname / url
PAD 解析并下载到本地缓存
        ↓  MD5 校验通过后原子替换
PAD 唯一的局域网 HTTP 服务
        ↓
同一局域网内的 Windows / macOS / Linux / PAD 下载
```

## 2. 明确不做什么

为了保证方案足够简单，以下内容不属于本版：

- 不开发 HBBC 云端下载页面。
- 不在云服务器上再创建一个 HTTP/HTTPS 下载服务。
- 不为四个平台分别创建 HTTP 服务或端口。
- 不在 PAD 上创建多个 HTTP 服务。
- 不使用 `release-manifest.json`。
- 不使用 `SHA256SUMS.txt`。
- 不把 GitHub 作为客户端的运行时下载源或备用源。
- 不把每次变化的 CDN 长地址写死在客户端。
- 不让局域网浏览器直接跳转到云端；浏览器只下载 PAD 已缓存并校验成功的文件。

“四个平台”指四个云端资源和四个本地文件，不是四个 HTTP 服务。

## 3. 云端固定资源

### 3.1 KEMI 远程办公当前约定

后台项目固定为 `Common`，四个资源名固定不变：

| 平台 | 固定资源名 | 固定解析接口 |
|---|---|---|
| Android/PAD | `KEMI-PAD` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-PAD` |
| Windows | `KEMI-Windows` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Windows` |
| macOS | `KEMI-macOS` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-macOS` |
| Linux | `KEMI-Linux` | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Linux` |

管理员入口：

`https://www.newlinksz.cn/screensaver/main/configPlug/Common`

管理员每次发布仍覆盖相同的四个资源名。客户端保存的是上面的固定解析接口，不保存后台管理地址，也不保存接口本次返回的 CDN 地址。

### 3.2 接口字段

客户端至少解析以下字段：

```json
{
  "data": [
    {
      "name": "KEMI-PAD",
      "nickname": "KEMI-PAD.apk",
      "version": "1.4.89+196",
      "md5": "32位MD5",
      "size": 123456789,
      "url": "https://cdn.newlink-sz.com/Common/..."
    }
  ]
}
```

字段用途：

| 字段 | 用途 | 是否必需 |
|---|---|---|
| `url` | 本次真实 HTTPS 下载地址 | 是 |
| `version` | 页面显示、缓存判断、PAD 自更新比较 | 是 |
| `md5` | 下载完整性校验和缓存身份 | 是 |
| `nickname` | 建议的下载文件名 | 是 |
| `size` | 下载进度和空间预检 | 建议；为 0 时可读取 HTTP `Content-Length` |
| `name` | 核对是否拿到正确资源 | 建议 |

接口返回失败、`data` 为空、URL 非 HTTPS、版本或 MD5 缺失时，不覆盖已有缓存。页面继续提供最后一次校验成功的版本，并显示“云端检查失败，当前提供本地缓存版本”。

### 3.3 版本号要求

后台 `version` 必须填写真实客户端版本，不能一直填写 `1`。推荐格式：

```text
Android/PAD：1.4.89+196
Windows：1.4.89
macOS：1.4.89+196
Linux：1.4.89
```

Android 比较时将 `1.4.89+196` 拆为：

- 展示版本：`1.4.89`
- 构建号：`196`

先逐段比较展示版本，再比较构建号。禁止直接按字符串比较，否则 `1.4.10` 可能被错误判断为小于 `1.4.9`。

## 4. 管理员与客户端开发的分工

### 4.1 管理员每次发布只做这些事

1. 获取已经签名并验收的四个平台正式文件。
2. 进入 `Common` 项目。
3. 覆盖 `KEMI-PAD`、`KEMI-Windows`、`KEMI-macOS`、`KEMI-Linux` 四个固定资源。
4. 为每个资源填写正确版本号；云后台生成或保存 MD5。
5. 上传后分别打开四个固定解析接口，确认 `version`、`md5`、`nickname`、`url` 已更新。
6. 将四个接口的验收结果通知客户端开发或测试人员。

管理员不需要：

- 生成第五、第六个清单文件；
- 修改客户端源码；
- 修改 PAD HTTP 页面；
- 配置 HBBC 下载站点；
- 把动态 CDN URL 手工写回客户端。

### 4.2 PAD 客户端负责这些事

1. 内置四个固定解析接口。
2. 并行查询四个平台元数据。
3. 按版本和 MD5 更新本地缓存。
4. 展示每个平台的云端版本、本地缓存状态和下载进度。
5. 启停唯一的局域网 HTTP 服务。
6. 为局域网访问者生成简单下载页和二维码。
7. 发现 PAD 自身有新版本时显示更新入口。

## 5. PAD 本地缓存设计

### 5.1 建议目录

```text
Android/data/<applicationId>/files/client-cache/
├── android/
│   ├── KEMI-PAD.apk
│   └── asset.json
├── windows/
│   ├── KEMI-Windows.exe
│   └── asset.json
├── macos/
│   ├── KEMI-macOS.zip
│   └── asset.json
├── linux/
│   ├── KEMI-Linux.AppImage
│   └── asset.json
└── temp/
    └── *.part
```

`asset.json` 只记录这个平台最近一次校验成功的信息，不是跨平台发布清单：

```json
{
  "platform": "android",
  "version": "1.4.89+196",
  "md5": "32位MD5",
  "nickname": "KEMI-PAD.apk",
  "bytes": 123456789,
  "verifiedAt": "2026-08-12T15:30:00+08:00"
}
```

### 5.2 是否需要重新下载

以下条件全部满足时直接复用缓存：

- 正式缓存文件存在；
- `asset.json` 存在且可解析；
- 本地记录的 `md5` 与云端 `md5` 相同；
- 本地文件重新计算的 MD5 与记录一致。

任一条件不满足就下载到 `.part` 临时文件。下载完成后：

1. 校验文件非空；
2. 校验 MD5；
3. 可用时校验文件类型，例如 APK/PE/ZIP/AppImage；
4. 写入新的临时元数据；
5. 通过原子重命名替换正式文件和 `asset.json`。

下载失败或校验失败时删除对应 `.part`，旧缓存保持不变。

### 5.3 四个平台互不阻塞

每个平台维护独立状态：

```text
CHECKING → DOWNLOADING → VERIFYING → READY
    └──────────────→ ERROR（保留旧 READY 缓存）
```

Windows 下载失败不能导致 Android、macOS、Linux 不可下载。页面按平台显示状态，不采用“四项必须同时成功才能使用”的批次门槛。

## 6. PAD 唯一的本地 HTTP 服务

### 6.1 生命周期

- 进入“客户端下载”页：启动本地 HTTP 服务，并立即刷新云端元数据。
- 页面停留期间：服务保持运行，缓存更新完成后页面状态自动刷新。
- 离开“客户端下载”页：关闭 HTTP 服务，释放端口。
- App 退出或 Activity 销毁：兜底关闭服务。

同一个进程内只能存在一个服务实例。重复进入页面时应复用或先确认旧实例已经停止，不能重复绑定端口。

建议默认端口沿用当前客户端约定，例如 `8686`。若端口被占用，页面要明确提示，不应静默创建第二个端口造成用户困惑。

### 6.2 最小路由

一个 HTTP 服务同时提供页面和四个文件：

| 路由 | 作用 |
|---|---|
| `GET /` 或 `GET /clients` | 返回简洁的四端下载页 |
| `GET /api/clients` | 返回四个平台本地缓存状态、版本和文件大小 |
| `GET /download/android` | 下载已校验的 Android/PAD APK |
| `GET /download/windows` | 下载已校验的 Windows 文件 |
| `GET /download/macos` | 下载已校验的 macOS ZIP |
| `GET /download/linux` | 下载已校验的 Linux 文件 |

下载路由只能读取正式缓存，不能读取 `.part`。对应平台没有已校验缓存时返回 `503` 和中文说明“客户端正在准备，请稍后刷新”，不能返回半个文件。

建议支持：

- 正确的 `Content-Type`；
- `Content-Length`；
- `Content-Disposition`，文件名使用云端 `nickname`；
- HTTP Range，便于大文件断点续传；
- 同一文件并发只读，不在下载过程中持有全局页面锁。

## 7. PAD 页面设计

PAD 页面只保留用户真正需要的信息：

```text
客户端下载

请让下载设备和本机连接同一个局域网
当前 Wi-Fi：KEMI-OFFICE
访问地址：http://192.168.3.63:8686/clients   [复制] [二维码]

Android / PAD   云端 1.4.89+196  本机 1.4.88+195   [更新]
Windows         1.4.89            已准备            [局域网下载]
macOS           1.4.89            下载中 63%        [转圈]
Linux           1.4.89            检查失败/已有缓存 [局域网下载]
```

说明：

- “本机版本”仅在 Android/PAD 行显示，指当前正在运行的 App 版本。
- “云端版本”来自 `plugData.version`。
- 下载中使用转圈动画并在圆圈内显示百分比；无法取得总大小时只显示转圈。
- 本地没有准备好时按钮禁用；准备好后局域网下载按钮可用。
- 不显示 HBBC 地址、云端页面地址、GitHub 地址或“备用下载”。
- 二维码内容始终是当前 PAD 的局域网 HTTP 地址。

浏览器页面同样只显示四个平台、版本、大小和下载按钮，不显示后台接口、CDN URL、MD5 等开发信息。

## 8. PAD 自更新

### 8.1 触发条件

当以下条件同时成立时，在 Android/PAD 行显示“更新”：

- 云端元数据解析成功；
- 云端版本格式有效；
- 云端版本严格高于本机安装版本；
- 云端 APK 的包名与本产品一致；
- 下载并校验后的 APK 签名与当前安装包兼容。

云端版本等于或低于本机版本时不显示更新按钮。版本字段不可解析时提示“云端版本配置异常”，不能误导用户降级。

### 8.2 用户流程

```text
发现新版本
  → Android 行显示“更新”
  → 用户点击
  → 已有正确缓存则直接使用，否则下载
  → MD5、包名、签名校验
  → 调用 Android 系统安装界面
```

本版只提示并由用户点击升级，不做静默安装，不在后台强制替换正在运行的 App。

下载失败时保留当前版本，给出“下载失败，请重试”；MD5、包名或签名不匹配时禁止安装，并记录明确错误原因。

## 9. 同步时机与网络策略

推荐以下三个轻量触发点：

1. PAD 启动后的空闲时段检查一次；
2. 用户进入“客户端下载”页立即检查一次；
3. 用户点击“重试”时检查一次。

没有必要每 60 秒查询。页面长时间停留时可以设置 10 分钟一次的低频刷新，但同一资源下载未完成时不得重复启动下载。

下载应使用 HTTPS，并限制到可信域名：

- 元数据：`www.newlinksz.cn`
- 文件：新智联后台实际使用的受信任 CDN 域名，例如 `cdn.newlink-sz.com`

接口临时失败时采用有限重试和退避，不能无限快速请求。PAD 离线时继续提供已有本地缓存。

## 10. 并发、异常与防止缓存损坏

必须遵守以下规则：

- 同一平台同一时间只允许一个下载任务。
- HTTP 服务只读正式文件，下载线程只写 `.part`。
- 只有 MD5 校验成功后才原子替换正式文件。
- App 被杀、断网、存储不足后遗留的 `.part` 下次启动清理或续传，不能当正式文件提供。
- 下载前检查剩余空间，至少满足目标文件大小加安全余量。
- 元数据更新和文件替换使用平台级互斥锁，四个平台使用四把独立锁。
- 保存最近一次成功元数据和最近一次错误，便于页面说明和调试。
- 不能因为云端出现新版本就先删除旧版本；新版本未验收前始终保留旧版本。

MD5 在这里用于发现下载损坏和确认缓存是否变化。它不是代码签名的替代品：Android 仍依靠 APK 签名，Windows 正式包依靠 Authenticode，macOS 正式包依靠 Developer ID、公证和 stapling。

## 11. 最小实现模块

为了让其他 App 复用，可以拆成四个很小的模块：

```text
CloudAssetResolver
  解析四个固定 plugData 接口，输出 CloudAsset

ClientCacheManager
  比较版本/MD5、下载 .part、校验、原子替换

LanClientServer
  只启动一个 HTTP 服务，只暴露已校验缓存

ClientDistributionController
  管理页面生命周期、进度、PAD 更新提示和错误信息
```

核心模型：

```text
CloudAsset {
  platform,
  resourceName,
  nickname,
  version,
  md5,
  size,
  downloadUrl
}

CachedAsset {
  platform,
  localPath,
  nickname,
  version,
  md5,
  size,
  verifiedAt
}
```

产品差异全部放在配置中：

```json
{
  "projectName": "Common",
  "httpPort": 8686,
  "resources": {
    "android": "KEMI-PAD",
    "windows": "KEMI-Windows",
    "macos": "KEMI-macOS",
    "linux": "KEMI-Linux"
  }
}
```

复制给其他项目时，只改 `applicationId`、端口和四个资源名，不改整体流程。

## 12. 验收清单

### 12.1 云端解析

- 四个固定接口均能返回非空 `url/version/md5/nickname`。
- 实际 URL 使用 HTTPS，能够下载完整文件。
- 后台覆盖同一资源后，固定解析接口不变而字段正确更新。

### 12.2 PAD 缓存

- 首次进入页面能并行准备四个平台文件。
- 下载中断不会覆盖旧文件。
- MD5 错误的文件不会进入正式缓存。
- 云端版本不变且 MD5 相同时不会重复下载。
- 单个平台失败不影响另外三个已准备文件。

### 12.3 局域网 HTTP

- 只启动一个服务、只监听一个端口。
- 同一局域网的手机、Windows、macOS 能打开页面。
- 四个下载按钮拿到对应平台文件，文件名、大小和 MD5 正确。
- 文件未准备好时不会下载 `.part`。
- 离开客户端页面后端口停止监听。

### 12.4 PAD 自更新

- 云端版本高于本机时显示“本机版本 xxxxx”和“更新”。
- 版本相同或更低时不显示更新。
- 点击更新后下载、校验并打开系统安装界面。
- 包名或签名不匹配时禁止安装，当前 App 不受影响。

### 12.5 弱网与离线

- 云端查询失败时仍能下载旧的已校验缓存。
- PAD 更换 Wi-Fi 后页面地址和二维码立即更新。
- Wi-Fi 禁止终端互访时明确提示“当前网络可能开启客户端隔离，请更换允许设备互访的局域网”。

## 13. 从完整方案迁移到精简方案

如果项目原来实现了 HBBC 页面、云备份、manifest 或 SHA256SUMS，精简时按以下边界处理：

1. 保留新智联四个固定 `plugData` 地址。
2. 保留 PAD 下载缓存和现有局域网 HTTP 页面。
3. 删除客户端对 HBBC 云下载页面的依赖和入口。
4. 删除 manifest、SHA256SUMS 的解析和“整批切换”逻辑。
5. 改为每个平台根据自己的 `version + md5` 独立更新。
6. 保留 APK/EXE/macOS/Linux 各自的签名与真实性检查。
7. 确认最终进程内只有一个局域网 HTTP 服务实例。

这份精简版的核心不是减少正确性校验，而是直接使用云端接口已有的版本和 MD5，去掉重复的服务器页面、清单文件和下载通道。

## 14. 一句话交接说明

管理员维护新智联 `Common` 项目中的四个固定客户端资源；PAD 解析接口已有的版本、MD5 和实际 URL，安全下载到本地缓存，再通过唯一的局域网 HTTP 页面提供给同网设备；云端 PAD 版本高于本机时显示用户可点击的升级按钮。
