# Mac 本地构建 RustDesk Android：华为设备认可与合规分发

> 目标：让一个合法的 RustDesk Android 移植版本拥有稳定、可追溯的发布身份，并通过华为
> AppGallery 的检测和审核后分发。本文不包含绕过“诈骗风险”、病毒扫描、安装拦截或系统
> 权限提示的方法。

## 1. 先明确什么是“被认可”

GitHub、Mac、Android Studio 和 Gradle 都不是 Android 证书颁发机构。它们只负责使用开发者
持有的私钥签名 APK。GitHub Release 下载的 APK 即使签名正确，仍是侧载包，手机仍可能显示
“来自未知来源”的安装提示。

对华为手机而言，可信发布由以下几层组成：

| 层 | 正确做法 | 不能解决的问题 |
|---|---|---|
| APK 身份 | 固定 release keystore 签名，所有后续版本使用同一证书 | 不能保证安全软件一定放行 |
| 安装来源 | 从 AppGallery 正式发布或测试渠道安装 | GitHub 下载通常仍是侧载 |
| 应用信誉 | 实名开发者、真实应用信息、审核、稳定更新链路 | 不能掩盖危险行为 |
| 产品行为 | 明示远控用途、逐次授权、可见通知、隐私政策 | 不能规避恶意软件检测 |

因此，“换成 GitHub 证书”不是解决方案；不存在这种证书。若华为将应用标记为诈骗风险，
必须先判断是普通侧载提示、签名/信誉问题，还是对应用行为的安全判定。

## 2. RustDesk 移植版本的发布前提

1. 确认 RustDesk 代码、名称、图标、包名和第三方组件的许可与商标使用权限；移植版本不得
   冒充官方 RustDesk 或其他机构。
2. 使用自己的稳定包名，例如 `com.example.remotesupport`；不要在正式发布后随意变更。
3. 远程协助仅在设备所有者明确知情和同意的场景下使用。控制前显示受控方确认界面；控制中
   保持明显且不可隐藏的持续通知；结束控制后立即停止会话和相关服务。
4. 不得隐藏图标、静默启动远控、诱导用户授予无障碍/录屏/悬浮窗权限，或下载并执行未随
   应用审核的可执行代码。这些设计会显著提高被安全产品判定为高风险的概率。
5. 准备真实的隐私政策、客服邮箱/电话、主体信息、数据收集说明和删除数据方式。描述中应
   直接写明“远程协助/远程桌面”，不能伪装成优化工具、系统更新或金融服务。

## 3. 与 KEMI 既有签名统一：统一治理，不共用系统测试密钥

`/Users/newlink/kemi/kboard/xtqx.md` 与 `kboard/chip.md` 记录的签名用于 KEMI 受控设备的
平台权限/调试环境：该环境会使用名为 `debug.keystore` 的 AOSP 平台测试签名，并可能关联
系统权限或 overlay 授权。它只适用于由 KEMI 管理的指定设备，**不得用于公开分发的 RustDesk
APK，也不得上传到 AppGallery**。远程控制应用使用平台测试签名或系统权限，会显著增加安全
审核和用户信任风险。

正确的统一方式是统一密钥治理、验签记录和发布流程，而不是把不同安全域的密钥混为一把：

| 密钥类别 | 适用范围 | 能否用于 RustDesk/AppGallery |
|---|---|---|
| 设备系统/平台测试签名 | 受控 RK/定制 Android 设备、系统权限调试 | 否 |
| KEMI 公开 Android release 签名 | 面向用户的普通 Android 应用 | 是；优先使用既有、受控的生产签名 |
| Android debug 签名 | 开发机调试 | 否 |

若 KEMI 已有公开 Android 的生产 release keystore，RustDesk 应复用该**生产签名治理体系**：
由密钥管理员批准该包名、提供本机受控访问，并登记签名 SHA-256。是否让多款公开应用使用同一
证书由密钥管理员决定；若没有明确策略，RustDesk 使用独立生产 key 以降低单点泄露的影响。
任何情况下都不使用 `debug.keystore`、AOSP 测试密钥或平台私钥。

仅当确认不存在可用的 KEMI 公开 release key，并经负责人批准后，才在受控 Mac 上生成一把新的
RustDesk 生产 key。不要在 CI、GitHub Actions runner、仓库、聊天记录或截图中生成/保存私钥
和密码。

无论使用既有 key 还是新 key，都必须：

- 使用高强度、独立的 keystore 密码和 key 密码；
- 创建两份离线加密备份，分别放在不同安全位置；
- 记录 alias、包名和证书 SHA-256，但绝不记录明文密码；
- `.jks`、`keystore.properties`、密码文件和任何导出私钥必须在 `.gitignore` 中；
- 一旦丢失该 keystore，已安装用户无法无缝更新到新签名版本，通常只能卸载重装。

查看用于 AppGallery 登记的证书指纹：

```bash
keytool -list -v -keystore <approved-release-keystore.jks> -alias <approved-alias>
```

保存输出中的 `SHA256:` 指纹到发布记录。不要公开 keystore 或密码。

## 4. 本地 release 签名构建

当前项目若执行 `assembleDebug`，产生的是 debug 签名 APK，不可作为正式分发包。应让 Android
模块的 `release` build type 读取本地、未提交的签名参数，然后执行项目对应的 release 任务。

`keystore.properties` 示例（仅存在本机，禁止提交；值由 KEMI 密钥管理记录提供）：

```properties
storeFile=/absolute/path/to/approved-kemi-public-release.jks
storePassword=<keystore-password>
keyAlias=<approved-alias>
keyPassword=<key-password>
```

