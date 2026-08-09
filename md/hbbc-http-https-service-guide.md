# KEMI 本地 HTTP 与云端 HTTPS 服务统一说明

> 更新日期：2026-08-09  
> 适用范围：KEMI远程办公、KEMI快传及其他跨平台客户端分发和HBBC账号接入项目

## 1. 结论

KEMI同时保留两种网络服务，但用途不同：

| 服务 | 地址示例 | 用途 | 是否保留 |
|---|---|---|---|
| PAD局域网HTTP | `http://192.168.3.63:8686` | 同一局域网高速下载PAD缓存文件 | 保留 |
| HBBC云端HTTP | `http://kemi-chat.newlinksz.com:21120` | 旧客户端和下载页面兼容入口 | 保留兼容 |
| HBBC云端HTTPS | `https://kemi-chat.newlinksz.com:21121` | 推荐云端下载、公开配置、账号、设备和用量接口 | 默认使用 |
| Newlink HTTPS资源 | `https://www.newlinksz.cn/...`、`https://cdn.newlink-sz.com/...` | 固定资源查询和实际文件下载 | 默认使用 |

不能把PAD局域网HTTP强行改成公网证书HTTPS。PAD地址是动态私网IP，公网证书无法正常匹配；局域网HTTP只用于用户主动打开的短生命周期文件下载。云端域名有固定证书，因此云端默认使用HTTPS。

## 2. 总体架构

```text
同一局域网设备
    │
    ├─ http://PAD-IP:8686/8687
    │      PAD短生命周期HTTP服务器
    │      只提供已完成下载并通过校验的本地缓存
    │
    └─ 互联网不可互访或本地通道失败
           │
           └─ https://kemi-chat.newlinksz.com:21121/<site>
                  HBBC固定HTTPS页面
                    │
                    ├─ 读取hbbc.json站点配置
                    ├─ 每600秒解析Newlink固定接口
                    └─ 302跳转至https://cdn.newlink-sz.com/...实际文件
```

下载优先级：

1. 同局域网并且PAD地址可达：使用PAD本地HTTP，速度最快。
2. PAD地址不可达或不在同一局域网：使用HBBC云端HTTPS固定页面。
3. 前两种均失败：才使用客户端实时解析并校验过的Newlink HTTPS云备份地址。

## 3. PAD本地HTTP

### 3.1 生命周期

- 用户进入“客户端下载”页时启动。
- 用户离开该页面时关闭。
- 服务只监听产品指定端口；远程办公首选`8686`，快传首选`8687`。
- 端口被占用时可使用系统分配端口，页面必须显示真实地址。
- 下载文件必须来自`ready`状态缓存；`.part`、下载中或校验失败文件不得提供。

### 3.2 为什么浏览器显示“不安全”

浏览器对私网HTTP显示“不安全”属于正常行为。页面必须明确提示：

> 当前为同一局域网内的PAD本地下载，文件由当前设备直接提供，不经过互联网。若设备不在同一局域网，请使用云端HTTPS下载。

本地HTTP页面不得承载手机号、密码、短信验证码、支付、管理员登录或任何账号接口。

### 3.3 网络限制

即使PAD和另一设备都能访问互联网，也可能因为访客Wi-Fi、AP隔离、多路由级联或手机热点策略而无法彼此访问。客户端发现本地地址超时后应快速切换到云端HTTPS，不要长时间反复重试。

## 4. HBBC云端HTTP与HTTPS

HBBC同一进程提供两套监听：

```text
HTTP  : 0.0.0.0:21120
HTTPS : 0.0.0.0:21121
```

两者共享同一份站点JSON、下载缓存和稳定路由。HTTPS由HBBC自身终止TLS，不依赖Nginx、Apache或其他反向代理。

### 4.1 页面和下载地址

以KEMI-SEND为例：

```text
推荐页面：
https://kemi-chat.newlinksz.com:21121/kemi-send

兼容页面：
http://kemi-chat.newlinksz.com:21120/kemi-send

推荐稳定下载路由：
https://kemi-chat.newlinksz.com:21121/kemi-send/download/windows
https://kemi-chat.newlinksz.com:21121/kemi-send/download/macos
https://kemi-chat.newlinksz.com:21121/kemi-send/download/linux
https://kemi-chat.newlinksz.com:21121/kemi-send/download/android
```

稳定路由返回302到当前版本的Newlink HTTPS CDN地址。管理员覆盖相同资源名后，客户端页面地址、二维码和代码不变化。

### 4.2 接口分工

| 接口类型 | HTTP 21120 | HTTPS 21121 |
|---|---|---|
| 下载首页和站点页面 | 兼容 | 推荐 |
| `/healthz`、站点发现 | 可用 | 推荐 |
| 稳定下载路由 | 兼容 | 推荐 |
| App公开配置 | 可兼容读取 | 推荐 |
| 手机号、密码、短信验证码 | 禁止 | 必须 |
| 设备绑定、Token、用量心跳 | 禁止 | 必须 |
| 管理后台 | 禁止 | 必须使用账号密码HTTPS登录 |

