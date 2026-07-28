# STK 双屏对战 — 完整需求文档 v2.0

> 整理自 2026-07-18 ~ 2026-07-19 全部聊天记录
> 关键参考：`STK_REFERENCE.md`（STK 源码全链路考证）

---

## 零、核心原则

1. **不动 STK 原有选择界面和操作逻辑** — 选车用 KartSelectionScreen，选赛道用 TrackSelectionScreen
2. **两屏永远是两个独立的世界，只是通过消息同步对应事件**
3. **所有操作体验和单屏 STK 完全一致** — 方向盘按住就能跑、前轮转向、正常碰撞

---

## 一、双屏渲染架构

| 项目 | Display 0（主屏） | Display 2（副屏） |
|------|:-----------------:|:-----------------:|
| 玩家 | Player 0 (kartA) | Player 1 (kartB) |
| Camera | Camera 0 | Camera 1 |
| 视角 | 独立追尾视角 | 独立追尾视角 |
| 帧率 | 60Hz 目标 | 60Hz 目标 |

- 非镜像！两个屏幕看到各自玩家的独立视角
- Android Presentation API 管理 Display 2，共享 EGL Context
- 渲染路径：`shader_based_renderer.cpp` Camera 0→Display 0, Camera 1→Display 2

---

## 二、操控方式

### 2.1 虚拟方向盘（STK 原版方式）

```
┌──────────────────────────────────────────────────┐
│  [⏸暂停]  [🔄救援]                               │  左上角小按钮
│                                                    │
│                                          ┌────┐   │
│                          道具使用🔥     │ N₂ │   │
│                                          ├────┤   │  右上 2×2 按钮区
│                          后视镜👁       │ 漂 │   │
│                                          └────┘   │
│                                                    │
│     ┌──────────┐                                  │
│     │          │                                  │
│     │ 🎯方向盘 │         3D 游戏画面               │
│     │  按住=跑  │                                  │
│     │ 左/右=转向│                                 │
│     │ 下滑=刹车 │                                  │
│     └──────────┘                                  │
└──────────────────────────────────────────────────┘
```

- **方向盘** 左下角 (~35% 屏幕高度)
  - X 轴 → 左/右转向（死区 0.1 + 灵敏度）
  - Y 轴 → 上半=加速/下半=刹车
  - **按住方向盘就加速** — STK 默认模式，无独立油门条
- **功能按钮** 右下角 2×2 网格：NITRO / DRIFT / FIRE / LOOK_BACK
- **两屏操作互不干扰** — 各自触摸各自的方向盘，Control 分开

### 2.2 触控路由

- SDL 标准路径：`touchDeviceId` → `routedDevId` → `DeviceManager`
- `m_multitouch_device` → Player 0, `m_multitouch_device_2` → Player 1
- Display 0 触控 → Player 0 的方向盘，Display 2 触控 → Player 1 的方向盘

### 2.3 操控体验（对标单屏 STK）

- ✅ 按住方向盘就能跑（不是单独油门条）
- ✅ 前轮转向（不是后轮 or 四轮）
- ✅ 松手减速停车
- ✅ 氮气加速、漂移、道具使用、后视镜
- ✅ 暂停、救援按钮

---

## 三、选单流程（王者荣耀风格主从界面）

### 阶段 1 — 选赛车（双屏独立渲染 + 共享状态）

| Display 0（主屏） | Display 2（副屏） |
|:---|:---|
| 独立渲染 P0 的选车界面 | 独立渲染 P1 的选车界面 |
| P0 方向盘左右 → 切赛车 | P1 方向盘左右 → 切赛车 |
| P0 按 NITRO → 确认 | P1 按 NITRO → 确认 |
| 底部显示 "P1: 等待中..." | 底部显示 "P0: 等待中..."（消息同步） |

**架构**：
```
共享状态层 (GameState 全局变量)
  g_kart_p0_idx, g_kart_p0_confirmed
  g_kart_p1_idx, g_kart_p1_confirmed
  g_selection_phase
       │                    │
       ▼                    ▼
  Display 0 渲染          Display 2 渲染
  P0 的选车界面           P1 的选车界面
```

**关键设计决策**：
- ❌ 不走 STK 的 `KartSelectionScreen`（那是单屏多人共享模式，不合需求）
- ✅ 走**双屏独立渲染 + 全局共享状态**：每个屏渲染各自的选车 UI，读各自的状态
- ✅ 渲染方式：`irr_driver.cpp` 菜单阶段，每屏用 Irrlicht 2D 原语绘制文字/矩形（不自己发明 UI 框架）
- ✅ 输入：`MultitouchDevice` 方向盘左右 → 切赛车，NITRO → 确认
- ✅ 消息同步：同一进程内全局变量即天然共享，无需消息队列

**渲染内容（每屏独立）**：
```
┌──────────────────────────┐
│    选择你的赛车           │
│                          │
│  ◀  [Tux]  ▶            │  ← 方向盘左右切赛车
│                          │
│  P0: Tux  ✓ 已确认       │
│  P1: 等待中...           │  ← 消息同步状态
│                          │
│  ◀ ▶ 切赛车  NITRO 确认   │
└──────────────────────────┘
```

### 阶段 2 — 选赛道（主屏主导）

| Display 0（主屏） | Display 2（副屏） |
|:---|:---|
| 显示 **STK 原生 TrackSelectionScreen** | 显示 **"等待房主选赛道..."** |
| P0 选择赛道 + 确认 | 被动等待，只读状态 |

