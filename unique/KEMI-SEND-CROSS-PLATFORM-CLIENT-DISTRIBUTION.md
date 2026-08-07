# KEMI快传跨平台客户端下载闭环设计

> 目标：让PAD、Windows、macOS、Linux等多端产品，用一个简单、稳定、可追溯的入口下载对应客户端。本文以`KEMI-SEND`为完整实例，同时可作为其他KEMI项目的通用模板。

## 0. 先说结论：页面地址和资源地址都可以提前约定

KEMI-SEND可以在客户端尚未构建、云端文件尚未上传时，先把页面和六个资源的固定地址全部约定好。后续发布只替换文件，不改客户端、不改hbbc程序，也不改用户访问入口。

这里必须区分四层地址：

| 地址层级 | 由谁控制 | 能否提前确定 | KEMI-SEND示例 |
|---|---|---|---|
| hbbc云端页面 | `hbbc.json`中的`public_base_url + sites[].path` | 可以 | `http://kemi-chat.newlinksz.com:21120/kemi-send` |
| 各平台稳定下载路由 | JSON中的页面`path + assets[].id` | 可以 | `/kemi-send/download/windows` |
| Newlink固定资源查询地址 | 管理员后台确定的`projectName + name`；JSON保存同一组值 | 可以，推荐开发前约定 | `https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-Windows` |
| 本次文件实际CDN地址 | 管理员上传文件后由Newlink后台在plugData的`data[0].url`返回 | 不能可靠写死 | `https://cdn.newlink-sz.com/...` |

因此，“每个资源的URL由管理员后台确定”有两种交接方式：

1. **推荐：提前约定。** 产品、开发和管理员先定好`Common`项目及六个固定`name`；开发可以提前写好hbbc JSON、网页入口和客户端发现配置。管理员以后始终覆盖相同name；首次上传和回读验收成功后，服务器管理员只需把`resolve_enabled`改为`true`，不需要重编hbbc。
2. **兼容：上传后反馈。** 管理员先在后台建立资源，然后把六个固定plugData查询地址交给开发；开发从地址中提取`projectName`和`name`填入JSON。仍然不把本次动态CDN长地址写进JSON。

建议KEMI-SEND直接采用第一种方式，提前冻结以下约定：

```text
云端页面：http://kemi-chat.newlinksz.com:21120/kemi-send
后台项目：Common
资源name：
  KEMI-Send-PAD
  KEMI-Send-Windows
  KEMI-Send-macOS
  KEMI-Send-Linux
  KEMI-Send-SHA256SUMS
  KEMI-Send-release-manifest
```

文件未上传前，JSON保持`resolve_enabled:false`，页面地址仍可提前访问并作为占位页；六项上传并验收通过后改为`true`。以后更新版本只覆盖同名资源，hbbc每600秒解析最新实际地址。

## 1. 最终用户看到什么

KEMI快传的“客户端下载”页只需要让普通用户理解两个主入口：

1. **同局域网下载**：从当前PAD的本地HTTP服务下载，速度快，适合同一Wi-Fi且设备允许互访的现场。
2. **云端下载**：打开固定地址`http://kemi-chat.newlinksz.com:21120/kemi-send`，不要求下载设备能访问PAD的局域网IP。

页面可以保留第三种“云备份下载”，但必须标注：

> 仅作为上面两种下载均失效情况下的备案。

“云端下载”经过hbbc固定页面，“云备份下载”绕过hbbc、直接使用PAD实时解析的新智联HTTPS文件地址。两者最终可能来自同一个Newlink CDN，因此云备份是**绕过hbbc的应急路径**，不是完全独立的第二家云存储。

建议界面：

```text
KEMI快传 - 客户端下载

┌─────────────────────────────────────────────────────────┐
│ 方式一：同局域网下载     │ 方式二：云端下载             │
│ 当前Wi-Fi：KEMI-T1      │ 无需与PAD处于同一局域网      │
│ http://PAD-IP:8687      │ kemi-chat...:21120/kemi-send │
│ 地址本身可点击           │ 地址本身可点击               │
└─────────────────────────────────────────────────────────┘

可下载客户端
Windows   [从PAD下载] [云备份下载]  仅作为上面两种下载均失效情况下的备案
macOS     [从PAD下载] [云备份下载]  仅作为上面两种下载均失效情况下的备案
Linux     [从PAD下载] [云备份下载]  仅作为上面两种下载均失效情况下的备案
PAD       [从PAD下载] [云备份下载]  仅作为上面两种下载均失效情况下的备案
```

