# KEMI 跨屏软键盘需求与设计

## 1. 文档目的

本文定义 KEMI 远程桌面在 Android 双屏设备上的软键盘行为、状态模型、原生与 Flutter 的职责边界，以及后续重构和验收标准。

本文是跨屏软键盘功能的唯一规格。现有实现若与本文冲突，以本文为准。

相关背景见 [dual-screen-port.md](dual-screen-port.md)。

## 2. 已确认需求

### 2.1 键盘显示位置

双屏均在线时，系统软键盘只显示在 App 当前所在屏幕的对面屏幕：

| App 所在屏幕 | 键盘目标屏幕 |
|---|---|
| Display 0（主屏） | 当前可用的非 0 副屏，设备现状通常为 Display 2 |
| 非 0 副屏 | Display 0（主屏） |

只有一个可用屏幕时，回退到 App 当前所在屏幕，不阻断输入。

目标屏幕必须在每次打开键盘时由 Android 原生层根据当前活动窗口和在线 Display 列表计算。Flutter 不查询、不缓存 Display ID。

### 2.2 键盘按钮行为

键盘按钮是一个开关：

1. `hidden` 状态点击按钮，请求打开目标屏幕键盘。
2. `visible` 状态点击按钮，请求关闭目标屏幕键盘。
3. `opening` 和 `closing` 状态不重复执行请求。
4. 按钮颜色只反映原生层确认的实际状态，不能在发送请求后直接假定成功。
5. 未打开时使用工具栏默认颜色；实际可见时使用明确的激活颜色。

### 2.3 App 页面必须保持不动

点击键盘按钮前后，当前 App 屏幕不得发生任何布局或系统栏变化：

- 底部状态栏/工具栏不能上移、缩放、展开或收起。
- 远程画面尺寸和位置不能改变。
- 不新增或移动悬浮按钮。
- 不切换系统状态栏和导航栏的显示状态。
- 不显示输入框、提示卡片、快捷键说明或其他代理 UI。
- 不因目标屏幕 IME 的显示状态触发当前页面 `MediaQuery.viewInsets` 自适应。

跨屏代理模式下，当前 App 页面不得创建或聚焦用于唤起 IME 的 Flutter `TextField`/`TextFormField`。

### 2.4 用户强制关闭键盘

用户可在目标屏幕通过输入法收起按钮、系统返回键、切换输入法等方式关闭软键盘。App 必须检测实际关闭并同步状态：

1. 目标屏幕原生代理窗口监听该窗口的 IME Insets。
2. 只有检测到 `WindowInsets.Type.ime()` 实际可见，状态才变为 `visible`。
3. 新打开请求必须先获得本次`showSoftInput()`的接受结果；代理停放后延迟到达的旧`visible=true` Insets不得确认新请求。
4. IME 从可见变为不可见时，状态立即变为 `hidden`。
5. 原生层通知 Flutter 更新按钮颜色。
6. 用户从输入法的收起键/返回键关闭时，代理窗口释放输入焦点并进入透明、不可触摸、不可聚焦的停放状态，下次直接复用。
7. 用户在主屏按HOME时，必须把这次动作视为明确关闭：隐藏IME、销毁代理Activity并释放Manager资源；副屏下次点击必须新建主屏代理，不得复用退到后台的旧任务。

不能使用当前 App 屏幕的 `KeyboardVisibilityController` 判断目标屏幕键盘状态。

### 2.5 自动关闭场景

以下任一情况发生时，必须关闭代理键盘并最终回到 `hidden`：

- 用户再次点击键盘按钮。
- 用户在目标屏幕强制收起键盘。
- 远程连接结束或远程页面销毁。
- App 进入后台。
- 主屏代理收到HOME / `onUserLeaveHint`。
- 代理 Activity 被系统销毁。
- 目标 Display 断开或变为不可用。
- 打开请求失败或超时。

### 2.6 输入能力

代理输入必须转发给发起请求时的当前远程会话：

- 中文、英文、数字、空格和常用符号。
- 回车、退格和 Tab。
- 输入法组合文本应在提交后发送，不能把拼音组合过程重复发送。
- 每个字符或提交文本只能发送一次。
- 代理关闭后停止转发。
- 会话已结束时丢弃输入，不得转发到新的连接。

### 2.7 远程画面指针操作与键盘保持

键盘打开后，在发起远控的PAD显示区域进行以下操作均不得关闭键盘：

- 物理鼠标左键点击、移动和拖动。
- PAD手指点击、滑动和多指触摸。
- 虚拟鼠标控件产生的远程输入。

源屏`FlutterActivity`必须在把`MotionEvent`交给Flutter前，把真实鼠标或触摸事件通知原生键盘代理。鼠标主键和触摸按下进入同一IME焦点保护；鼠标右键必须取消该保护，保证右键down/up完整转发。只有用户点击键盘开关、在键盘目标屏主动收起、按HOME、退出会话或目标Display消失才可关闭键盘。

### 2.8 工具栏收起状态

用户主动点击底部工具栏“收起”时：

1. 工具栏收起后，展开入口固定在远程画面右下角。
2. 展开入口使用50%透明度，避免遮挡远程内容。
3. 若键盘为`hidden`，收起完成后自动请求打开键盘。
4. 键盘已处于`opening/visible/closing`时不得重复请求或反向关闭。
5. 点击右下角入口只展开工具栏，不自动关闭键盘。

这是用户明确触发的工具栏动作，不代表键盘可见性变化可以自动移动工具栏。

### 2.9 首次认证输入安全

密码认证完成前不得创建可能抢占焦点的对屏键盘代理Activity，这条规则与远程页位于主屏还是副屏无关。只有`pi.isSet`确认认证完成后才允许预创建代理宿主。密码框继续使用Flutter自身输入连接，代理键盘不得接收或转发认证密码。

## 3. 非目标

本功能不提供：

- 主屏快捷键控制面板。
- 键盘使用教学或快捷键提醒。
- 可见的代理输入框。
- 用户手动选择目标 Display 的设置页。
- 同时在两个屏幕显示软键盘。

## 4. 设计原则

### 4.1 原生层是唯一状态源

Android 原生层拥有以下事实：

- App 当前窗口所在 Display。
- 当前在线 Display 列表。
- 代理 Activity 是否创建成功。
- 目标窗口的 IME 是否实际可见。
- 目标 Display 是否断开。

因此键盘代理状态必须由原生层维护，Flutter 只能发送意图和渲染状态。

### 4.2 Flutter 不参与 IME 生命周期

跨屏代理流程中 Flutter 不执行：

- `enable_soft_keyboard`。
- 创建 `_showEdit` 对应的隐藏输入框。
- 请求 `_mobileFocusNode` 焦点。
- 根据 `KeyboardVisibilityController` 调整布局。
- 根据键盘状态改变 `SystemChrome` overlays。

现有单屏键盘流程若仍需保留，应与跨屏代理流程通过明确模式隔离，不能同时运行。

### 4.3 请求与实际状态分离

“请求打开”不等于“已经可见”。状态转换必须以代理窗口的实际回调为准。

## 5. 状态机

```text
                    open request
        +----------------------------------+
        |                                  v
     hidden --------------------------> opening
        ^                                  |
        |                                  | IME visible
        | open failed / timeout            v
        +------------------------------- visible
        ^                                  |
        |                                  | close request
        |                                  | user hides IME
        |                                  | display removed
        |                                  v
        +------------------------------- closing
                   IME hidden / activity destroyed
```

状态定义：

| 状态 | 含义 | 按钮 |
|---|---|---|
| `hidden` | 无代理或 IME 不可见 | 默认颜色，可点击打开 |
| `opening` | 已创建打开请求，等待 IME Insets 确认可见 | 默认颜色，暂时禁用 |
| `visible` | 目标屏幕 IME 实际可见 | 激活颜色，可点击关闭 |
| `closing` | 已请求关闭，等待 IME 隐藏或代理停放 | 激活颜色，暂时禁用 |

状态事件必须携带递增的 `requestId`。旧代理实例的延迟回调若 `requestId` 不匹配，必须被忽略。

## 6. 目标架构

```text
RemotePage 键盘按钮
        |
        | keyboard_proxy_open / keyboard_proxy_close
        v
当前 FlutterActivity 的 MethodChannel
        |
        v
KeyboardProxyManager（进程级单例，唯一状态源）
        |
        +-- 读取源 Activity displayId
        +-- 枚举在线 Displays
        +-- 选择对面 targetDisplayId
        +-- 创建 requestId 和 sessionId 快照
        +-- 进入远程页时在 targetDisplayId 预创建 KeyboardProxyActivity
        +-- 打开键盘时优先复用已停放的代理
        |
        v
KeyboardProxyActivity（目标屏幕，无可见 UI）
        |
        +-- 唯一原生 EditText / InputConnection
        +-- WindowInsets.Type.ime() 可见性监听
        +-- 输入提交和特殊键转发
        +-- 返回键、IME 收起、Display 移除、onDestroy 关闭
        |
        v
KeyboardProxyManager
        |
        +-- 校验 requestId / sessionId
        +-- 发布 keyboard_proxy_state
        +-- 转发输入到当前远程会话
        v
Flutter 只更新键盘按钮状态与颜色
```

## 7. 组件职责

### 7.1 `KeyboardProxyManager`

建议将当前 `InputProxyManager` 重命名并重写为 `KeyboardProxyManager`，职责如下：

- 保证同一进程最多一个活动代理。
- 计算源 Display 和目标 Display。
- 维护 `hidden/opening/visible/closing` 状态。
- 生成和校验 `requestId`。
- 持有发起请求时的远程 `sessionId`。
- 预创建、激活、停放和最终释放 `KeyboardProxyActivity`。
- 接收 Activity 的 IME 状态和输入事件。
- 向正确的 Flutter engine 发布状态。
- 监听 Display 移除事件并关闭代理。