客户端不得因为HTTPS失败而把账号、密码、验证码或Token请求降级到HTTP。

## 5. 客户端固定配置

新客户端统一使用：

```text
cloud_download_base = https://kemi-chat.newlinksz.com:21121
cloud_site_id       = <后台约定的site id>
last_known_site_url = https://kemi-chat.newlinksz.com:21121/<site path>
```

HTTP `21120`只为已发布的旧客户端下载页面兼容，不作为新客户端默认地址。

账号客户端使用：

```text
account_api_base = https://kemi-chat.newlinksz.com:21121
app_id           = <HBBC后台创建并发布的App ID>
channel_id       = <HBBC后台自动生成并发布的渠道号>
```

## 6. TLS证书和服务器配置

生产证书路径示例：

```text
/etc/kemi-rustdesk/tls/fullchain.pem
/etc/kemi-rustdesk/tls/privkey.pem
```

`hbbc.json`中的HTTPS配置：

```json
{
  "https": {
    "enabled": true,
    "listen": "0.0.0.0:21121",
    "public_base_url": "https://kemi-chat.newlinksz.com:21121",
    "certificate_file": "/etc/kemi-rustdesk/tls/fullchain.pem",
    "private_key_file": "/etc/kemi-rustdesk/tls/privkey.pem"
  }
}
```

要求：

- 私钥必须是未加密、仅服务账号可读的PEM，权限建议`600`。
- 证书链必须包含站点证书和必要中间证书。
- 证书到期前提前续签并重启`kemi-rustdesk-hbbc.service`加载新证书。
- 证书私钥、管理员密码、Token和用户数据不得进入Git、APK、BIN、云盘或公开文档。
- 只更新和重启HBBC，不操作HBBS/HBBR。

## 7. Android网络实现要求

- 使用系统默认CA信任链验证`kemi-chat.newlinksz.com`，禁止信任所有证书。
- 禁止关闭主机名校验，禁止自定义“永远返回true”的`HostnameVerifier`。
- 连接、读取和总超时要分开设置；HTTPS失败应展示明确错误并允许选择PAD本地HTTP。
- 下载先写`.part`，完成后验证大小和SHA-256，再原子改名为正式文件。
- 页面展示的云端二维码必须编码HTTPS地址；本地二维码编码当前真实PAD HTTP地址。
- 日志不得输出账号密码、短信验证码、完整手机号、访问Token、刷新Token或设备私钥。

## 8. 验收命令

```bash
# 本地兼容服务
curl -fsS http://127.0.0.1:21120/healthz

# 公网HTTPS服务
curl -fsS https://kemi-chat.newlinksz.com:21121/healthz
curl -I https://kemi-chat.newlinksz.com:21121/kemi-send
curl -I https://kemi-chat.newlinksz.com:21121/kemi-send/download/windows

# 查看证书域名和有效期
openssl s_client -connect kemi-chat.newlinksz.com:21121 \
  -servername kemi-chat.newlinksz.com </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
```

验收标准：

- HTTP兼容健康检查返回200。
- HTTPS健康检查返回200且证书域名匹配、证书未过期。
- HTTPS站点页面返回200。
- 稳定下载路由返回302，`Location`必须是允许的Newlink HTTPS CDN域名。
- 下载文件大小和SHA-256与发布清单一致。
- HTTP账号/注册请求返回426或拒绝，HTTPS接口正常响应。

## 9. 故障处理

| 现象 | 判断和处理 |
|---|---|
| PAD本地地址打不开 | 检查同一Wi-Fi、AP隔离、访客网络、端口和服务生命周期；切换云端HTTPS |
| HTTPS证书错误 | 检查系统时间、域名、证书链和有效期，禁止让客户端忽略错误 |
| HTTPS页面正常但文件失败 | 检查302 Location白名单、Newlink资源是否上传完整及manifest/SHA256SUMS |
| HTTP正常、HTTPS失败 | 检查21121安全组、防火墙、证书路径和HBBC日志 |
| 账号接口被HTTP拒绝 | 正常安全行为，客户端必须改用HTTPS21121 |

## 10. 新项目复制清单

1. 为项目确定`site id`和`sites[].path`。
2. 提前约定Newlink项目名及各平台固定资源名。
3. 客户端默认写入HTTPS `21121`云端入口。
4. PAD本地HTTP使用项目独立端口，进入页面启动、离开关闭。
5. 二维码同时显示本地HTTP和云端HTTPS，文案说明适用条件。
6. 所有账号和权限接口只走HTTPS。
7. 上线前完成证书、302域名、manifest、SHA-256和四平台实际下载验收。