如果KEMI远程桌面和KEMI快传可能安装在同一台PAD上，KEMI快传不要固定抢占远程桌面的`8686`。建议KEMI快传优先使用`8687`，占用时绑定系统分配端口，并始终以页面实际显示地址为准。

## 2. 完整架构

```text
                   ┌─────────────────────────────┐
                   │ KEMI-SEND同一源码与版本批次 │
                   └──────────────┬──────────────┘
                                  │ 构建、签名、验收
                 ┌────────────────┼────────────────┐
                 ▼                ▼                ▼
          PAD/Android APK   Windows EXE     macOS ZIP / Linux AppImage
                 └────────────────┬────────────────┘
                                  │ 生成SHA256SUMS与release-manifest
                                  ▼
                         六个完整发布文件
                                  │
                     管理员按固定顺序上传Common项目
                                  ▼
              Newlink固定plugData接口 → 动态HTTPS CDN地址
                                  │
                    hbbc每600秒解析、检查并保存缓存
                                  ▼
           http://kemi-chat.newlinksz.com:21120/kemi-send
                                  │
                    稳定平台路由302到当前HTTPS文件
                                  │
              ┌───────────────────┴───────────────────┐
              ▼                                       ▼
      KEMI快传客户端云端入口                 PAD后台同步到私有缓存
                                                      │
                                           用户进入客户端下载页
                                                      ▼
                                         PAD本地HTTP（建议8687）
```

核心原则：

- 客户端、二维码和文档只保存稳定入口，不保存每次上传后变化的CDN长地址。
- hbbc JSON控制云端HTTP页面、站点ID、页面后缀、平台路由，并描述管理员后台已经确定或提前约定的`projectName`和固定`name`；不写某一批次的版本、哈希或动态CDN URL。
- 日常升级只替换六个云端项目；hbbc二进制、JSON、客户端页面地址都不需要更新。
- 四端文件未全部构建并验收前，不更新正式manifest，不让用户看到半成品批次。
- 本地HTTP与云端HTTP互补，任何一条链路失败都不能破坏另一条链路。

## 3. 四个角色和交接物

同一个人可以承担多个角色，但交接内容必须分清：

| 角色 | 负责内容 | 交付物 |
|---|---|---|
| KEMI快传构建人员 | 冻结版本、构建四端、签名、基础启动验证 | 四个平台客户端 |
| 发布人员 | 计算大小和SHA-256，生成两份清单 | 六个发布文件及发布记录 |
| Newlink云后台管理员 | 与项目提前约定或在后台建立六个固定name，并在`Common`项目覆盖对应文件 | 六个固定plugData查询地址、上传完成时间；若已提前约定，只需确认未变 |
| hbbc服务器管理员 | 首次合并JSON、检查配置、只重启hbbc | `/kemi-send`页面与四个稳定下载路由 |
| KEMI快传客户端开发 | 接入本地HTTP、云端发现API和下载UI | PAD/桌面端“客户端下载”功能 |

云后台账号密码只由管理员保管，不进入源码、JSON、文档、聊天记录或构建日志。

## 4. KEMI快传一次发布应给出哪些文件

以下以版本`1.0.0+1`为例。建议实际客户端文件名带版本号，云后台的固定name不带版本号。

```text
release/
├── KEMI-Send-PAD-1.0.0+1.apk
├── KEMI-Send-Windows-x64-1.0.0.exe
├── KEMI-Send-macOS-arm64-1.0.0.zip
├── KEMI-Send-Linux-x86_64-1.0.0.AppImage
├── KEMI-Send-SHA256SUMS.txt
└── KEMI-Send-release-manifest.json
```