Manager 不持有 Activity 强引用；当前代理使用弱引用或仅由 Activity 主动回调。

### 7.2 `KeyboardProxyActivity`

职责如下：

- 只在指定 `targetDisplayId` 启动。
- 在 `onCreate` 校验实际 `displayId`；不匹配则失败并结束。
- 使用不可见但合法尺寸的原生 `EditText` 获取输入连接。
- 激活时不得设置`FLAG_NOT_FOCUSABLE/FLAG_NOT_TOUCHABLE`；停放时必须设置，避免透明窗口遮挡本地屏幕。
- 使用 `WindowInsets` 监听目标窗口 IME 的实际可见性。
- 在第一次确认可见后上报 `visible`。
- 可见后检测到输入法自身隐藏时上报 `hidden`并停放复用；收到HOME、页面退出、App后台化或Display移除时`finish()`。
- `onDestroy` 必须幂等上报关闭。
- 不显示标题、背景、提醒、快捷键或任何可见控件。

Activity 的 theme 和 window 配置应保证：

- 无动画。
- 无 dim。
- 不进入最近任务。
- 不改变另一屏 App 布局。
- 生命周期只服务一次键盘会话。

### 7.3 `RemotePage`

跨屏模式下只负责：

- 点击按钮时发送 open/close 请求。
- 接收原生 `keyboard_proxy_state`。
- 根据状态设置按钮颜色和是否可点击。
- 页面销毁时请求关闭代理。

键盘状态变化本身不得修改：

- `_showEdit`。
- `keyboardVisibilityController`。
- `_mobileFocusNode`。
- `SystemChrome` overlays。
- `floatingActionButtonLocation`。
- `_showBar`。

用户点击工具栏“收起”是唯一例外：该明确动作可以收起`_showBar`、把50%透明的展开入口放到右下角，并在键盘为`hidden`时调用现有打开流程；IME可见性回调自身仍不得改变这些布局状态。

`Scaffold` 明确设置 `resizeToAvoidBottomInset: false`，作为布局不变的第二道保护；第一道保护仍是当前页面不创建 IME 输入连接。

## 8. 原生与 Flutter 协议

建议统一使用当前 Activity 对应的 Flutter engine channel，不再用广播在多个 Activity 之间猜测接收者。

### Flutter -> Android

#### `keyboard_proxy_open`

参数：

```json
{
  "sessionId": "当前远程会话ID"
}
```

返回：

```json
{
  "accepted": true,
  "requestId": 42
}
```

返回 `accepted` 仅代表请求进入 `opening`，不代表 IME 已可见。

#### `keyboard_proxy_close`

参数：

```json
{
  "requestId": 42
}
```

### Android -> Flutter

#### `keyboard_proxy_state`

参数：

```json
{
  "requestId": 42,
  "state": "visible",
  "sourceDisplayId": 0,
  "targetDisplayId": 2,
  "reason": "ime_visible"
}
```

`reason` 可取：

- `open_requested`
- `ime_visible`
- `user_hidden`
- `close_requested`
- `display_removed`
- `launch_failed`
- `open_timeout`
- `activity_destroyed`
- `session_closed`
- `app_backgrounded`
- `home_pressed`

### 输入事件

输入事件不再通过全局隐式广播。建议由 `KeyboardProxyManager` 直接转给发起请求的 Flutter engine：

- `keyboard_proxy_commit_text`：提交文本。
- `keyboard_proxy_key`：回车、退格、Tab 等特殊键。

每个输入事件必须携带 `requestId` 和 `sessionId`，Flutter 在发送到 Rust FFI 前再次校验当前会话。

## 9. 需要删除或替换的现有逻辑

实施时按以下清单收敛，不在旧路径上继续叠加：

### Flutter

- 删除 `_proxyKeyboardActive` 作为自行推断的布尔状态，改为原生状态枚举。
- 跨屏代理分支不再设置 `_showEdit`。
- 跨屏代理分支不再调用 `enable_soft_keyboard`。
- 移除代理状态对 `keyboardIsVisible`、FAB 位置和工具栏布局的影响。
- 移除全局 FocusManager 自动触发键盘代理的逻辑。
- 移除代理输入对应的 `KeyboardVisibilityController` 状态判断。

### Android

- 删除 Presentation 方案残留和相关注释。
- 删除 `get_display_id` Dart 调用路径。
- 将 `show_input_proxy`/`hide_input_proxy` 替换为新协议。
- 删除 `SOFT_INPUT_STATE_ALWAYS_HIDDEN` 与 `FLAG_ALT_FOCUSABLE_IM` 的交叉控制；当前 App 页面不创建输入连接后不再需要依赖这些 flag 阻止副屏 IME。
- 删除隐式 `ACTION_TEXT_INPUT` 广播转发，改为 Manager 内部有请求归属的回调。
- 代理关闭必须真正隐藏IME并释放输入焦点；输入法自身收起时可保留不可交互容器，主屏HOME时必须销毁容器。

## 10. 打开与关闭时序

### 10.1 打开

1. Flutter 状态为 `hidden`，用户点击键盘按钮。
2. Flutter 调用 `keyboard_proxy_open(sessionId)`，按钮暂时禁用。
3. Manager 计算目标 Display，状态设为 `opening`。
4. Manager 优先激活进入远程页时预创建的`KeyboardProxyActivity`；无可复用实例时才启动新Activity。
5. Activity 校验实际 Display，把现有代理任务切到目标屏前台，恢复输入焦点并请求显示 IME。
6. `showSoftInput()`先确认本次请求已被输入法服务接受，随后`WindowInsets.Type.ime()`确认实际可见；旧请求的延迟Insets必须忽略。
7. Manager 发布 `visible`。
8. Flutter 将按钮切换为激活色并恢复可点击。

### 10.2 App 主动关闭

1. Flutter 状态为 `visible`，用户再次点击键盘按钮。
2. Flutter 调用 `keyboard_proxy_close(requestId)`。
3. Manager 状态设为 `closing`并请求隐藏IME。
4. Activity 检测IME隐藏，释放输入焦点并停放为透明、不可触摸状态。
5. Manager 发布 `hidden`。
6. Flutter 按钮恢复默认色。

### 10.3 用户强制关闭

1. 用户在目标屏幕点击输入法收起按钮或按返回键。
2. Activity 的 IME Insets 从可见变为不可见。
3. Activity 通知 Manager，Manager 发布 `hidden(reason=user_hidden)`。
4. Flutter 按钮恢复默认色。
5. Activity释放输入焦点并停放；下一次点击复用同一实例。

### 10.4 主屏按HOME关闭

1. 主屏键盘代理收到`onUserLeaveHint()`。
2. Activity请求Manager执行`release(home_pressed)`，状态从`visible/opening`进入`closing`。
3. Activity隐藏IME、清理输入连接并`finish()`；Manager幂等发布`hidden(home_pressed)`。
4. 副屏下次点击键盘时创建全新的主屏Activity任务。
5. Android 12及部分厂商ROM需授予`SYSTEM_ALERT_WINDOW`（系统界面通常显示为“允许显示在其他应用上层”），才允许副屏上的用户点击在主屏桌面上新建代理。该权限不用于创建可见悬浮窗。

## 11. 失败与超时

- 当前`opening`超时为8秒，覆盖部分输入法首次唤起较慢的设备。
- 超时未检测到 IME 可见：关闭代理并发布 `hidden(reason=open_timeout)`。
- 目标 Display 在启动前不可用：发布 `hidden(reason=launch_failed)`。
- Activity 实际 Display 与目标不一致：结束 Activity 并发布失败。
- 任意关闭路径必须幂等，多次调用不能崩溃或重复发送状态。

## 12. 验收标准

### 12.1 屏幕位置

- App 在 Display 0，键盘只出现在在线副屏。
- App 在副屏，键盘只出现在 Display 0。
- 单屏设备回退到当前屏幕。

### 12.2 页面稳定性

打开和关闭键盘前后截图对比，除键盘按钮颜色外，当前 App 屏幕必须满足：

- 工具栏坐标不变。
- 远程画面边界不变。
- FAB 数量和坐标不变。
- 系统栏可见性不变。
- 不出现提示、快捷键面板或代理 UI。

### 12.3 状态同步

- 首次点击：`hidden -> opening -> visible`。
- 再次点击：`visible -> closing -> hidden`。
- 用户强制收起：App 在 500ms 内收到 `hidden`，按钮恢复默认色。
- 主屏按HOME：旧代理任务被销毁并返回`hidden(home_pressed)`；副屏再次点击后新任务到达`visible`。
- 打开失败：超时后恢复`hidden`，按钮不保持激活。

### 12.4 输入

- 中文输入法提交文本只发送一次。
- 英文、数字、空格、符号正常。
- 回车、退格、Tab 正常。
- 关闭代理后输入不再发送。
- 旧会话代理不能向新会话发送输入。

### 12.5 稳定性

- 连续开关50次无崩溃；停放Activity不可触摸、不可聚焦，主屏HOME或退出远程页后必须销毁。
- 两屏分别启动 App 并测试交叉弹出。
- 目标屏拔出时代理正确关闭。
- 连接退出、App 后台化后代理正确关闭。

## 13. 实施顺序

1. 建立原生 `KeyboardProxyManager` 状态机和事件协议，不接输入。
2. 重写 `KeyboardProxyActivity` 的 Display 校验和 IME Insets 监听。
3. Flutter 接入状态事件，只实现按钮状态和颜色，不创建 TextField。
4. 删除旧布局适应和 Focus 自动触发路径。
5. 接入文本提交、退格、回车、Tab，并校验 session/request。
6. 实现关闭、超时、Display 移除和生命周期清理。
7. 真机执行双屏截图、状态日志和 50 次开关测试。

## 14. 当前实现对照（2026-07-27）

本节按 2026-07-27 的代码与真机日志更新。

### 14.1 已落地（2026-07-28 更新）

