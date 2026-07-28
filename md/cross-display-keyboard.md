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
3. IME 从可见变为不可见时，状态立即变为 `hidden`。
4. 原生层通知 Flutter 更新按钮颜色。
5. 代理窗口随后释放输入连接并结束，不残留透明 Activity。

不能使用当前 App 屏幕的 `KeyboardVisibilityController` 判断目标屏幕键盘状态。

### 2.5 自动关闭场景

以下任一情况发生时，必须关闭代理键盘并最终回到 `hidden`：

- 用户再次点击键盘按钮。
- 用户在目标屏幕强制收起键盘。
- 远程连接结束或远程页面销毁。
- App 进入后台。
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
| `closing` | 已请求关闭，等待 IME 隐藏或代理销毁 | 激活颜色，暂时禁用 |

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
        +-- 在 targetDisplayId 启动 KeyboardProxyActivity
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
- 启动、关闭 `KeyboardProxyActivity`。
- 接收 Activity 的 IME 状态和输入事件。
- 向正确的 Flutter engine 发布状态。
- 监听 Display 移除事件并关闭代理。

Manager 不持有 Activity 强引用；当前代理使用弱引用或仅由 Activity 主动回调。

### 7.2 `KeyboardProxyActivity`

职责如下：

- 只在指定 `targetDisplayId` 启动。
- 在 `onCreate` 校验实际 `displayId`；不匹配则失败并结束。
- 使用不可见但合法尺寸的原生 `EditText` 获取输入连接。
- 不设置 `FLAG_NOT_TOUCHABLE` 等可能阻止 IME 焦点的 flag。
- 使用 `WindowInsets` 监听目标窗口 IME 的实际可见性。
- 在第一次确认可见后上报 `visible`。
- 可见后检测到隐藏时上报 `hidden` 并 `finish()`。
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

跨屏流程不得修改：

- `_showEdit`。
- `keyboardVisibilityController`。
- `_mobileFocusNode`。
- `SystemChrome` overlays。
- `floatingActionButtonLocation`。
- `_showBar`。

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
- 代理关闭必须真正结束 Activity；当前空实现 `hide()` 不符合要求。

## 10. 打开与关闭时序

### 10.1 打开

1. Flutter 状态为 `hidden`，用户点击键盘按钮。
2. Flutter 调用 `keyboard_proxy_open(sessionId)`，按钮暂时禁用。
3. Manager 计算目标 Display，状态设为 `opening`。
4. Manager 启动 `KeyboardProxyActivity`，传入 `requestId/sessionId/targetDisplayId`。
5. Activity 校验实际 Display，创建输入连接并请求显示 IME。
6. `WindowInsets.Type.ime()` 确认可见。
7. Manager 发布 `visible`。
8. Flutter 将按钮切换为激活色并恢复可点击。

### 10.2 App 主动关闭

1. Flutter 状态为 `visible`，用户再次点击键盘按钮。
2. Flutter 调用 `keyboard_proxy_close(requestId)`。
3. Manager 状态设为 `closing`，请求隐藏 IME 并结束代理 Activity。
4. Activity 检测 IME 隐藏或执行 `onDestroy`。
5. Manager 发布 `hidden`。
6. Flutter 按钮恢复默认色。

### 10.3 用户强制关闭

1. 用户在目标屏幕点击输入法收起按钮或按返回键。
2. Activity 的 IME Insets 从可见变为不可见。
3. Activity 通知 Manager，Manager 发布 `hidden(reason=user_hidden)`。
4. Flutter 按钮恢复默认色。
5. Activity 结束并释放输入连接。

## 11. 失败与超时

- `opening` 超时建议为 2 秒。
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
- 打开失败：2 秒内恢复 `hidden`，按钮不保持激活。

### 12.4 输入

- 中文输入法提交文本只发送一次。
- 英文、数字、空格、符号正常。
- 回车、退格、Tab 正常。
- 关闭代理后输入不再发送。
- 旧会话代理不能向新会话发送输入。

### 12.5 稳定性

- 连续开关 50 次无崩溃、无透明 Activity 残留。
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