为什么实际文件名建议带版本号：管理员仍然覆盖同一个固定云端项目，但plugData返回的`nickname`会变成新版本文件名。hbbc会要求`nickname`与manifest中的`file`完全一致。四个客户端上传到一半时，新文件名与旧manifest不一致，hbbc会继续保留上一批完整缓存；manifest最后上传后才整体切换到新批次，避免新旧版本短暂混用。

四端构建最低检查：

| 平台 | 文件 | 最低检查 |
|---|---|---|
| PAD/Android | APK | applicationId、versionName/versionCode、ABI、固定签名、非debuggable、启动 |
| Windows | EXE | PE架构、产品版本、来源commit、便携/安装行为、启动；正式外发建议Authenticode签名 |
| macOS | ZIP内App | CFBundle版本、CPU架构、深层签名；正式外发需Developer ID与公证 |
| Linux | AppImage | ELF架构、AppImage魔数、可执行权限、启动 |

同一发布批次必须记录：产品版本、构建号、源码commit、构建机或CI run ID、文件大小、SHA-256、签名状态和实机验收状态。构建成功不等于真实平台功能已验收。

## 5. 两份清单怎么生成

### 5.1 SHA256SUMS

`KEMI-Send-SHA256SUMS.txt`必须只列四个平台文件，文件名与实际上传文件名逐字一致：

```text
1111111111111111111111111111111111111111111111111111111111111111  KEMI-Send-PAD-1.0.0+1.apk
2222222222222222222222222222222222222222222222222222222222222222  KEMI-Send-Windows-x64-1.0.0.exe
3333333333333333333333333333333333333333333333333333333333333333  KEMI-Send-macOS-arm64-1.0.0.zip
4444444444444444444444444444444444444444444444444444444444444444  KEMI-Send-Linux-x86_64-1.0.0.AppImage
```

上面的哈希仅为格式示例，发布时必须替换为真实文件哈希。每行格式固定为：

```text
<64位小写SHA-256><两个空格><文件名>
```

### 5.2 release-manifest

`KEMI-Send-release-manifest.json`示例：

```json
{
  "schema_version": 1,
  "channel": "stable",
  "release_batch": "kemi-send-1.0.0+1",
  "generated_at": "2026-08-04T14:00:00+08:00",
  "source_commit": "替换为完整源码commit",
  "targets": [
    {
      "id": "android",
      "version": "1.0.0+1",
      "architecture": "arm64-v8a",
      "file": "KEMI-Send-PAD-1.0.0+1.apk",
      "size": 12345678,
      "sha256": "1111111111111111111111111111111111111111111111111111111111111111"
    },
    {
      "id": "windows",
      "version": "1.0.0",
      "architecture": "x86_64",
      "file": "KEMI-Send-Windows-x64-1.0.0.exe",
      "size": 23456789,
      "sha256": "2222222222222222222222222222222222222222222222222222222222222222"
    },
    {
      "id": "macos",
      "version": "1.0.0+1",
      "architecture": "arm64",
      "file": "KEMI-Send-macOS-arm64-1.0.0.zip",
      "size": 34567890,
      "sha256": "3333333333333333333333333333333333333333333333333333333333333333"
    },
    {
      "id": "linux",
      "version": "1.0.0",
      "architecture": "x86_64",
      "file": "KEMI-Send-Linux-x86_64-1.0.0.AppImage",
      "size": 45678901,
      "sha256": "4444444444444444444444444444444444444444444444444444444444444444"
    }
  ]
}
```

hbbc当前强制读取每个target的`id`、`version`、`file`、`size`和`sha256`。其他发布追溯字段可以保留，但不能缺少这些必需字段。

发布前本地必须检查：

1. manifest四个`id`为`android/windows/macos/linux`，不重复、不缺失。
2. manifest的`file`与磁盘文件名完全一致。
3. manifest的`size`与实际字节数一致。
4. manifest与SHA256SUMS中的SHA-256一致。
5. 两份清单都是UTF-8、非空、可解析。

## 6. 管理员上传云后台的六个固定项目

管理入口：<https://www.newlinksz.cn/screensaver/main/configPlug/Common>。

项目固定使用`Common`。后台“固定name”与本地选择的文件是两个概念：固定name永远不变，本地文件名随版本变化。