- 原生状态机已落地：`hidden/opening/visible/closing`，并用 `requestId` 防止旧回调串扰。
- 代理 Activity 已落地：目标屏启动、原生 `EditText` 输入连接、`WindowInsets.Type.ime()` 监听可见性。
- Flutter Android 分支已改为代理协议：`keyboard_proxy_prepare/open/close/release`。
- Flutter 键盘按钮已加互斥与过渡态禁点；`opening/closing` 显示加载态。
- 关闭竞态已修复：在 `onPointerDown` 捕获关闭意图，避免同一次点击被状态抖动反转。
- 会话归属已落地：文本/按键转发前校验 `sessionId` 与 `requestId`。
- 页面稳定性已落地：Android 端 `resizeToAvoidBottomInset=false`，并跳过当前屏 `KeyboardVisibilityController` 对布局的影响。
- 输入转发链路完整落地：`commitText` / `finishComposingText` / `TextWatcher` 三条文本提交路径，含 250ms 去重。
- 删除键转发完整落地：`deleteSurroundingText` / `sendKeyEvent` / `setOnKeyListener` 三条路径，含 composition 状态感知。
- IME 焦点增强：主动 `requestFocus` + `restartInput` + `showSoftInput` 高频重试（350ms × 16 次），不依赖 `windowFocus` 前置条件。
- 去 `FLAG_NOT_TOUCHABLE`：确保代理窗口可正常接收 IME 输入连接。
- `FFI.invokeMethod` 返回类型修复：`Future<bool>` → `Future<dynamic>`（修 Map 返回导致的 type mismatch 红屏）。
- `mChannel.invokeMethod` 全路径异常容错：`MissingPluginException` / `PlatformException` / 通用异常降级。
- 副屏 `RemoteActivity.mChannel` 文件传输平台方法补齐。
- `enable_soft_keyboard` 改为 no-op：避免跨屏代理模式下干扰键盘代理焦点路由。
- 单屏回退已落地：没有可用副屏时，Manager 选择发起 Activity 所在 Display，`KeyboardProxyActivity` 由该 Activity 直接启动，不使用 `launchDisplayId=0`；双屏时仍只使用对面 Display 的 `ActivityOptions.launchDisplayId`。
- `requestPermission` 支持指定 `MethodChannel`：保障副屏权限回调回到副屏引擎。

### 14.2 与设计仍有偏差

- 文档建议 `opening` 超时为 2 秒；当前实现为 8 秒（容错优先）。
- `showSoftInput()` 仍可能前 1-2 次返回 `false`，当前靠 350ms 重试兜底，不属于确定性首发成功路径。

## 15. 2026-07-27 调试实录（问题 -> 证据 -> 处理）

本节用于后续问题追踪，按当天实际排障顺序记录。

### 15.1 初始现象

- 用户反馈“跨屏点击键盘后，对面系统键盘经常不弹或明显慢”。
- 现场复测后确认：并非完全不弹，而是“能弹但慢”，且多次点击会出现状态错乱体感。

### 15.2 中间问题与失败路径

1. Presentation 路径失败。
        - 现象：跨屏窗口 token/焦点行为不稳定，无法形成可持续输入连接。
        - 处理：放弃 Presentation，统一改为 `ActivityOptions.launchDisplayId` 启动真实目标屏 Activity。

2. Flutter 当前屏输入连接路径失败。
        - 现象：当前屏 `TextField` 与软键盘联动导致页面布局/系统栏变化，不满足“页面不动”要求。
        - 处理：Android 跨屏代理分支不再走 `_showEdit` 与本地软键盘唤起路径。

3. 状态来源不权威。
        - 现象：旧路径用当前屏可见性推断对面屏状态，出现按钮颜色与真实状态不同步。
        - 处理：状态唯一来源收敛到目标屏代理窗口的 IME Insets 变化。

4. 快速点击与关闭竞态。
        - 现象：在 IME 显隐过渡中连点，出现“本应关闭却重新打开”的错乱。
        - 处理：
          - 引入 `opening/closing` 互斥与禁点。
          - 在 `onPointerDown` 抢先捕获 close intent，`onPressed` 只执行该快照意图。

5. 保活代理 Activity 低时延尝试失败。
        - 现象：尝试保留同一代理 Activity 以热复用，但该 ROM 下 IME client 不会可靠迁移到旧代理输入连接。
        - 处理：回退为“每次打开新建目标屏代理 Activity，关闭即销毁”的保守可靠路径。

6. 延迟主因误判纠正。
        - 初看像是 RustDesk 启动慢；日志拆解后确认 Activity 启动仅约 0.4-0.5 秒。
        - 真正大头在跨屏迁移期间 IME Service 重建导致输入引擎停启。

### 15.3 关键时间线证据

- 修复前（典型两次）：
  - `open_requested -> ime_visible` 约 2.8 秒、约 3.7 秒。
  - `KeyboardProxyActivity` 首次 `showSoftInput` 返回 `accepted=false`，并出现固定 2 秒重试间隔。
- 联合日志显示：
  - 跨屏迁移时 `FcitxInputMethodService` 被销毁/重建。
  - Daemon 在短暂无客户端窗口内立即 `stop()`，随后新 Service 再次启动引擎。
  - 停启过程出现约 2.3 秒级阻塞，成为主要延迟来源。

### 15.4 当天最终处理与效果

- RustDesk 侧保留可靠路径：每次新建目标屏代理 Activity；状态机与会话归属严格校验。
- 联动输入法侧采用“最后客户端断开后短暂宽限再停机”的策略，避免跨屏迁移期间立即停引擎。
- 修复后连续 5 轮 `opening -> visible -> closing -> hidden` 全部成功。
- 修复后 `open_requested -> visible` 约 1.11-1.42 秒；未再观察到修复前的停启重建长尾。

## 16. 当日验证结论与遗留项

### 16.1 已验证

- 双屏方向 `Display 0 -> Display 2`：连续多轮开关成功，状态闭环完整。
- 用户强制收起可回传 `hidden`，按钮状态可同步恢复。
- 页面不动约束满足：未再出现因当前屏输入连接导致的布局变化。

### 16.2 待补验证

- 反向方向 `Display 2 -> Display 0` 需同口径计时并留档。
- `FLAG_NOT_TOUCHABLE` 在该机型可用，但跨 ROM 兼容性仍需回归。
- 若后续首发仍偶发 `accepted=false`，应先保留可靠路径，再评估是否缩短重试间隔。

## 17. 双向申请键盘实现原理（主屏 <-> 副屏）

本节描述“主屏 APK 申请键盘，键盘弹到副屏”和“副屏 APK 申请键盘，键盘弹到主屏”的同一套实现机制。

### 17.1 核心原则

- 键盘不跟随“哪个 APK 实例”决定，而是跟随“当前发起请求的 Activity 所在 Display”决定。
- 目标屏计算规则固定为“对面屏优先”：
  - 源为 `Display 0` 时，目标取可用非 0 屏（当前设备通常为 `Display 2`）。
  - 源为非 0 屏时，目标固定回 `Display 0`。
- Flutter 只表达 open/close 意图，不做 Display 选择，不做键盘可见性判断。
- Android 原生层是状态唯一真源，Flutter 仅渲染状态与禁点控制。

### 17.2 主屏 APK 申请键盘 -> 键盘弹到副屏

1. 主屏远程页点击键盘按钮，Flutter 发送 `keyboard_proxy_open(sessionId)`。
2. `KeyboardProxyManager` 读取当前 Activity 的 `sourceDisplayId=0`。
3. Manager 枚举在线 Display，选择首个可用非 0 屏作为 `targetDisplayId`。
4. Manager 生成本次 `requestId`，状态切 `opening`。
5. Manager 通过 `ActivityOptions.launchDisplayId(targetDisplayId)` 启动 `KeyboardProxyActivity`。
6. 代理 Activity 创建原生 `EditText/InputConnection` 并请求 `showSoftInput()`。
7. 当目标窗口 `WindowInsets.Type.ime()` 真实可见，Manager 发布 `visible`。
8. Flutter 收到 `keyboard_proxy_state` 后将按钮置激活色。

### 17.3 副屏 APK 申请键盘 -> 键盘弹到主屏

1. 副屏远程页点击键盘按钮，Flutter 同样发送 `keyboard_proxy_open(sessionId)`。
2. Manager 读取当前 Activity 的 `sourceDisplayId!=0`（例如 `2`）。
3. 目标屏直接选 `targetDisplayId=0`。
4. 后续流程与 17.2 完全一致：`opening -> visible -> closing -> hidden`。

说明：
- 双向流程复用同一套状态机与协议，不需要两套代码。
- 差异只在“目标屏计算”一步；其余输入连接、状态回传、关闭清理完全一致。

### 17.4 关键实现细节

#### 17.4.1 为什么必须用 Activity，而不是当前页隐藏输入框

- 当前页隐藏输入框会触发本屏 `viewInsets` 变化，导致布局/状态栏联动，违反“页面不动”要求。
- 目标屏代理 Activity 提供独立输入连接，能让键盘出现在对面屏，且不影响当前屏布局。

#### 17.4.2 为什么要有 `requestId` + `sessionId`

- `requestId` 防止旧代理延迟回调污染新请求（典型在快速连点或系统调度抖动时发生）。
- `sessionId` 防止输入误发到新会话（连接切换或页面重建时尤为关键）。

#### 17.4.3 为什么 `visible` 不能在 open 返回时就认定

- `open` 返回仅表示请求被接受，不代表 IME 已显示。
- 必须等待目标窗口 Insets 的真实可见回调，才能认为已成功打开。

#### 17.4.4 关闭必须幂等

- 用户点击关闭、用户手势收起、App 后台、Display 断开，都可能同时触发关闭路径。
- 关闭逻辑必须允许重复进入，最终只收敛到一次 `hidden`，不能崩溃或卡死。

### 17.5 中间遇到的坑与处理