- P0 是"房主"，负责选赛道
- Display 2 不渲染选赛道界面，只显示等待提示

### 阶段 3 — 确认开赛

- 两屏都已确认选车 + 赛道已选 → 加载地图
- 3-2-1 倒计时（两屏都渲染 `drawGlobalReadySetGo`）
- 进入比赛

---

## 四、比赛流程

- 双方加入地图 → 3-2-1 倒计时 → 比赛开始
- 两屏同时看到倒计时
- 道具箱可拾取（距离检测），拾取后消失 5 秒重生
- 赛道边界检测（`findRoadSector` → `UNKNOWN_SECTOR` 时减速 + 推回）
- 比赛结束显示排名

---

## 五、已完工项（✅）

| 功能 | 说明 |
|------|------|
| 双屏独立视角 ✅ | Camera 0→Display 0, Camera 1→Display 2 |
| 虚拟方向盘(两屏) ✅ | 两屏都有，独立渲染 |
| 触控分离 ✅ | SDL 统一路径，按 routedDevId 分流 |
| 功能按钮 ✅ | NITRO/DRIFT/FIRE/LOOK_BACK 各屏独立 |
| 双屏 HUD ✅ | minimap + timer + player list 全部两屏渲染 |
| 3-2-1 倒计时 ✅ | 两屏都渲染 drawGlobalReadySetGo |
| 比赛结束 ✅ | 两屏都渲染 drawGlobalGoal |
| 双光标 ✅ | CGUIEnvironment Hovered[2]/Focus[2] |
| 自动确认去掉 ✅ | kart_selection.cpp line 1070 |
| STK 参考文档 ✅ | STK_REFERENCE.md (源码全链路考证) |

---

## 六、待完工项（⬜）

| 优先级 | 功能 | 说明 |
|:--:|------|------|
| 🔴 P0 | **选车流程（阶段1）** | push KartSelectionScreen, join 两个 player, 双光标独立操作 |
| 🔴 P0 | **选赛道流程（阶段2）** | push TrackSelectionScreen(P0), Display 2 显示等待 |
| 🔴 P0 | **显示 STK 原生菜单界面** | 菜单阶段 irr_driver 双屏渲染 GUIEngine::render() |
| 🟡 P1 | 赛道边界严格碰撞 | 利用 TriangleMesh 的物理碰撞，不只是 quad 检测 |
| 🟡 P1 | 场景物体碰撞 | 卡丁车 vs 建筑/树/赛道边缘的物理碰撞 |
| 🟢 P2 | 赛道材料影响 | 草地/沙地减速效果 |
| 🟢 P2 | AI 对手 | 加入 AI kart 对战 |
| 🟢 P2 | 赛后排名界面 | 比赛结束显示名次 |

---

## 七、技术架构速查

### 7.1 关键文件

| 文件 | 作用 |
|------|------|
| `src/main.cpp` | 入口 + `enterKartSelectionPhase()` / `startSelectedRace()` |
| `src/graphics/irr_driver.cpp` | 双屏渲染（菜单阶段 else-branch, 比赛阶段 Camera loop） |
| `src/graphics/shader_based_renderer.cpp` | 比赛渲染 Camera 0→Display 0, Camera 1→Display 2 |
| `src/input/multitouch_device.cpp/hpp` | 触控按钮定义 + 状态更新 |
| `src/input/device_manager.cpp` | `m_multitouch_device` / `m_multitouch_device_2` 管理 |
| `src/input/input_manager.cpp` | `EET_TOUCH_INPUT_EVENT` 按 DeviceID 路由 |
| `lib/irrlicht/source/Irrlicht/CGUIEnvironment.cpp/h` | 双光标 `Hovered[2]` / `Focus[2]` |
| `src/states_screens/kart_selection.cpp` | 选车界面（去掉自动确认） |
| `android/android_native_dual_screen.cpp` | EGL 双屏管理 |

### 7.2 物理模型（STK 源码考证）

参见 `STK_REFERENCE.md §4`：
- 引擎力：`applyEngineForce(forward * engine_power * accel)` 
- 空气阻力：`friction = |v| * sqrt(|v|) * 5`（二次阻力，自稳定）
- 滚动阻力：`compense = 39.33 * |v| * mass / 350`
- 转向扭矩：`applyTorque(0, steer * 3500, 0)`
- 车轮动画：`spin = distance / radius`, 前轮叠加 `steer * 30°`

---

## 八、当前阻塞问题

1. **GLSurfaceView 吃掉所有触摸** — TouchControls View 覆盖层收不到事件
   - 解决方案：操控逻辑放回 `KartGLView.onTouchEvent()`（正在进行中）
2. **选单界面未接通** — 当前 `startDualScreenRace()` 直接开赛，无选车/选赛道
3. **菜单阶段双屏渲染 GUI** — `irr_driver.cpp` 的 else-branch 已实现双屏 GUI 渲染，只需保证选单界面 push 后能正常显示

---

## 九、不做的事

- ❌ 不自己设计选车界面（用 STK 原生 KartSelectionScreen）
- ❌ 不自己设计物理模型（已用 STK 的 2D 刚体简化版）
- ❌ 不修改 STK 原有 widget 交互逻辑
- ❌ 不做网络对战（当前仅本地双人）