`projectName=Common`和六个`name`最好在首次开发前由管理员、产品和开发共同确认。这样下面六个plugData查询URL在任何文件上传前就已经可以写入hbbc JSON、联调文档和自动验收脚本。首次上传是在这些固定资源名下填入真实文件，不是在发布时临时发明新URL。

| 上传顺序 | Common固定name | 本地选择文件 |
|---:|---|---|
| 1 | `KEMI-Send-PAD` | `KEMI-Send-PAD-1.0.0+1.apk` |
| 2 | `KEMI-Send-Windows` | `KEMI-Send-Windows-x64-1.0.0.exe` |
| 3 | `KEMI-Send-macOS` | `KEMI-Send-macOS-arm64-1.0.0.zip` |
| 4 | `KEMI-Send-Linux` | `KEMI-Send-Linux-x86_64-1.0.0.AppImage` |
| 5 | `KEMI-Send-SHA256SUMS` | `KEMI-Send-SHA256SUMS.txt` |
| 6（最后） | `KEMI-Send-release-manifest` | `KEMI-Send-release-manifest.json` |

必须最后上传manifest。任何客户端文件或SHA清单上传失败时，停止发布，不上传新manifest。

管理员上传完成后交给开发/验证人员的是下面六个**固定查询地址**，不是网页后台地址，也不需要手工抄写每次变化的CDN URL。如果这些地址已提前约定，管理员上传后只需确认项目和name没有变，并给出完成时间：

```text
https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-PAD
https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-Windows
https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-macOS
https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-Linux
https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-SHA256SUMS
https://www.newlinksz.cn/screensaver/api/plugData?projectName=Common&name=KEMI-Send-release-manifest
```

管理员的最小交接信息：

```text
项目：Common
发布批次：kemi-send-1.0.0+1
六个固定name：已按顺序覆盖
manifest完成时间：2026-08-04 14:30:00 +08:00
失败项：无
```

不要传账号密码。管理员负责后台固定资源的建立和覆盖；动态`cdn.newlink-sz.com` URL由Newlink后台在上传后生成，由开发流程和hbbc通过固定接口自行解析。

## 7. 上传后开发流程检查什么

每个plugData响应应满足：

- `code == 0`；
- `data`恰好一项；
- `data[0].name`等于请求的固定name；
- `data[0].nickname`等于manifest中的对应文件名；
- `data[0].url`为HTTPS且主机严格等于`cdn.newlink-sz.com`；
- `data[0].md5`为32位十六进制；
- 下载回来的真实文件大小、SHA-256与manifest一致，MD5与plugData一致。

需要特别说明：当前hbbc为了保持轻量，不会把四个大文件全部下载到服务器重新计算哈希。它会交叉检查manifest、SHA256SUMS、plugData文件名、URL白名单及MD5格式，并把SHA-256/MD5展示给用户；**实际大文件的大小、SHA-256和MD5回读验证仍属于发布流程的责任**。

任何一项不一致都不能启用首次发布，也不能宣布新版本完成。

## 8. 首次生成KEMI-SEND的hbbc JSON

服务器现有`hbbc.json`已经包含`kemi-desk`，不能为了增加KEMI-SEND覆盖掉其他站点。应在现有`sites`数组中合并下面的站点对象：

```json
{
  "id": "kemi-send",
  "enabled": true,
  "resolve_enabled": true,
  "path": "/kemi-send",
  "title": "KEMI文件快传客户端下载",
  "subtitle": "无需与PAD处于同一局域网，请选择对应系统下载。",
  "project_name": "Common",
  "manifest_plug_name": "KEMI-Send-release-manifest",
  "checksums_plug_name": "KEMI-Send-SHA256SUMS",
  "assets": [
    {
      "id": "windows",
      "manifest_id": "windows",
      "plug_name": "KEMI-Send-Windows",
      "title": "Windows（x64）",
      "detail": "下载后双击安装包",
      "icon": "windows",
      "recommended": true
    },
    {
      "id": "macos",
      "manifest_id": "macos",
      "plug_name": "KEMI-Send-macOS",
      "title": "macOS（Apple芯片）",
      "detail": "下载后拖入应用程序文件夹",
      "icon": "macos"
    },
    {
      "id": "linux",
      "manifest_id": "linux",
      "plug_name": "KEMI-Send-Linux",
      "title": "Linux（x86_64）",
      "detail": "下载AppImage后按系统提示运行",
      "icon": "linux"
    },
    {
      "id": "android",
      "manifest_id": "android",
      "plug_name": "KEMI-Send-PAD",
      "title": "PAD / Android",
      "detail": "下载后按系统提示安装",
      "icon": "android"
    }
  ]
}
```