1. 坑：Presentation/悬浮输入方案在双屏 ROM 下焦点不稳定。
        - 现象：偶发拿不到输入焦点，键盘不弹或弹出后无法稳定输入。
        - 处理：统一改为目标屏真实 Activity 承载输入连接。

2. 坑：用当前屏可见性推断对面屏键盘状态，按钮状态经常错。
        - 现象：按钮已亮但对面没键盘，或对面已收起但按钮仍亮。
        - 处理：状态真源改为代理窗口 Insets，Flutter 不再自行推断。

3. 坑：快速连点导致 open/close 串扰。
        - 现象：用户本意关闭，结果因为延迟回调又被重新打开。
        - 处理：引入 `opening/closing` 禁点 + `onPointerDown` 意图快照 + `requestId` 过滤。

4. 坑：跨屏迁移时输入法服务停启，首开耗时长。
        - 现象：`open_requested -> visible` 出现 2~4 秒长尾。
        - 处理：代理路径保持可靠关闭；输入法侧采用短暂宽限保活，减少停启重建。

5. 坑：Android 权限/通道方法在某些上下文可能缺失，导致红屏。
        - 现象：点击“传输文件”等入口时抛 `PlatformException(No such method)`。
        - 处理：对通道调用统一加异常兜底，避免未捕获异常触发 Flutter 红屏。

### 17.6 排障建议（现场执行顺序）

1. 先看状态链是否完整：`hidden -> opening -> visible -> closing -> hidden`。
2. 再看 Display 链是否正确：`sourceDisplayId` 与 `targetDisplayId` 是否对向。
3. 再看输入链：提交文本是否携带并匹配当前 `requestId/sessionId`。
4. 最后看系统链：IME 服务是否在跨屏时发生销毁重建长尾。

若只有“副屏->主屏”失败，优先检查：
- 源 Activity 实际 displayId 识别是否正确。
- 目标 `Display 0` 启动参数是否被 ROM 策略覆盖。
- 代理 Activity 是否在主屏真正获得焦点与 Insets 回调。
## 18. 输入转发链路详解（2026-07-28 调试终版）

本节是跨屏代理输入转发的权威参考，记录每条路径的触发条件、处理逻辑和已知坑。

### 18.1 架构总览

代理 EditText（1×1 透明像素）的唯一职责是托管 IME 的 `InputConnection`。所有输入通过覆写 `InputConnectionWrapper` 的方法拦截并转发到远程。本地 EditText 在每次 `commitText` 后立即清空，不保留任何用户文本。

```
用户输入
  │
  ├─ 文本提交 ──────────────────────────────────────
  │   ├─ commitText()           ← IME 提交最终文本（主路径）
  │   ├─ finishComposingText()  ← 选词结束，组合文本刷新（兜底）
  │   └─ TextWatcher.onTextChanged() ← 安全网（捕获绕过 commitText 的 IME）
  │       │
  │       └─ forwardCommittedText(text, source)
  │           ├─ 250ms 同文本去重
  │           └─ KeyboardProxyManager.commitText()
  │               └─ channel.invokeMethod("keyboard_proxy_commit_text")
  │
  ├─ 删除键 ────────────────────────────────────────
  │   ├─ deleteSurroundingText() ← 软键盘 IME 主路径
  │   ├─ sendKeyEvent(DEL)       ← 部分 IME 用 KeyEvent 发删除
  │   └─ setOnKeyListener(DEL)   ← 硬件键盘兜底
  │       │
  │       └─ composingStart >= 0 ?
  │           ├─ YES → super（IME 本地删拼音/组合文本）
  │           └─ NO  → KeyboardProxyManager.sendKey("VK_BACK")
  │
  └─ 特殊键 ────────────────────────────────────────
      ├─ setOnEditorActionListener  → VK_RETURN
      ├─ setOnKeyListener(TAB)      → VK_TAB
      └─ setOnKeyListener(DEL)      → VK_BACK（硬件键盘）
```

### 18.2 文本提交三条路径与去重

#### 18.2.1 commitText（主路径）

```kotlin
override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
    forwardCommittedText(text, "commitText")  // 先转发，再调 super
    val handled = super.commitText(text, newCursorPosition)
    host.post { /* 清空 EditText */ }
    return handled
}
```

- 大部分 IME 在用户选词后走此路径。
- **先转发再 super**：确保文本先到达远程，避免 super 内部修改状态影响转发。
- **post 清空 EditText**：在下一帧清空，避免干扰 IME 的后续回调。

#### 18.2.2 finishComposingText（兜底路径）

```kotlin
override fun finishComposingText(): Boolean {
    val handled = super.finishComposingText()
    val composed = host.text?.toString().orEmpty()
    if (composed.isNotEmpty()) {
        forwardCommittedText(composed, "finishComposingText")
        host.post { /* 清空 EditText */ }
    }
    return handled
}
```

- **为什么需要**：部分中文输入法（尤其是第三方 IME）在选词时不调 `commitText`，而是先调 `finishComposingText` 结束组合态，把最终文本留在 EditText 里。如果不覆写此方法，文本会丢失。
- **时序**：先 `super.finishComposingText()` 让组合态结束 → 读取 EditText 中剩余文本 → 转发 → 清空。
- 这是 2026-07-28 调试中发现"nihao 选词不回传"的根因修复。

#### 18.2.3 TextWatcher（安全网）

```kotlin
override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    if (ignoreTextChange) return
    val committed = s?.toString().orEmpty()
    val composingStart = BaseInputConnection.getComposingSpanStart(editableText)
    if (committed.isEmpty() || composingStart >= 0) return
    forwardCommittedText(committed, "textWatcher")
    ignoreTextChange = true
    text.clear()
    ignoreTextChange = false
}
```

- **安全网定位**：捕获极少数完全不走 `commitText` / `finishComposingText` 的 IME。
- **composingSpan 检查**：有活跃组合态时不转发——组合文本变动是 IME 内部行为（如拼音逐字母变化），不应发送到远程。
- **ignoreTextChange 标志**：`commitText` 和 `finishComposingText` 在清空 EditText 前设置此标志，防止 TextWatcher 把清空操作误当新文本转发。

#### 18.2.4 去重机制

```kotlin
private fun forwardCommittedText(text: CharSequence?, source: String) {
    // ...
    val now = SystemClock.elapsedRealtime()
    if (committed == lastForwardedText &&
        now - lastForwardedAtMs <= DUPLICATE_COMMIT_WINDOW_MS  // 250ms
    ) {
        Log.i(TAG, "skip_duplicate_commit_text src=$source lastSrc=$lastForwardedSource ...")
        return  // ← 丢弃重复
    }
    lastForwardedText = committed
    lastForwardedSource = source
    lastForwardedAtMs = now
    KeyboardProxyManager.commitText(requestId, sessionId, committed)
}
```

- **为什么 250ms**：同一选词操作触发 `commitText` + `finishComposingText` 的间隔通常在 10-50ms 内。250ms 窗口足够覆盖此场景，同时不会误杀正常连续输入。
- **来源标记**：日志中可区分是 `commitText` / `finishComposingText` / `textWatcher` 哪条路径触发，便于排障。
- **每次 activate 重置**：新键盘会话开始时清空去重状态，避免跨会话干扰。
- 这是 2026-07-28 调试中发现"选词后副屏收到两次输入"的修复。

### 18.3 删除键三条路径与 composition 感知

#### 18.3.1 核心问题

`setOnKeyListener(KEYCODE_DEL)` **只对硬件键盘生效**。软键盘 IME 通过 `InputConnection` 方法与 App 通信：

| IME 操作 | 实际调用 |
|---------|---------|
| 点击删除键 | `deleteSurroundingText(beforeLength, afterLength)` |
| 部分 IME 删除 | `sendKeyEvent(KeyEvent.KEYCODE_DEL)` |
| 硬件键盘删除 | `onKeyDown` → `setOnKeyListener` |

只覆写 `setOnKeyListener` 会导致软键盘删除键完全无响应——这是 2026-07-28 调试中"删除键不工作"的根因。

#### 18.3.2 deleteSurroundingText（主路径）

```kotlin
override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
    val composingStart = BaseInputConnection.getComposingSpanStart(host.text)

    // 拼音组合中 → IME 本地处理，不转发远程
    if (composingStart >= 0) {
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    // 组合已结束 → 转发到远程
    for (i in 0 until beforeLength) {
        KeyboardProxyManager.sendKey(requestId, sessionId, "VK_BACK")
    }
    for (i in 0 until afterLength) {
        KeyboardProxyManager.sendKey(requestId, sessionId, "VK_DELETE")
    }

    val superHandled = super.deleteSurroundingText(beforeLength, afterLength)
    host.post { /* 清空 EditText */ }
    return superHandled || true
}
```

**composition 感知的设计逻辑**：

| 场景 | composingStart | 行为 |
|------|:---:|------|
| 输入拼音 `nihao` 中按删除 | ≥ 0 | → super（IME 删拼音字母，不转发） |
| 选词 `你好` 后按删除 | < 0 | → 转发 VK_BACK（远程删内容） |
| 输入英文 `hello` 后按删除 | < 0 | → 转发 VK_BACK（同上） |

- **beforeLength=1, afterLength=0**：标准 Backspace，删光标前一个字符。
- **beforeLength > 1**：IME 可能一次删多个字符（如删除选区），每个字符发一个 VK_BACK。
- **afterLength > 0**：Forward-delete（Delete 键），移动端极少触发，但仍做兼容。

#### 18.3.3 sendKeyEvent（备用路径）

```kotlin
override fun sendKeyEvent(event: KeyEvent?): Boolean {
    if (event != null && event.action == KeyEvent.ACTION_DOWN &&
        event.keyCode == KeyEvent.KEYCODE_DEL
    ) {
        val composingStart = BaseInputConnection.getComposingSpanStart(host.text)
        if (composingStart < 0) {
            KeyboardProxyManager.sendKey(requestId, sessionId, "VK_BACK")
            return true
        }
    }
    return super.sendKeyEvent(event)
}
```

