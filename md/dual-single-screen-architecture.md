# KEMI 双屏浏览器：双屏/单屏技术架构与验收

更新日期：2026-08-11<br>
当前正式版本：`1.2.1`

面向其他项目的失败方案、OEM 差异、EGL/Surface/输入细节和测试避坑清单见
[`multi-display-browser-lessons.md`](multi-display-browser-lessons.md)。

## 1. 产品模式

KEMI 双屏浏览器基于 Iceraven/Fenix 与 GeckoView，不依赖系统 WebView。应用提供两种入口：

- 普通点击应用图标：默认进入双屏模式。
- 长按应用图标选择“单屏模式”：只在发起操作的当前显示运行。

两种模式使用同一套 KEMI 主页、知识站收藏、地址栏、工具栏、严格跟踪保护和网页输入能力。

## 2. 硬件与显示角色

目标车机为 Android 12 / arm64，逻辑显示 ID 与页面角色固定如下：

| 逻辑显示 | 分辨率 | 双屏角色 | Activity |
| --- | --- | --- | --- |
| Display 2 | `1920×1280@60Hz` | 连续网页顶部 `y=0…1279` | `DualScreenTopActivity` |
| Display 0 | `1920×1280@60Hz` | 连续网页底部 `y=1280…2559` | `DualScreenBrowserActivity` |

双屏模式的目标不是镜像，也不是让两个浏览器实例追踪滚动位置，而是把两块物理屏组合成一个
`1920×2560` 的连续逻辑视口。两块屏均固定横屏，应用运行期间不跟随传感器旋转。

## 3. 双屏渲染架构

双屏模式只有一个网页运行实例：

1. `DualScreenCoordinator` 创建一个 `GeckoSession`，网页 DOM、JavaScript、视频、Canvas、Cookie、历史和缩放状态只存在一份。
2. Gecko 以 `1920×2560` 逻辑尺寸渲染到一个共享 `SurfaceTexture`。
3. `DualScreenSharedCompositor` 在同一 EGL 合成线程中取得一张 Gecko 帧。
4. 合成器把帧的上半区提交给 Display 2 的 Surface，把下半区提交给 Display 0 的 Surface。
5. Surface 重建或 `singleInstance` Activity 复用时，重新绑定存活 Surface；缓存首帧可以立即恢复副屏，避免黑屏。

这套结构从根本上消除了“双 Session 互相设置滚动位置”造成的反馈环、抖动和动态内容分叉。

## 4. 输入与联动操作

- Display 2 的触摸坐标直接进入共享 `PanZoomController`。
- Display 0 的触摸 Y 坐标统一增加 `1280`，映射到同一 `1920×2560` 页面下半区。
- 任一屏发起滚动时，Gecko 只处理一次手势；另一屏显示同一新帧的相邻裁切区域，不会反向发送滚动。
- 两屏的后退、前进、刷新、主页、地址跳转和加载进度都操作同一个 GeckoSession。
- `GeckoInputSurface` 把 `InputConnection`、硬键和输入法焦点绑定到实际触摸的 Activity，因此两块屏的网页输入框都可弹出软键盘。
- 鼠标右键显示通用网页菜单；车机把右键降级为 Android Back 时，只有确实存在近期鼠标事件才走菜单逻辑。网页选区通过系统 `ClipboardManager` 写入主剪贴板，供输入法和其他 App 使用。

当前正式验收覆盖“一次由一块屏完成一个手势”。两块触摸屏同时按下时的跨 Activity
pointer stream 所有权锁尚未实现，不属于 `1.2.1` 的承诺；需要该能力的项目应从首个
`ACTION_DOWN` 到对应的 `ACTION_UP/ACTION_CANCEL` 显式锁定手势来源。

## 5. 从任意屏启动双屏模式

### 5.1 原故障

旧版本的 LAUNCHER alias 直接指向 `DualScreenBrowserActivity`。用户在 Display 2 点击图标时，车机 ROM
先把这个任务绑定到 Display 2；Activity 随后又尝试把同一任务搬到 Display 0。WindowManager 因窗口和任务的
显示归属冲突而隐藏两个任务，表现为副屏启动后立即“闪退”，但 logcat 中没有 Java `FATAL EXCEPTION`。

### 5.2 1.2.1 修复

LAUNCHER alias 现在指向轻量的 `DualScreenLaunchActivity`：