首次文件尚未上传时应保持`resolve_enabled:false`，页面可以作为占位站点存在，但平台下载路由返回503。六项上传并回读通过后再改为`true`。

JSON中不应出现：

- 某个版本号；
- 某次上传产生的CDN URL；
- 文件大小、SHA-256或MD5；
- 云后台账号密码；
- RustDesk服务器私钥。

这些动态数据来自云端两份清单和plugData响应。

## 9. 管理员如何让JSON对应到访问网址

JSON不是上传到Newlink云后台的第七个文件，也不能通过网页URL直接上传。正确流程是由具备服务器权限的管理员部署到hbbc配置路径：

```text
/etc/kemi-rustdesk/hbbc.json
```

更新前备份现有配置，合并`kemi-send`站点后执行：

```bash
sudo /opt/kemi-rustdesk-server/bin/hbbc \
  --config /etc/kemi-rustdesk/hbbc.json \
  --check-config

sudo systemctl restart kemi-rustdesk-hbbc.service
sudo systemctl status kemi-rustdesk-hbbc.service --no-pager
sudo journalctl -u kemi-rustdesk-hbbc.service -n 100 --no-pager
```

只重启`kemi-rustdesk-hbbc.service`，不重启`hbbs`、`hbbr`，不重新运行全量安装脚本。

云端HTTP页面地址完全由JSON控制。`public_base_url + sites[].path`自动形成正式页面地址，因此在KEMI-SEND客户端编译前就可以确定：

```text
http://kemi-chat.newlinksz.com:21120/kemi-send
```

自动形成四个平台稳定下载地址：

```text
http://kemi-chat.newlinksz.com:21120/kemi-send/download/windows
http://kemi-chat.newlinksz.com:21120/kemi-send/download/macos
http://kemi-chat.newlinksz.com:21120/kemi-send/download/linux
http://kemi-chat.newlinksz.com:21120/kemi-send/download/android
```

这些稳定路由也由JSON中的`sites[].path`和`assets[].id`生成，并返回302到本次Newlink HTTPS真实文件。云盘覆盖更新后，稳定路由、二维码和客户端代码都不改变。

JSON与管理员后台资源的对应关系如下：

```text
JSON project_name              ← plugData参数 projectName
JSON manifest_plug_name        ← 发布清单资源name
JSON checksums_plug_name       ← SHA清单资源name
JSON assets[].plug_name        ← 各平台客户端资源name
JSON sites[].path              → hbbc云端页面后缀
JSON assets[].id               → hbbc平台下载路由后缀
```

管理员若采用提前约定的六个name，JSON可以提前定稿；管理员若在后台使用了另一组name，只修改JSON对应字段并检查配置即可，不需要重新编译hbbc。

## 10. hbbc验证清单

```bash
curl http://127.0.0.1:21120/healthz
curl http://127.0.0.1:21120/api/v1/sites
curl http://127.0.0.1:21120/api/v1/sites/kemi-send
curl -I http://kemi-chat.newlinksz.com:21120/kemi-send
curl -I http://kemi-chat.newlinksz.com:21120/kemi-send/download/windows
curl -I http://kemi-chat.newlinksz.com:21120/kemi-send/download/macos
curl -I http://kemi-chat.newlinksz.com:21120/kemi-send/download/linux
curl -I http://kemi-chat.newlinksz.com:21120/kemi-send/download/android
```

验收标准：