- 部分 IME（如三星键盘、部分第三方输入法）用 `sendKeyEvent` 而非 `deleteSurroundingText` 发删除。
- 同样的 composition 判断逻辑：组合中不转发。

#### 18.3.4 setOnKeyListener（硬件键盘兜底）

保留 `setOnKeyListener` 处理硬件键盘删除键，逻辑不变。

### 18.4 输入转发校验链

每条输入事件经过三层校验才到达远程：

```
KeyboardProxyActivity                KeyboardProxyManager               Flutter
    │                                      │                              │
    ├─ active? closeRequested?             │                              │
    ├─ releaseRequested?                   │                              │
    │                                      │                              │
    └─ forwardCommittedText / sendKey ──→  ├─ requestId 匹配?             │
                                           ├─ sessionId 匹配?             │
                                           ├─ state == "visible"?         │
                                           │                              │
                                           └─ channel.invokeMethod() ──→ ├─ 当前会话校验
                                                                          └─ Rust FFI 发送
```

- **Activity 层**：`active` 为 false（已请求关闭）或 `releaseRequested` 时立即丢弃。
- **Manager 层**：`requestId` 不匹配 → 旧代理回调，丢弃；`sessionId` 不匹配 → 会话已切换，丢弃；`state != "visible"` → 键盘未真正打开，丢弃。
- **Flutter 层**：收到后再校验当前远程会话是否仍匹配。

### 18.5 IME 焦点增强策略

代理 Activity 在目标屏上是一个透明窗口，系统可能不自动给它 IME 焦点。因此采用主动抢焦策略：

```kotlin
private val requestIme = object : Runnable {
    override fun run() {
        if (!active || closeRequested || ...) return
        editText.requestFocus()           // 1. 先抢焦点
        inputMethodManager.restartInput(editText)  // 2. 重启输入连接
        val accepted = inputMethodManager.showSoftInput(editText, SHOW_IMPLICIT)  // 3. 请求显示键盘
        if (!accepted && imeRequestAttempts < MAX_IME_REQUEST_ATTEMPTS) {
            editText.postDelayed(this, IME_RETRY_DELAY_MS)  // 4. 350ms 后重试
        }
    }
}
```

- **不依赖 windowFocus**：即使窗口尚未获得焦点，也会尝试请求。
- **restartInput**：每次重试前重启输入连接，确保 IME 与最新 EditorInfo 同步。
- **最多 16 次重试**（350ms × 16 ≈ 5.6 秒），超出后静默放弃。
- 典型日志：第 1-2 次 `accepted=false`，第 3 次 `accepted=true`，总耗时约 1 秒。

### 18.6 关键类型修复：FFI.invokeMethod 返回类型

**问题**：`model.dart` 中 `FFI.invokeMethod` 声明返回 `Future<bool>`，但 `keyboard_proxy_open` 等方法返回 `Map`。Flutter 在类型检查时抛 `_Map<Object?, Object?> is not a subtype of FutureOr<bool>`，导致红屏。

**修复**：改为 `Future<dynamic>`。

```dart
// 修复前
Future<bool> invokeMethod(String method, [dynamic arguments]) async { ... }

// 修复后
Future<dynamic> invokeMethod(String method, [dynamic arguments]) async { ... }
```

影响范围：`mChannel` 和 `remoteChannel` 上所有返回非 bool 的方法调用。

### 18.7 异常容错一览

| 位置 | 异常类型 | 处理 |
|------|---------|------|
| `native_model.dart` invokeMethod | `MissingPluginException` | catch + log，不抛出 |
| `native_model.dart` invokeMethod | `PlatformException` | catch + log，不抛出 |
| `native_model.dart` invokeMethod | 通用 `Exception` | catch + log，不抛出 |
| `common.dart` connect() | Wakelock enable 异常 | try-catch，不阻断连接 |
| `common.dart` connect() | AndroidPermissionManager 异常 | try-catch，不阻断连接 |
| `common.dart` 文件传输权限 | 权限请求失败 | catch + 降级提示 |

### 18.8 2026-07-28 调试时间线

本节按实际排障顺序记录关键发现与修复。

#### 阶段 1：PAD 端缺少"传输文件"入口
- **现象**：PAD 端远程页面找不到文件传输入口。
- **处理**：在 `remote_page.dart` 三点菜单新增 Transfer file 入口，并移到底部操作栏。

#### 阶段 2：点击"传输文件"红屏
- **现象**：`PlatformException(No such method)` + Wakelock channel error。
- **根因**：副屏 `RemoteActivity.mChannel` 缺少文件传输相关方法实现。
- **处理**：补齐 `RemoteActivity.kt` 的 `mChannel` 方法；`common.dart` 加异常容错。

#### 阶段 3：Map 返回类型崩溃
- **现象**：`_Map<Object?, Object?> is not a subtype of FutureOr<bool>`。
- **根因**：`FFI.invokeMethod` 声明返回 `Future<bool>`。
- **处理**：改为 `Future<dynamic>`（见 18.6）。

#### 阶段 4：中文选词不回传副屏
- **现象**：输入 `nihao` 选词后副屏无任何文本，日志只有状态变更无 commit 日志。
- **根因**：部分 IME 选词时不调 `commitText`，而是调 `finishComposingText` 结束组合态。
- **处理**：覆写 `finishComposingText`，读取组合结束后的 EditText 内容并转发（见 18.2.2）。

#### 阶段 5：选词后副屏收到两次输入
- **现象**：同一次选词，副屏出现重复文本。
- **根因**：`commitText` 和 `finishComposingText` 先后触发，各自转发一次。
- **处理**：增加 250ms 同文本去重（见 18.2.4）。

#### 阶段 6：删除键不工作
- **现象**：软键盘点删除，副屏无反应。
- **根因**：只覆写了 `setOnKeyListener`（仅硬件键盘生效），未覆写 `deleteSurroundingText`（软键盘 IME 主路径）。
- **处理**：覆写 `deleteSurroundingText` + `sendKeyEvent`，含 composition 感知（见 18.3）。

#### 阶段 7：Git 备份推送失败
- **现象**：`git push backup master` 报 `did not receive expected object`。
- **根因**：backup 远程仓库存在对象损坏（浅克隆历史不完整）。
- **处理**：`git fetch --unshallow origin` 补全历史，再 force push 修复 backup/master。

## 19. 复用代理任务的跨屏稳定性（2026-08-01，PAD +120）

### 19.1 现场根因

每次点击时 `KeyboardProxyManager` 都正确得到 `sourceDisplayId=2`、`targetDisplayId=0`，所以问题不在 Display 枚举或源屏检测。错误发生在代理已经停放后的恢复阶段：旧实现用 `PendingIntent` 再次启动自身，并同时使用 `singleInstance`、`NEW_TASK`、`REORDER_TO_FRONT` 和 `SINGLE_TOP`。在当前 Android 12 双屏 ROM 上，这个从 Display 2 发出的 Activity launch 会把既有代理 task 迁移到 Display 2，即使第一次创建时的 `launchDisplayId` 是 0。

这也解释了“有时正确、有时错误”：第一次创建严格使用 `ActivityOptions.launchDisplayId=0`，通常正确；输入法被收起、代理进入复用路径后，下一次自启动才可能触发迁移。Manager 的 `targetDisplayId` 没变，因此只看 Manager 日志会误以为仍在主屏，必须同时读取 `KeyboardProxyActivity.displayId`、Activity task 所属 Display 和 IME token Display。

### 19.2 固定规则

1. 已存在的代理 Activity 不得通过 Intent、PendingIntent、`startActivity()` 或 `REORDER_TO_FRONT` 恢复焦点。
2. 复用只允许按已有 `taskId` 调用 `ActivityManager.moveTaskToFront()`；该操作不创建新的 Activity launch 请求。
3. `KeyboardProxyActivity` 创建时保存 `expectedDisplayId`。每次请求 IME 前必须用 Activity 的真实 `displayId` 再校验一次。
4. 真实屏幕不符时不得尝试“将错就错”显示 IME；必须发布 `display_mismatch`、释放代理，让下一次用户点击重新按当前屏幕关系创建。
5. HOME 仍属于显式退出：旧代理销毁，下一次点击走完整跨屏创建；不能把 HOME 后的后台 task 当成普通停放实例复用。

### 19.3 真机验收证据

`192.168.3.46:5555`、Display 2 远控页、PAD `1.4.46+120`：

- request 2～16连续打开均为 `display=0 expected=0`，输入法真实可见后进入 `visible`；收起后停放仍为 Display 0。
- request 5、12、14 后分别按 HOME，旧 task 均释放；request 6、13、15创建的新 task 仍位于 Display 0。
- 共15次打开，没有 `Refuse IME on unexpected display`、`display_mismatch`或 task 迁移到 Display 2。

后续修改跨屏键盘时，至少重复“首次打开→输入法收起→再次打开”10轮，并额外覆盖一次“HOME→副屏再打开”。只验证第一次弹出不能证明复用路径正确。

## 20. 2026-08-03 跨屏键盘与物理鼠标最终闭环（PAD 1.4.48+137）

本节记录2026-08-03最终验收结论：跨屏软键盘打开后，远控画面的物理鼠标左键不再导致键盘关闭或闪烁，物理鼠标右键保持完整的按下/抬起转发且不再卡住。最终交付版本为`1.4.48+137`；此前`+130～+136`均属于定位或中间验证版本，不能替代本节的最终结论。

### 20.1 已证实的根因

当键盘位于 Display 2、远控画面位于 Display 0 时，鼠标点击远控画面会让厂商 Android 12 把全局 IME token 临时切到 Display 0。输入法先报告`visible=false`，但 Display 2 上的代理 Activity 仍可能同时报告`hasWindowFocus=true`。因此下面的旧判据不成立：