Gradle 配置示例（按实际 `app/build.gradle` 或 `build.gradle.kts` 的 DSL 调整）：

```groovy
def signingProperties = new Properties()
def signingFile = rootProject.file('keystore.properties')
if (!signingFile.exists()) {
    throw new GradleException('Missing local release signing configuration')
}
signingFile.withInputStream { signingProperties.load(it) }

android {
    signingConfigs {
        release {
            storeFile file(signingProperties['storeFile'])
            storePassword signingProperties['storePassword']
            keyAlias signingProperties['keyAlias']
            keyPassword signingProperties['keyPassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

构建命令以项目实际模块为准，例如：

```bash
./gradlew :app:assembleRelease
```

若项目由 Flutter/Rust 构建脚本驱动，必须确认最后调用的是 Android 的 `release` variant，不能
只看到 Rust 或 Flutter 编译成功就认为 APK 已正式签名。

每个候选 APK 必须验证签名并记录输出：

```bash
apksigner verify --verbose --print-certs <release.apk>
shasum -a 256 <release.apk>
```

验收条件：`apksigner` 成功、证书 SHA-256 与第 3 节记录完全一致、APK SHA-256 已写入发布
manifest、可在干净测试机安装、启动和完成一次双方确认的远程协助。

## 5. 华为 AppGallery 的正确流程

1. 注册并完成华为开发者实名认证；在 AppGallery Connect 创建 Android 应用。
2. 创建时使用第 2 节确定的正式包名，填写真实开发者主体、应用名称、联系信息和隐私政策。
3. 将第 3 节的 release 证书 SHA-256 指纹登记到项目设置。若使用华为 App Signing，先理解
   密钥托管和更新密钥流程；不要中途切换签名体系。
4. 先上传 release APK 到测试/审核流程，使用 AppGallery 的包检测结果处理问题，再提交正式
   发布。AppGallery Connect 提供应用签名管理和上传包检测能力。
5. 审核材料应主动说明：这是自研或获授权的 RustDesk 移植版本；远程协助的目标用户、双方
   授权流程、持续通知、权限用途、服务器域名、数据处理方式以及客服渠道。
6. AppGallery 审核通过后，让用户从 AppGallery 安装并更新；不要把 GitHub Release 当成面向
   普通用户的主分发渠道。

华为的 Android 接入文档要求开发者生成签名证书、获取 SHA-256 指纹并在 AppGallery Connect
配置；其 App Signing 服务用于管理和保护应用签名密钥，而不是为任意 APK 提供风险豁免。

## 6. 当华为手机显示风险提示时

先记录提示原文和截图，再分类处理：

| 提示类型 | 含义 | 正确处理 |
|---|---|---|
| “允许此来源安装未知应用” | 从浏览器、文件管理器或 GitHub 侧载的系统提示 | 测试机按需授权；正式用户改从 AppGallery 安装 |
| “应用未经验证”一类提示 | 新签名、低信誉或非应用商店分发常见 | 固定 release 签名、走 AppGallery 测试/正式发布并建立更新记录 |
| “诈骗/风险/恶意软件”警告或阻止 | 安全引擎对 APK、权限或行为的判定 | 停止分发该版本，审查权限和远控流程，并走华为开发者支持/审核反馈申诉 |

遇到第三类提示，提交申诉前准备一个可复现证据包：

```text
package name / versionCode / versionName
APK SHA-256
签名证书 SHA-256
下载来源与发布时间
华为设备型号、HarmonyOS/EMUI 版本、提示截图和完整原文
远控双方确认流程的录屏
所有敏感权限及每项权限的业务必要性
隐私政策与客服信息
测试账号、测试步骤、服务端域名和网络说明
对应源码 commit、开源组件清单与许可证
```

不要让用户关闭华为安全防护作为“解决方案”，也不要通过改包名、频繁换签名、混淆提示文本或
隐藏远控能力来规避检测。这些做法会破坏更新链路、增加风险，并可能使审核和安全判定更差。

## 7. 后续版本发布铁律

- 只使用同一正式包名和同一 release keystore；
- 每次发布提高 `versionCode`，保留对应源码 commit、APK SHA-256 和签名 SHA-256；
- 所有新增权限、远控能力、服务端域名和数据收集变化都重新审查并更新隐私说明；
- 在至少一台未安装旧测试包的华为真机上验证 AppGallery 安装、更新、启动、授权、远控确认和
  结束后通知消失；
- 仅在 AppGallery 审核/测试状态和真机结果均真实记录后，才写“已被华为认可”。

## 8. 交接模板（给后续会话）

```text
目标：为 RustDesk Android 移植版本进行合法的华为分发，不绕过安全检测。

正式包名：
release keystore 所在的离线保管位置（不要写密码）：
签名证书 SHA-256：
本次源码 commit：
versionCode / versionName：
release APK 路径：
APK SHA-256：
apksigner verify 结果：
AppGallery Connect 应用 ID / 当前审核状态：
华为设备型号 / 系统版本：
安装来源（AppGallery/测试/侧载）：
风险提示完整原文（如有）：
远控双方确认与持续通知验收结果：
隐私政策 URL：
客服联系方式：
下一步：
```

## 官方参考

- Android release/debug 签名：<https://developer.android.com/studio/publish/app-signing.html>
- 华为 Android 签名与 SHA-256 指纹配置：<https://developer.huawei.com/consumer/en/codelab/HMSPreparation/>
- AppGallery Connect（包检测与 App Signing）：<https://developer.huawei.com/consumer/en/doc/distribution/app/agc-help-overview-0000001100246618>
- 华为 AppGallery 发布接口说明：<https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agcapi-release_app>