- `/healthz`返回200；
- `/api/v1/sites`中`id=kemi-send`、`ready=true`；
- `/kemi-send`返回200并显示四个平台、真实版本、大小和校验值；
- 四个`/download/<id>`均返回302；
- 每个Location都是正确平台的`https://cdn.newlink-sz.com/...`；
- 实际下载文件与发布目录中的原文件SHA-256一致。

## 11. 把网址反馈给KEMI快传客户端

推荐给客户端的不是四个动态CDN地址，而是下面三项稳定配置：

```text
cloud_download_base = http://kemi-chat.newlinksz.com:21120
cloud_site_id       = kemi-send
last_known_site_url = http://kemi-chat.newlinksz.com:21120/kemi-send
```

客户端启动或进入“客户端下载”页时：

1. 请求`http://kemi-chat.newlinksz.com:21120/api/v1/sites`；
2. 在`sites`数组按稳定`id=kemi-send`查找；
3. 读取返回的`url`和`ready`；
4. `ready=true`时显示“云端下载”；
5. API暂时失败时使用`last_known_site_url`，不要因为发现API短时不可用隐藏入口；
6. 永远不要把API返回的各平台`actual url`长期保存到客户端。

这样未来管理员修改`sites[].path`时，支持发现API的客户端无需升级；旧客户端仍可使用最后已知地址。若没有迁移需求，也可以长期保持`/kemi-send`不变。

## 12. KEMI快传本地HTTP服务

PAD端建议实现与KEMI远程桌面相同的短生命周期服务，但使用独立首选端口`8687`：

```text
GET /                                  下载首页
GET /health                            返回ok
GET /download/<白名单文件名>           发送已验证本地缓存
```

生命周期：

- 用户进入“客户端下载”页时启动；
- 离开页面、Activity销毁或应用退出时关闭；
- 端口被占用时使用系统分配端口；
- 页面必须显示真实IP、端口和Wi-Fi名称，二维码使用实际地址；
- 只绑定下载白名单，不支持目录浏览、任意路径、上传、删除或命令执行；
- `.part`、未校验文件和旧版本伪装文件绝不对外提供。

PAD后台同步状态机：

```text
missing → downloading(.part，可续传) → verifying → ready
                      └─失败→ error → 保留最后成功版本
```

本地网页顶部用一个统一外边框显示两个主入口，宽屏中间用分隔线并排，窄屏在同一外框内上下排列。地址本身可点击，不再增加“打开备用地址”或重复复制按钮：

1. 当前PAD本地地址，例如`http://192.168.3.86:8687`；
2. hbbc云端页`http://kemi-chat.newlinksz.com:21120/kemi-send`。

每个平台卡片显示：

- “从PAD下载”：只提供`ready`缓存；
- “云备份下载”：PAD实时解析并通过白名单的Newlink HTTPS地址；
- 提示“云备份下载仅作为上面两种下载均失效情况下的备案”。

## 13. APK不会把自己无限打包

KEMI-SEND APK内不嵌入四个平台安装包。正确方式是：

- App安装包只包含程序代码和页面逻辑；
- 安装后由后台任务下载四端客户端到App私有缓存；
- 当前云端Android版本与已安装版本完全一致时，可以读取Android的`applicationInfo.sourceDir`作为当前APK来源，并核对大小和SHA-256；
- 云端Android版本更高时，只缓存新APK文件，不把它重新塞入正在运行的APK；
- 卸载App会清除私有缓存，重装后重新同步。

因此不会出现“APK包含自己、自己又包含自己”的无限递归。

## 14. 初次接入与日常升级的区别

### 初次接入KEMI-SEND

1. 提前约定hbbc页面地址、`site_id/path`、Newlink项目和六个固定name。
2. 先生成/合并`kemi-send`站点JSON，资源未就绪时保持`resolve_enabled:false`。
3. 建立四端统一版本规则和固定签名。
4. 构建并验收四端客户端。
5. 生成SHA256SUMS和release-manifest。
6. 管理员按顺序上传六个固定云端资源，确认固定查询地址未变。
7. 开发流程回读六个plugData地址并验证真实文件。
8. 将JSON的`resolve_enabled`改为`true`，服务器管理员检查JSON并只重启hbbc。
9. 验证页面、API和四个302路由。
10. KEMI快传客户端使用提前约定的`base + site_id + last_known_url`，验证本地HTTP、云端页和云备份三条路径。