```text
IME hidden + host hasWindowFocus == 用户主动收起
```

`1.4.48+129`真机日志明确出现`IME insets visible=false ... windowFocus=true`，紧接着被分类为`Confirmed user IME hide`和`state=closing reason=user_hidden`。系统确实切走了输入法，但最终关闭是客户端误判造成的。

### 20.2 为什么`+130`仍会闪

`+130`增加了源显示鼠标时间戳和120ms隐藏原因分类。它能在鼠标导致IME失焦时避免发布`user_hidden`，随后调用`moveTaskToFront()`和`showSoftInput()`恢复，因此解决了“点击后键盘永久关闭”。但这个方案发生在系统已经开始隐藏动画之后：

```text
鼠标按下
  └─ 20～40ms：IME Insets=false，键盘开始消失
       └─ 120ms：客户端确认最近有鼠标事件
            └─ 重新聚焦并显示IME
```

最终状态仍是`visible`不等于视觉连续。只要日志出现一次`IME insets visible=false`，用户就会看到键盘闪一下。因此`+130`只能作为原因分类和失败恢复兜底，不能作为最终体验方案。

### 20.3 最终的主键手势前置守护

源Activity在把事件交给Flutter前完成分类：

```text
SOURCE_MOUSE事件
  ├─ secondary：BUTTON_SECONDARY / secondary actionButton
  │    └─ 立即取消IME守护，交给右键转发器
  └─ primary down：
       ├─ ACTION_DOWN 且不是secondary
       └─ ACTION_BUTTON_PRESS + BUTTON_PRIMARY
            └─ Manager校验状态和sourceDisplayId
                 └─ KeyboardProxyActivity提前保持IME
```

不能只用`buttonState & BUTTON_PRIMARY`判断左键。当前PAD和ADB测试均观察到合法鼠标`ACTION_DOWN`的`buttonState=0 / actionButton=0`；如果漏掉，保护不会启动，仍会回到`+130`的隐藏后恢复。最终规则是：鼠标`ACTION_DOWN`只要没有明确标记为secondary，就按主键按下处理。

Activity的保护窗口遵循以下参数和边界：

- 主键按下：保护截止时间为当前时间加650ms，并立即执行一次。
- 保护期间：每48ms维持代理task、EditText焦点和当前IME显示。
- 主键抬起/取消：若保护仍有效，将尾部窗口缩短为180ms，覆盖系统在UP之后到达的焦点切换。
- secondary：立即把截止时间清零并移除保护Runnable。
- `activate/parkForReuse/hideIme/onDestroy`：全部清零并移除Runnable，不允许跨request残留。
- 保持过程只调用`showSoftInput()`，不调用`restartInput()`；已有中文拼音组合和InputConnection不会被重建。

延迟分类仍保留为异常兜底，鼠标时间窗为2200ms；它不再承担正常左键路径，正常验收日志不应出现`IME insets visible=false`。

### 20.4 右键为何必须与左键守护隔离

PAD物理鼠标右键有两类输入：

1. `MotionEvent.ACTION_DOWN/BUTTON_PRESS + BUTTON_SECONDARY`；
2. Android把右键解释为鼠标来源的`KEYCODE_BACK`。

`PhysicalMouseRightButtonForwarder`把两类输入统一转成远端`right down/right up`。`+134`曾在所有鼠标事件上高频移动键盘task，右键down与up之间发生焦点切换，up可能无法回到源Activity，远端就会一直保持右键按下并表现为卡住。最终实现一旦识别secondary，先取消IME保护再发送右键；右键down/up期间不做任何键盘task抢焦。

回归判断必须同时满足：

- 每个右键down后都能观察到对应up；
- 右键过程中没有`primary_mouse_guard`持续输出；
- Flutter收到`on_physical_mouse_button`的`down/up`各一次；
- 远端没有停留在右键按下状态。

### 20.5 版本演进与结论

| 构建号 | 处理方式 | 现场结论 |
|---|---|---|
| `+130` | 鼠标时间戳 + 120ms隐藏后恢复 | 不再永久关闭，但肉眼可见闪烁 |
| `+131/+132` | 调整窗口和焦点标志 | 未阻止厂商系统撤销IME焦点 |
| `+133/+134` | 扩大主动task抢焦范围 | 仍不稳定；`+134`破坏右键up，出现卡住 |
| `+135` | 撤销全事件抢焦 | 右键恢复，左键仍是隐藏后重开 |
| `+136` | 只在主键手势期间前置守护 | 实体鼠标通过；发现无按钮位DOWN兼容缺口 |
| `+137` | 未标secondary的鼠标DOWN也视为主键 | 最终真机验收版本 |

### 20.6 延迟恢复兜底与明确关闭

```text
远控源显示收到 SOURCE_MOUSE
        │
        ├─ Manager状态为 opening/visible？
        ├─ event display == sourceDisplayId？
        └─ 记录 elapsedRealtime
                │
IME随后报告隐藏 ─┴─ 120ms分类
        │
        ├─ 最近2.2秒有当前request的源显示鼠标事件 → 恢复IME，状态保持visible
        ├─ 代理窗口真实失焦                         → 恢复IME
        └─ 两者都不是                              → 用户主动收起，正常close
```

主、副屏两个远控 Activity 都在`dispatchTouchEvent`和`dispatchGenericMotionEvent`入口记录事件，但只接受 Android 原生`InputDevice.SOURCE_MOUSE`。记录早于物理鼠标右键兼容层消费事件，因此左键、右键、移动和滚轮都能作为异常失焦的恢复证据；显示 ID 不匹配、代理已隐藏或普通触摸不会刷新时间。正常左键由20.3的前置守护处理，本段只在系统仍然强制隐藏IME时兜底。

恢复中使用`restoreImeInProgress`做单飞门禁。开始恢复后，后续`visible=false`只更新日志，不重复移动 task；只有输入法重新真实可见，或用户/生命周期明确关闭时才清除门禁。

- 用户再次点击远控栏“键盘”：Flutter直接发`keyboard_proxy_close(requestId)`，不经过隐藏原因猜测。
- 输入法自身返回/收起且没有源显示鼠标事件：仍按`user_hidden`关闭。
- HOME或任务切换：`onUserLeaveHint()`只标记待确认；代理 Activity 真正进入`onStop()`后才按`home_pressed`或`keyboard_host_stopped`释放。跨屏鼠标焦点变化如果没有停止 Activity，就不能冒充 HOME。
- 远控页面退出、会话销毁和目标显示移除：继续走原有 release 通道，不受鼠标保持逻辑影响。

### 20.7 真机闭环与后续门禁

设备`192.168.3.63:5555`，PAD`1.4.48+137`：

1. 覆盖安装后系统回读`versionName=1.4.48 / versionCode=137`，固定签名不变。
2. 覆盖远控Display 0→键盘Display 2，以及远控Display 2→键盘Display 0两个方向。
3. 左键产生`onPointDownImage PointerDeviceKind.mouse`，保护日志为`primary_mouse_guard`；IME在进入`visible`后没有再产生`IME insets visible=false`，因此不存在隐藏帧和视觉闪烁。
4. 多次实体右键均观察到`on_physical_mouse_button right down`和`right up`完整成对，没有卡住。
5. 左右键测试后继续使用键盘输入、退格，`keyboard_proxy_commit_text`和`keyboard_proxy_key VK_BACK`仍正常，说明保护没有破坏InputConnection。
6. 用户现场确认“左键不影响键盘，右键功能正常”。

以后修改跨屏键盘、物理鼠标或Activity生命周期时，必须同时验证“左键不闪”“右键down/up完整”“键盘按钮仍能关闭/重开”“HOME后可重新打开”。只看最终状态、只看画面或只看`hasWindowFocus()`都不能作为通过依据；正常左键路径中出现任何一次`IME insets visible=false`都应判为回归。

## 21. 触摸保持、工具栏收起与首次认证（2026-08-04，PAD 1.4.49+153）

### 21.1 触摸与物理鼠标不是同一输入源

`+137`只在`SOURCE_MOUSE`上建立主键前置守护，因此实体鼠标左键不会关闭键盘，但PAD手指产生的`SOURCE_TOUCHSCREEN`完全绕过该入口。`+150`把主、副屏Activity的事件入口统一为`onSourcePointerEvent`，同时接受鼠标和触摸；secondary仍单独取消保护，保持右键down/up路径不变。

真机证明仅统一事件入口仍不足：触摸滑动会让厂商Android把远程源Activity设为新的可聚焦窗口，系统因此撤销对面Display的IME焦点。`STATE_ALWAYS_VISIBLE`和立即恢复只能缩短消失时间，日志仍出现一次`IME insets visible=false`，不能按“最终又显示了”判定通过。

### 21.2 双屏焦点所有权

最终规则如下：

```text
键盘 opening / visible，且 sourceDisplayId != targetDisplayId
        ↓
源远控Activity：FLAG_NOT_FOCUSABLE（仍然可触摸）
键盘代理Activity：持有目标Display输入焦点和IME
        ↓
源屏手指/鼠标MotionEvent继续进入Flutter并传给远端
但源窗口不会夺走IME焦点
        ↓
键盘hidden / 打开失败 / Display移除 / App后台 / 会话释放
        ↓
清除源Activity FLAG_NOT_FOCUSABLE
```

严禁同时设置`FLAG_NOT_TOUCHABLE`，否则虽然键盘稳定，远程画面也无法操作。该规则只用于双屏；单屏回退必须维持普通窗口焦点模型。

### 21.3 收起状态