1. 路由 Activity 使用独立 task affinity，不创建 Gecko 或网页 Surface。
2. 从 Display 0 启动时，直接使用 Application Context 在 Display 0 创建总控。
3. 从 Display 2 启动时，先 `finishAndRemoveTask()` 完全移除副屏启动任务。
4. 等待 400ms 让车机 WindowManager 释放副屏任务归属。
5. 使用 `ActivityOptions.launchDisplayId=0` 创建 D0 总控；总控再创建 D2 顶部 Activity。
6. 创建配对 Activity 的短暂内部切屏期间屏蔽 `onUserLeaveHint`，避免 ROM 把内部任务切换误判为用户离开。

因此无论用户在哪块屏点击普通图标，最终结构都稳定收敛为 D0 总控 + D2 顶部。

## 6. 单屏模式

`SingleScreenBrowserActivity` 是独立的单屏入口：

- 在 Display 0 发起，就只在 Display 0 运行。
- 在 Display 2 发起，就只在 Display 2 运行。
- 使用一张 `1920×1280` Gecko 帧，不创建另一块屏的 Activity。
- 与双屏模式共用 KEMI 主页、工具栏、地址输入、收藏、隐私策略和鼠标/剪贴板功能。
- 从单屏切换到双屏或反向切换时，协调器会结束旧会话并建立与目标模式匹配的新会话，避免 Surface 或页面状态串用。

## 7. 生命周期与退出

- 双屏两 Activity 由 `DualScreenCoordinator` 统一持有弱引用和共享会话。
- 任一屏系统返回、工具栏退出或真实离开都会调用 `exitAll()`，先关闭共享 Gecko/EGL 资源，再同时结束 D0/D2 任务。
- 配对 Activity 的内部创建不视为退出。
- 首次系统返回不再被错误识别为鼠标右键：1.2.1 在计算鼠标活动时间差前先验证时间戳已经初始化，避免 `Long.MIN_VALUE` 减法溢出。
- `onDestroy` 会通知配对成员；某一屏异常结束时，另一屏不会留下孤立窗口或黑屏。

## 8. 本地构建与可复现源码

- 固定上游提交见 `build/upstream.env`。
- 所有产品改动按 `patches/series` 重放；跨屏启动修复位于
  `patches/0030-route-launches-across-displays.patch`。
- 本地开发构建：`./scripts/build-local.sh`。
- 正式构建：`./scripts/build-release.sh 1.2.1`。
- 正式签名只从仓库外 `/Users/kemi/coding/priv/pem/kemi-unified-release` 读取，私钥和密码不得提交 GitHub。
- 正式 APK：`bin/DualScreenBrowser-v1.2.1-arm64-release.apk`。
- APK SHA-256：`d4d88472c2d1155dad63d6263afe1229ba81ee038988d34f0fb2221b59484f5c`。
- 签名证书 SHA-256：
  `C3:09:13:B0:C3:5B:84:50:F6:49:61:F5:B3:C7:6C:E8:30:4A:F0:76:0C:59:1E:40:BC:45:82:59:8C:38:8D:04`。

## 9. 63 真机最终验收

设备：`192.168.3.63:5555`，安装版本 `1.2.1`。

| 场景 | 通过条件 | 结果 |
| --- | --- | --- |
| Display 0 普通图标 | D0 总控 + D2 顶部，收到 `1920×2560` 首帧 | 通过 |
| Display 2 普通图标 | 先释放 D2 路由任务，再形成 D0 总控 + D2 顶部 | 通过 |
| Display 0 单屏 | 只有 D0 `SingleScreenBrowserActivity`，收到 `1920×1280` 首帧 | 通过 |
| Display 2 单屏 | 只有 D2 `SingleScreenBrowserActivity`，持续 10 秒无退出 | 通过 |
| D2 滚动长页 | D0/D2 截图内容均更新，Activity 不分叉 | 通过 |
| D0 滚动长页 | D0/D2 截图内容再次同步更新 | 通过 |
| D2 系统返回 | D0/D2 浏览器 Activity 同时消失 | 通过 |
| D0 系统返回 | D0/D2 浏览器 Activity 同时消失 | 通过 |
| 崩溃检查 | 无浏览器 `FATAL EXCEPTION` / `AndroidRuntime` | 通过 |

W3C 长页面测试中，初始、D2 滚动后、D0 滚动后的六张双屏截图 SHA-256 均不同，结合 Activity
状态和共享 `KBrowserCompositor` 首帧日志，确认两块屏显示并操作同一个连续页面，而不是镜像或两个页面同步模拟。