### 以后发布新版本

1. 构建新的四端完整批次。
2. 生成新两份清单。
3. 管理员仍覆盖相同六个固定name，manifest最后。
4. hbbc最多600秒自动解析新资源；需要立即刷新时只重启hbbc。
5. 客户端、二维码、hbbc JSON和页面URL都不需要修改。

只有增加平台、修改页面标题、改变云项目或改固定name时才更新hbbc JSON。

## 15. 失败场景与回退

| 故障 | 用户路径 | 系统行为 |
|---|---|---|
| 下载设备不能访问PAD IP | 云端下载 | 打开`/kemi-send` |
| hbbc暂时不可达 | 同局域网下载 | PAD缓存不受影响 |
| PAD IP与hbbc都不可达 | 云备份下载 | 直接打开PAD已解析的Newlink HTTPS地址 |
| Newlink新批次上传不完整 | 上一稳定版本 | hbbc应保留最后成功缓存，不删除旧站点数据 |
| 新manifest或SHA错误 | 上一稳定版本 | 新批次拒绝切换并记录日志 |
| 某平台尚未构建完成 | 不发布新manifest | 其他平台也不冒充新批次 |
| 本地下载中断 | 稍后续传 | 保留`.part`，不暴露临时文件 |
| 云端发现API失败 | 最后已知页面 | 使用`last_known_site_url` |

如果hbbc和云备份都使用Newlink CDN，Newlink整体故障时两者会同时失败。需要真正的异构容灾时，应再增加另一家对象存储或GitHub Release，但它必须使用同一manifest和SHA-256门禁，不能提供未经校验的替代文件。

## 16. 发布门禁清单

### 构建人员

- [ ] 四个平台来自同一发布批次和明确源码commit。
- [ ] 版本、架构、签名、文件大小和SHA-256已记录。
- [ ] 四端至少完成目标平台启动检查。
- [ ] 六个发布文件完整生成。

### 云后台管理员

- [ ] 当前项目为`Common`。
- [ ] 四个客户端固定name全部覆盖成功。
- [ ] SHA256SUMS倒数第二上传。
- [ ] release-manifest最后上传。
- [ ] 只反馈固定查询地址和完成时间，不反馈账号密码。

### 开发/验证人员

- [ ] 六个plugData响应的name、nickname、MD5、HTTPS域名正确。
- [ ] 下载回读四个真实文件，大小、SHA-256、MD5正确。
- [ ] manifest与SHA256SUMS完全一致。
- [ ] hbbc JSON不含动态URL、版本和密钥。

### hbbc服务器管理员

- [ ] 合并而不是覆盖其他站点。
- [ ] `--check-config`通过。
- [ ] 只重启hbbc，未影响hbbs/hbbr。
- [ ] `/api/v1/sites`中`kemi-send ready=true`。
- [ ] 页面和四个平台稳定路由全部通过。

### KEMI快传客户端

- [ ] 客户端页显示真实本地地址和云端地址。
- [ ] 本地服务进入页面启动、离页关闭。
- [ ] 本地只提供已校验文件。
- [ ] 云端入口按`site_id`发现，失败使用最后已知URL。
- [ ] 云备份说明清楚，不把动态CDN URL作为长期配置。
- [ ] Android自包复用不会造成APK嵌套。

## 17. 可复用到其他项目的命名规则

新增项目时只替换`KEMI-Send`前缀和站点ID：

```text
站点ID：kemi-<product>
页面路径：/kemi-<product>
四端固定name：
  KEMI-<Product>-PAD
  KEMI-<Product>-Windows
  KEMI-<Product>-macOS
  KEMI-<Product>-Linux
清单固定name：
  KEMI-<Product>-SHA256SUMS
  KEMI-<Product>-release-manifest
```

一个项目六个固定云端name、一个hbbc站点ID、一个稳定页面URL。客户端只认识站点ID和稳定入口，平台文件如何更新由manifest、plugData和hbbc共同完成。这就是多平台客户端下载保持简单、可升级和可维护的关键。