- 点击底栏“收起”后，如代理为`hidden`则自动执行既有键盘打开流程。
- 已处于`opening/visible/closing`时不重复请求。
- 收起后的展开按钮固定在源屏右下角，透明度50%。`+153`不再使用自带16dp外边距的`endFloat`，而是用Scaffold实际可绘制宽高减去按钮宽高计算坐标，因此右侧和底部间距严格为0，同时仍避开Android系统导航栏。
- 收起动作只在代理状态明确为`hidden`时打开键盘；`opening/visible/closing`均不重开、不调用`restartInput`，避免已经打开的键盘闪动、输入连接重建或中文组合态丢失。
- 点击展开按钮只恢复底栏，不关闭键盘。
- 展开后点击“键盘”仍必须正常进入`closing→hidden`并恢复源窗口可聚焦。

### 21.4 首次主屏密码输入

认证前预创建跨屏代理可能抢走Flutter密码框输入连接。`+152/+153`只在默认主屏使用`deferDefaultDisplay=true`，从非默认副屏启动时仍允许认证前预创建；该方案后来被证明不完整，已由第22节的`1.4.50+155`规则取代。当前规则是不区分显示屏，统一等待`pi.isSet`确认认证完成后再prepare。

密码输入验收必须使用卸载后全新安装，不能用已记住密码的最近访问卡片代替。只能输入不提交的测试字符串，并从UI层确认TextField真实持有内容；认证阶段日志不得出现`Preparing keyboard proxy`或代理Activity激活。

### 21.5 真机验收

设备`192.168.3.63:5555`，PAD`1.4.49+152`：

1. 收起工具栏后Display 2键盘真实可见，Display 0右下角为50%透明展开按钮。
2. Display 0注入touchscreen点击和500毫秒滑动，Flutter收到`PointerDeviceKind.touch`；日志中`IME insets visible=false`为0，`mInputShown=true`。
3. 展开后点击键盘，Manager进入`closing→hidden`并打印`source window focusable=true`，`mShowRequested=false`。
4. 卸载并全新安装后，主屏首次密码框实际接收测试文本；认证前只有`Defer keyboard proxy preparation on default display until authentication`，没有代理Activity启动。

以后相关修改必须同时满足：鼠标左键不闪、右键down/up成对、PAD触摸不闪、收起自动开键盘、主动关闭可恢复源窗口、首次主屏密码可输入。任一正常源屏操作出现`IME insets visible=false`均视为回归。

## 22. 副屏密码输入失效根因与最终认证门禁（2026-08-04，PAD 1.4.50+155）

### 22.1 现场证据

本次密码页面实际位于Display 2，系统状态同时满足：Flutter `EditText`仍为focused、输入法`mShowRequested=true`、`mInputShown=true`、served connection类型仍显示Flutter。随后日志出现：

```text
Preparing keyboard proxy source=2 target=0 request=1
KeyboardProxyActivity started on Display 0
commitText on inactive InputConnection
commitText on inactive InputConnection
```

这说明“键盘可见”和“密码输入连接有效”不是一回事。代理Activity启动改变了跨Display输入连接所有权，输入法后续仍向旧Flutter connection提交，Android只能丢弃并报告inactive。

### 22.2 为什么旧保护失效

旧实现把“source display是否为DEFAULT_DISPLAY”错误地当成“是否处于认证阶段”：

```text
Display 0认证 → deferDefaultDisplay命中 → 不创建代理
Display 2认证 → 条件不命中           → 提前创建Display 0代理 → 密码连接失效
```

认证状态应由`pi.isSet`表达，不能由屏幕编号推断。首次安装只在主屏验证通过，不代表副屏路径正确。

### 22.3 最终实现

- 删除远程页`initState`中的认证前prepare调用。
- `pi.isSet`从false变为true时再调用`keyboard_proxy_prepare`。
- 页面构造时若peer info已经有效，保留同步prepare兜底。
- 认证期不创建、置前或复用`KeyboardProxyActivity`；连接后的跨屏键盘行为保持不变。

以后主屏和副屏都必须执行首次密码验收。认证期间日志只允许Flutter自身`showSoftInput`，不得出现`Preparing keyboard proxy`；认证完成后才允许出现代理prepare。最终产品验收还必须由用户真实输入密码并成功连接，不能只根据键盘可见或UI焦点判定通过。

## 23. HOME后键盘与文件传输可重开（2026-08-05，PAD 1.4.57+162）

旧键盘宿主在另一个屏幕按HOME后进入`onStop`，但`release()`先设为`closing`并等待已退后台IME的隐藏回调；回调可能永远不到，后续`open()`因此一直返回busy。现在停止的宿主由`onHostStopped()`同步清成`hidden`、解除Activity/Display/channel所有权并结束任务，不等待IME回调。预创建但尚未激活的宿主同样处理。

文件传输的独立`singleInstance`任务过去在HOME后仍存活但不可见，下一次启动可能只命中隐藏实例。现在非配置变更导致的`onStop`会结束该辅助任务并由Flutter dispose关闭独立文件FFI；远控视频Session继续运行。验收必须覆盖：键盘HOME后连续重开、文件页HOME后连续重开、远控画面不断开、右键和首次密码输入不回归。

## 24. 首页数字键盘与HOME后同宿主复用（2026-08-06，PAD 1.4.59+164）

### 24.1 对第23节键盘结论的修正

`1.4.57`把停止的键盘宿主清成`hidden`并调用`finishAndRemoveTask()`，解决了Manager长期busy，却引入了更底层的问题：厂商Android不保证允许Display 2前台Activity再次启动Display 0的`singleInstance` Activity。现场日志表现为新request进入`opening`，但没有`onActivityReady()`，8秒后固定`open_timeout`；断开重连也不能绕过系统跨屏后台启动限制。

最终规则改为：

```text
HOME导致已激活KeyboardProxyActivity.onStop
        ↓
Manager同步发布hidden，但保留Activity/channel/owner
        ↓
宿主清焦点并设置NOT_FOCUSABLE + NOT_TOUCHABLE，停驻在原task
        ↓
用户再次点击键盘
        ↓
activate同一宿主、清除停驻flags、moveTaskToFront、重新请求IME
```

HOME路径不得`finish()`或`finishAndRemoveTask()`，也不得在HOME手势内马上`moveTaskToFront`，否则分别会造成无法重建或把用户刚打开的Launcher抢走。`onStop()`只处理`active=true`的宿主；尚未显示的预创建宿主保持停驻。远程页退出、App真正后台、Display移除和显式release仍完整销毁。

现场进一步确认，这台Android 12 ROM在HOME后会暂时把全局`appSwitchAllowed`设为false。即使副屏KEMI仍是前台，`PendingIntent.send()`和`ActivityManager.moveTaskToFront()`也可能被静默拦截。双屏客户端因此必须在首次使用跨屏工具时检查`SYSTEM_ALERT_WINDOW`：未授权先显示中文用途说明，再由用户进入系统页授权；该权限只作为Android后台Activity启动例外，不创建悬浮窗。Native层缺少权限时必须返回`cross_display_permission_required`，不能让Flutter进入假`opening`。IME重试还要持续复核窗口焦点，并忽略“HOME回调晚于副屏点击”的旧`onStop`竞态。

文件传输采用同一原则：`FileTransferActivity`在HOME后保留原task和独立FlutterEngine，下一次仅在peer与目标Display一致时更新连接参数并置前原task；显式关闭才销毁。这样不会把文件窗口留成不可见的`singleInstance`，也不会重复创建文件FFI会话。

### 24.2 首页远程ID数字模式

双屏首页在确认设备角色后调用`keyboard_proxy_prepare`，提前在另一屏创建非交互宿主，从而避开第一次点击时的跨屏后台启动拒绝。点击远程ID输入框时传入`inputMode=numeric_id`：

- Android EditText使用`TYPE_CLASS_NUMBER`和`IME_ACTION_DONE`；
- `commitText`和退格分别走`local_id_keyboard_commit_text/local_id_keyboard_key`；
- Dart只接受当前本地ID伪session及匹配requestId的事件，过滤非数字并同步ID模型和实际输入框；
- 不调用`sessionInputString`，因此首页数字不会发送到任何远端；
- 单屏设备不创建跨屏宿主，直接使用本屏`TextInputType.number`。

ConnectionPage销毁时按本地伪session执行release，认证和远程会话不会复用本地ID所有权；远程认证阶段仍严格遵守第22节，不提前创建会抢密码InputConnection的远程键盘宿主。

### 24.3 真机证据

`192.168.3.63:5555`只在Display 2启动`1.4.59+164`：首页预创建日志为`source=2 target=0`，宿主实际在Display 0。首次点击ID后系统`editorInfo inputType=2`，Manager进入`visible`。Display 0按HOME后依次记录：

```text
Keyboard host stopped: park reason=home_pressed task=508 request=2
state=hidden reason=home_pressed
Parked keyboard proxy for reuse
```

最终包先在未授权状态验证一次性流程：说明弹在Display 2，厂商权限列表也留在Display 2；选择“KEMI远程办公”后原点击自动继续，不要求再点键盘。随后键盘页HOME后0.4秒再次点击恢复同一`task=554`并到达`visible`；文件页HOME后同样恢复同一`task=555`。系统日志明确出现`allowed because SYSTEM_ALERT_WINDOW permission is granted`，没有`open_timeout`、重复Activity、FATAL或进程重启。以后验收至少覆盖首页ID、远程键盘和文件页三类调用方：停驻复用路径核对taskId保持不变，显式release后的重建路径核对Display保持正确，并分别覆盖“HOME后立即点击”和“正常关闭后重开”，不能只看Flutter按钮状态。

## 25. 连接密码复用对屏宿主（2026-08-06，PAD 1.4.61+166）

### 25.1 问题边界

第22节通过“认证前不创建远程键盘代理”保护了密码框的Flutter InputConnection，但它也意味着从副屏点击连接后，密码对话框只能依赖本屏系统输入法。如果首页数字键盘刚在另一屏使用过，厂商ROM又临时禁止跨屏Activity启动，密码阶段再次点击键盘可能长时间没有反应。正确目标不是提前创建远程键盘，而是复用首页已经存在的停驻宿主，并给密码输入建立隔离的本地通道。

### 25.2 三种输入模式必须隔离

同一个`KeyboardProxyActivity`现在支持三种明确模式：

```text
numeric_id      → local_id_keyboard_*       → 首页远程ID控制器
local_password  → local_password_keyboard_* → 当前密码TextEditingController
remote          → keyboard_proxy_*          → 已认证远程session
```

`local_password`使用`__kemi_local_password__:<session>`伪session和单独的`KeyboardProxyController`。文字按当前选择区插入；退格优先删除选择区，否则删除光标前一个字符。它不经过`sessionInputString`，所以认证前密码不会误发给远端桌面。对话框关闭时必须先请求关闭代理、有限等待状态回到`hidden`，然后清空回调和伪session。

### 25.3 宿主交接顺序

从首页连接到密码页的固定顺序为：

1. 首页点击连接，关闭`numeric_id`并等待其状态真实变成`hidden`；
2. 保留已经停驻在另一屏的Activity，避免ROM拒绝重新创建；
3. 密码框获得焦点后，以`local_password`激活同一宿主；
4. 用户提交或关闭密码对话框后，释放本地密码回调；
5. 认证成功且收到第一帧后，才prepare正常`remote`模式。

远程页销毁时释放当前实际宿主，而不是只按最初远程session过滤，因为宿主所有权可能经历数字ID、密码和远程三次切换。后续不得把密码事件合并到远程键盘Controller，也不得恢复“`pi.isSet`立刻prepare远程宿主”的旧逻辑；第一帧之前必须继续保护认证输入连接。

### 25.4 稳定证据与回归边界

用户确认`192.168.3.63:5555`当前行为相对稳定。确认时真机`base.apk`与本地11:05构建包完全一致，均为24,579,999字节，SHA-256 `027a10ee9581a1d45b3d322192a9d96eb709137c27e2c3b1d2718fc60835e535`。该包仍使用`1.4.60+165`元数据，因此最终归档提升为唯一版本`1.4.61+166`。

版本统一后固定签名归档为`BIN/KEMI-远程办公-PAD-1.4.61+166-release.apk`，大小24,579,994字节，SHA-256 `29a2ee1c6610fdcec847c8130fbc952bec2f12f8135dd16a8f9ebb2405f02e6d`。业务代码与用户确认稳定的包一致，差异仅为唯一版本/构建号和随原生重编更新的构建时间；`BIN/release`保持原批次。

以后最小回归只需覆盖：副屏首页输入ID→连接→密码框对屏输入及退格→取消一次；再连接并成功出第一帧→远程键盘打开/关闭；最后主屏HOME后从副屏重开键盘。密码阶段不得出现远程字符发送，第一帧后不得残留`local_password`回调，鼠标左右键和远程画面不得因键盘宿主切换而失效。

## 26. 当前唯一有效的跨屏键盘与文件窗口规则（2026-08-06）

本节覆盖第20～25节中的临时方案，是后续修改必须遵循的**唯一有效结论**。旧节保留用于追踪根因；特别是第21.2节“给远控源Activity设置`FLAG_NOT_FOCUSABLE`”属于短期实验，已被撤销：它会让Android把远控源窗口视为非法输入目标，并在物理鼠标/Flutter输入分发场景触发`InputDispatcher`等待甚至ANR。稳定版本中远控源Activity始终保持可聚焦，键盘保持通过代理状态机、源端指针证据和IME可见性恢复完成，不能再靠改源窗口焦点标志实现。

### 26.1 目标与显示屏选择

- 双屏：从当前远控页面所在显示屏发起，在**另一块有效显示屏**创建或激活键盘宿主；远控在Display 0则键盘在Display 2，反之亦然。
- 单屏：不创建跨屏宿主，使用当前屏原生输入法，不能因为没有副屏而卡在`opening`。
- 每次激活都要核对`sourceDisplayId`、期望`targetDisplayId`、`KeyboardProxyActivity`真实`displayId`和IME token所属显示屏。Manager缓存的目标值不是事实来源。
- 首页远程ID、连接密码和已连接后的远程输入共用宿主能力，但必须隔离输入通道：`numeric_id`、`local_password`、`remote`三种模式不可混用、不可把认证文字发送到远端。

### 26.2 认证、HOME与“再次点不开”的根因闭环

认证前不能预创建或抢占远程输入代理。此前主屏/副屏密码“键盘显示但字符输入不进去”的根因，是代理Activity改变了IME的输入连接所有权，Flutter密码框随后收到`commitText on inactive InputConnection`。认证状态以`pi.isSet`和首帧会话状态判断，不能以Display 0/2判断。

用户按HOME、在另一屏关闭键盘或把主屏应用退后台后，Android可能保留一个不可见的`singleInstance`任务、销毁其`InputConnection`，或暂时禁止跨屏新建Activity。旧实现仍把它当作正在打开的宿主，新的`open()`返回busy，Flutter一直转圈；重连不能修复，重启App才恢复。

当前规则是：

1. `onUserLeaveHint()`只记录HOME意图，真正的`onStop()`才确认宿主是否已停驻；不能把普通鼠标焦点变化误判成HOME。
2. 对已激活宿主立即发布`hidden(home_pressed)`，清掉本次IME请求和过期回调；但为避免Android 12从副屏重建主屏任务被拦截，保留同一宿主任务、channel和显示屏归属为可复用的停驻实例。
3. 再次点击时只能以新的`requestId`激活该停驻实例、清除停驻窗口标志、核对真实Display后重新请求IME；旧请求的Insets/`onStop`回调不得覆盖新请求。
4. 显式关闭、远控页退出、会话销毁、App真正后台或目标显示屏移除才完整释放任务、channel和输入回调。已释放后下次必须新建，不能复用弱引用中的旧Activity。
5. Android 12双屏ROM第一次需要跨屏任务恢复时，`SYSTEM_ALERT_WINDOW`仅作为系统允许后台跨屏启动的许可；它不创建悬浮图标，也不能被当作键盘实现本身。没有许可时原生层返回`cross_display_permission_required`，Flutter必须回到`hidden`并显示中文说明，绝不能停在假`opening`。

### 26.3 键盘保持与物理鼠标

键盘一旦进入`opening/visible`，在远控显示区域的正常操作不能把它关闭：PAD手指点击、单指/多指操作、物理鼠标移动、左键点击、左键拖动均继续转发远端，同时保持键盘。关闭来源只能是用户再次点击“键盘”、目标屏输入法自身明确收起且没有源端操作证据、HOME/任务切换、会话退出、App后台或显示屏移除。

实体右键不能套用左键的IME守护。此前右键被源端“保键盘”逻辑吞掉或只发down不发up，表现为远端右键卡住/闪一下。稳定链路为：主、副屏`dispatchTouchEvent`和`dispatchGenericMotionEvent`先记录源于`SOURCE_MOUSE`的事件；左键短时作为IME恢复证据；右键独立走`onSourceSecondaryMouseEvent`，完整转发`down → up`并取消不应保留的左键守护。因此验收必须同时确认“左键不让键盘消失”和“右键完整弹出并立即释放”，两者缺一不可。

### 26.4 工具栏与文件传输窗口

- “收起”是显式工具栏状态，不是系统键盘状态。收起时若键盘已经`opening/visible/closing`，保持原状；仅`hidden`时才打开键盘。收起后的展开按钮紧贴远控源屏右下角、无额外空隙、约50%透明，点击仅展开工具栏，不关闭键盘。
- 文件按钮是独立的开关：打开时使用和键盘一致的背景色，第二次点击关闭并恢复原色。目标屏用户主动关闭、Activity销毁或会话退出时，原生`FileTransferActivity`通过状态channel通知源页面，按钮必须自动恢复关闭态。
- 双屏文件传输使用另一屏的独立`FileTransferActivity`、FlutterEngine和文件FFI会话；它是90%×78%的真实非模态窗口，窗口外主屏桌面和远控画面仍可操作。它绝不能盖成透明全屏输入层，也不能因为打开文件窗口而中断视频Session。
- HOME后的文件窗口采用与键盘相同的“停驻后复用 / 显式释放后重建”生命周期。不得把隐藏的旧`singleInstance`当作已显示窗口，也不得不加释放地重复创建独立文件Session。

### 26.5 固定验收矩阵

每次改动键盘、文件窗口、鼠标、Activity flag或跨屏权限，必须在双屏和单屏各执行一次：

1. 副屏首页输入数字ID → 连接 → 密码框输入、退格、取消；主屏再完整重复一次。
2. 连接成功后两个方向各打开/关闭远程键盘，PAD触摸、鼠标左键、拖动、滚轮均不让键盘消失；实体右键必须有完整down/up，远端不能卡住。
3. 主屏按HOME或在另一屏主动关闭IME后，从副屏连续打开键盘、文件窗口至少三次；不得转圈、`open_timeout`、迁移到错误屏或要求重启App。
4. 文件窗口由按钮关闭、目标窗口关闭、HOME后重开三种路径分别验证；远控视频、主屏桌面和文件独立Session均持续正常。
5. 拔掉副屏或仅启动单屏后，键盘/文件回落本屏且状态不会残留。检查`dumpsys activity activities`、`KeyboardProxyActivity`真实display/task、`FileTransferActivity`以及日志中的requestId，而不是只看Flutter图标颜色。

### 26.6 修改红线

- 不得根据`hasWindowFocus()`、全局Insets或Flutter按钮状态单独判断键盘真实状态；以目标代理Activity、IME和匹配`requestId`的原生事件共同判定。
- 不得恢复认证前远程代理预创建，不得把三种输入模式合并，不得用`restartInput()`替代状态修复。
- 不得为了保键盘而拦截远控触摸/鼠标或修改远控源Activity为不可聚焦；远控操作与IME保持必须并行工作。
- 不得用扩大/全屏透明文件Activity解决布局问题；文件窗口必须保持真实非模态边界。
