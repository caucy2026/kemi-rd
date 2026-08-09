# 暗黑2 PAD 超分辨率实现、性能分析与可移植项目指南

文档状态：基于当前 `kemi/main` 代码、现有真机记录及 2026-08-08 的开源项目资料整理。

适用工程：`/Volumes/ORICO/kemi/kemi-diablo2-android`

## 1. 先说明：当前实现严格来说不是“AI 神经网络超分”

当前 APK 已落地的高清链路是 **AMD FidelityFX FSR1 的空间超分思路**：

1. EASU（Edge-Adaptive Spatial Upsampling）读取当前 800×600 游戏帧，判断局部边缘方向，
   在放大时沿边缘重建像素；
2. RCAS（Robust Contrast Adaptive Sharpening）读取 EASU 的高分辨率结果，根据局部亮度范围、
   噪声和可允许的锐化强度恢复细节；
3. Android PAD 操作层最后叠加，因此摇杆、按钮和高清图标不经过游戏超分，不会被再次放大变虚。

它没有模型权重，也不会凭空生成纹理，所以不是 Real-ESRGAN、FSRCNN 一类神经网络算法。
但它有三个非常适合本项目的特点：

- 只需要当前完整画面，不需要改造暗黑2引擎；
- 不需要运动矢量、深度、抖动序列和上一帧历史；
- 计算全部在 ARM PAD 的 Mali GPU 上进行，CPU 不逐像素跑超分。

本项目后续可以增加真正的神经网络超分，但必须把它作为另一条可切换路径，不能把当前稳定的
空间超分直接删除。

## 2. 项目约束与设计目标

### 2.1 输入和输出

- 游戏原始渲染：800×600，4:3；
- 暗黑2经典逻辑/动画更新：约 25 Hz；
- 目标游戏画面：1600×1200，仍为 4:3；
- PAD/HDMI 画布：1920×1280；
- 布局：1600×1200 游戏画面居中，四周保留 PAD 操作区；
- Android 合成目标：稳定 30 Hz，而不是伪造游戏逻辑帧。

### 2.2 不能牺牲的内容

- 中文位图字体和物品名称必须可读；
- 自动地图的单像素细线不能被抹掉；
- 雨、火焰、角色精灵不能出现持续光环和闪烁；
- 48 kHz 音频不能因为 GPU/CPU 抢占重新出现卡顿；
- 鼠标、触摸和摇杆的反馈延迟不能明显上升；
- 超分 shader 或 FBO 创建失败时必须回退，不能黑屏或崩溃。

## 3. 当前渲染数据流

```text
Windows 暗黑2 / Wine
        │
        │ 800×600 最终游戏窗口纹理
        ▼
XServer / GLRenderer
        │
        │ Pass 1：WindowMaterial 内的 EASU
        │ 12 个源纹理采样/输出像素
        ▼
高分辨率 Android 离屏 FBO（当前按整个 Surface 分配）
        │
        │ Pass 2：RCASEffect
        │ 中心 + 上下左右，共 5 个高分辨率采样
        ▼
1920×1280 Android Surface
        │
        │ Android View 操作层
        ▼
摇杆、血瓶、菜单、技能按钮、鼠标提示等最终叠加
```

关键结论：当前不是“每一帧 CPU 把每个点算一遍”。Java 负责创建 GL 资源、设置 uniform 和
发起 draw call；真正逐像素的 EASU/RCAS 运算由 GPU fragment shader 并行完成。

## 4. 代码位置和职责

### 4.1 模式选择与总开关

文件：`app/src/main/java/com/winlator/XServerDisplayActivity.java`

`setupUI()` 在 KEMI 游戏模式执行：

```java
renderer.setWindowAntialiasing(true);
renderer.setWindowUpscaleMode(WindowMaterial.UPSCALE_FSR1_EASU);
renderer.effectComposer.addEffect(new RCASEffect(0.75f));
xServerView.setTargetFrameRate(30);
```

它把模式限定在暗黑2适配场景，普通 Winlator 窗口仍可走原有路径，减少全局回归风险。

### 4.2 EASU / Scale2x / 最近邻

文件：`app/src/main/java/com/winlator/renderer/material/WindowMaterial.java`

当前有三个模式：

| 值 | 模式 | 用途 |
|---:|---|---|
| 0 | `UPSCALE_NEAREST` | 最近邻基线、故障回退 |
| 1 | `UPSCALE_SCALE2X` | 像素艺术对照组，颜色稳定、文字锐利 |
| 2 | `UPSCALE_FSR1_EASU` | 当前默认，方向性边缘重建 |

EASU 路径从源图周围显式读取 12 个像素。原版算法常使用 `textureGather`，当前实现改成
OpenGL ES 兼容的 `texture2D` 显式读取，以适配该设备较老的 Mali 驱动。

主要步骤：

1. `sourcePosition = uv × textureSize - 0.5`，把输出 UV 映射回源像素空间；
2. 取得当前源像素基点以及小数位置 `pp`；
3. 采样 b/c/e/f/g/h/i/j/k/l/n/o 十二点；
4. 用近似亮度 `G + 0.5 × (R + B)` 估算局部变化；
5. 四个象限分别累积水平和垂直梯度，得到边缘方向；
6. 根据边缘可信度计算椭圆形采样核的长短轴；
7. 对 12 个样本应用方向性 Lanczos 风格权重；
8. 用中心 2×2 的最小值/最大值夹紧结果，避免过冲和明显光环。

这一步的本质不是简单模糊，而是让采样核沿着边缘延伸、垂直边缘收窄，因此人物轮廓、墙体
斜线比普通双线性更平滑。

### 4.3 RCAS 第二阶段锐化

文件：`app/src/main/java/com/winlator/renderer/effects/RCASEffect.java`

每个输出像素读取：中心 e、上 b、左 d、右 f、下 h。处理过程：

1. 计算五点亮度范围；
2. 比较中心亮度与邻居均值，估算局部噪声；
3. 用局部最小/最大颜色计算允许的负 lobe，限制锐化过冲；
4. 对噪声区域减弱锐化；
5. 将中心与四邻域按自适应权重组合；
6. 将 RGB 夹到 0～1，保留中心 alpha。

当前参数是 `0.75 stops`。RCAS 的约定是 0 stops 最强，每增加 1 stop 强度减半：

```text
实际锐化系数 = 2 ^ (-sharpnessStops)
0.75 stops ≈ 0.5946
```

选择 0.75 而不是 0 的原因是：暗黑2包含高对比度位图字、自动地图和大量细碎地面纹理；最大
锐化更容易在文字和栅栏周围制造白边或黑边。

### 4.4 离屏 FBO 与回退

文件：`app/src/main/java/com/winlator/renderer/EffectComposer.java`

`EffectComposer` 创建两个 RGBA RenderTarget，执行 ping-pong 后处理：

- `readBuffer` 接收 EASU 后的完整场景；
- `writeBuffer` 为多个后处理效果预留；
- 最后一个效果直接输出到屏幕；
- FBO attachment 不完整时，绑定默认 framebuffer 并直接 `drawFrame()`，避免黑屏。

目前只有一个 RCAS 效果，但仍分配了两个全屏 FBO。这是下一轮内存和带宽优化的重要位置。

### 4.5 30 Hz 节拍控制

文件：`app/src/main/java/com/winlator/widget/XServerView.java`

GLSurfaceView 使用 `RENDERMODE_WHEN_DIRTY`，再由主线程 Handler 每约 33 ms 请求一次合成。
游戏、指针和 UI 的重复刷新请求会被合并，防止短时间内连续提交多次完整 EASU+RCAS。

这不是把游戏从 25 FPS 插帧到 30 FPS。它只保证 Android Surface、鼠标反馈和最新游戏纹理
以稳定节拍合成。

## 5. 每帧成本估算

以下是数量级估算，用来指导优化，不替代 GPU profiler：

### 5.1 EASU

目标游戏区 1600×1200，共 1,920,000 个输出像素。每像素约 12 次源纹理读取：

```text
1,920,000 × 12 × 30 ≈ 691,200,000 次纹理读取/秒
```

### 5.2 RCAS

当前 RCAS 按整个 1920×1280 Surface 运行，共 2,457,600 像素，每像素 5 次读取：

```text
2,457,600 × 5 × 30 ≈ 368,640,000 次纹理读取/秒
```

EASU 与 RCAS 合计约 10.6 亿次纹理读取/秒。GPU 会利用纹理缓存，实际外部内存访问不会与这个
数字一一对应，但它说明瓶颈更可能是纹理/显存带宽和驱动提交，而不是 Java 或 CPU 算术。

### 5.3 FBO 内存

单个 1920×1280 RGBA8 纹理约 9.375 MiB，两个约 18.75 MiB，不含源纹理、深度、驱动对齐和
系统合成缓冲。

如果把后处理区域裁成 1600×1200：

- 处理像素减少约 21.875%；
- 两个 RGBA8 FBO 约降至 14.65 MiB；
- RCAS 每秒理论采样约从 3.69 亿降至 2.88 亿；
- PAD 两侧黑边和 Android 操作区域不再参与无意义锐化。

因此“裁剪 RCAS 到游戏区”是当前投入产出比最高的优化。

## 6. 已完成的真机阶段与结论

以下内容已合并原路线文档中的版本记录、性能快照与验收结论，本文是本工程超分方案的唯一主文档。

### 6.1 v14：EASU 预览

- 构建版本为 `0.4.0-fsr1-easu-preview`（versionCode 14），能够进入角色存档；
- 800×600 到 1600×1200 正常；
- 人物、石墙和斜边阶梯感下降；
- 中文小字和地图细线偏软，证明只做重建不够，需要后锐化；
- 没有观察到 shader 编译错误、ANR 或 native crash；
- 单次动态负载快照：`Game.exe` 约 210% CPU、Android 应用约 21%、
  SurfaceFlinger 约 21%。这只能证明尚有 CPU 余量，不能代替帧时和带宽测试；
- 真机证据：`docs/evidence/v14-fsr1-menu.png`、`docs/evidence/v14-fsr1-game.png`、
  `docs/evidence/v14-fsr1-map.png`。

### 6.2 v15：EASU + RCAS

- 主菜单、角色页、营地、背包和自动地图可显示；
- 人物、石墙、雨线和背包格线更清楚；
- 没有观察到持续光环或颜色溢出。

### 6.3 v16：不限速连续渲染，不可交付

- 短时间约 42.9 FPS；
- 平均 GL command submit 约 12.72 ms；
- 随后旧 Mali 驱动 RenderThread SIGSEGV。

结论：平均速度看似足够，不代表驱动在高压连续提交下稳定。

### 6.4 v17：30 Hz，稳定边界通过

- 七个连续窗口依次为 29.6、30.0、30.1、30.1、30.1、30.0、30.1 FPS；
- 未记录到 GL crash、ANR 或音频 underrun；
- 测试前后应用和 `Game.exe` PID 保持一致；
- 动态快照中 `Game.exe` 约 124% CPU、Android 应用约 55%、SurfaceFlinger 约 14%，
  整机约有 5.8 个 CPU 核空闲。

### 6.5 v18：32 Hz，拒绝

- 表面帧率达到约 31.5～32.1 FPS；
- 出现显存撕裂，随后应用退出并丢失 ADB 连接。

### 6.6 v19：回到 30 Hz

版本为 `0.5.4-fsr1-rcas-stable-30fps`，已逐页确认主菜单、角色页和真实营地画面，进入游戏
前后 PID 保持一致。当前安全结论是：在该 PAD 旧 Mali 驱动上，EASU+RCAS 的可交付节拍为
30 Hz。要提高到 32/35 Hz，必须先降低处理区域和带宽，再重新做稳定性测试。

对应 Git 记录：

- `cb3cb56 feat: add FSR1 EASU upscale preview`
- `51fe7a2 feat: add stable FSR1 RCAS 30fps pipeline`
- 基线标签：`backup/v13-hd-ui-20260806`

## 7. 当前实现的边界与技术债

1. **不是完整官方 SDK 封装。**当前 EASU/RCAS 是为 GLES/Mali 适配的 GLSL 实现，算法结构
   取自 FSR1，但不是把 AMD 头文件逐字编译到 Android。
2. **RCAS 处理范围过大。**完整 1920×1280 Surface 都进入后处理。
3. **两个全屏 FBO 有冗余。**只有一个后处理时可探索单中间纹理或区域化 framebuffer。
4. **算法选择入口还不完整。**代码有 NEAREST/SCALE2X/EASU，但交付 UI 没有稳定的 A/B
   开关、帧时记录和自动回退策略。
5. **地图和小字仍需专项评价。**自然图像看起来更平滑，不等于位图中文更准确。
6. **当前输入问题与超分是独立问题。**鼠标/触摸事件能否传入 Wine 不应通过降低画质掩盖；
   两条链路必须分别验收。
7. **没有神经网络模型。**不能宣传成“AI 生成高清纹理”；对外应称“GPU 实时边缘自适应超分”。

## 8. 可以借鉴或移植的开源项目

### 8.1 AMD FidelityFX FSR1：继续完善当前路线

官方资料：

- https://gpuopen.com/fidelityfx-superresolution/
- https://gpuopen.com/manuals/fidelityfx_sdk/techniques/super-resolution-spatial/
- https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK

许可证：FSR1 以 MIT 许可提供；本工程已保存
`third_party/FidelityFX-FSR-LICENSE.txt`。

可借鉴内容：

- EASU 常量与精度路径；
- RCAS 锐化/降噪逻辑；
- 输入/output viewport 的裁剪设计；
- FP16 路径和着色器排列方式；
- 品质档位和锐化参数定义。

适配评价：**最高优先级**。现有代码已跑通，只需区域化、精度优化、A/B 和稳定性工程。

### 8.2 NVIDIA Image Scaling（NIS）：空间超分对照组

官方资料：https://developer.nvidia.com/rtx/image-scaling

许可证：官方说明为 MIT。NIS 是跨 GPU 的空间缩放和锐化算法，不依赖 NVIDIA 专用硬件。

可借鉴内容：

- 单阶段方向性缩放 + 锐化；
- scale/锐化系数设计；
- 不依赖历史帧，适合只有最终 X11 帧的场景；
- 可与 EASU+RCAS 在相同输入、相同输出下直接 A/B。

移植方式：将官方 shader 改写为 GLES 3.0 可接受的 GLSL，避免桌面 HLSL/Vulkan 专用语法；
新建 `NIS` 模式，不替换现有 EASU。重点测试老 Mali 的编译器限制、寄存器压力和精度。

适配评价：**高优先级对照**。可能用更少 pass 达到相近清晰度，但是否优于 EASU 必须真机判定。

### 8.3 xBRZ / xBR / SABR / ScaleFX：像素美术路线

xBRZ 是面向低分辨率像素图形的边缘规则放大器。它通常能保持调色板式精灵轮廓，不会像
通用神经网络那样发明地面纹理。

可选实现方式：

- CPU C++/JNI + ARM NEON；
- 将画面按水平条带分给 4/6/8 线程；
- 输出到复用的直接缓冲区，再上传 GL 纹理；
- 或借鉴 Libretro shader 生态中的 GPU xBR/SABR/ScaleFX 实现。

优点：人物精灵、物品图标和斜线通常锐利；结果确定性强。

风险：

- CPU 处理后还要上传 1600×1200 RGBA，产生同步和内存带宽开销；
- 地面、光照、透明火焰不完全属于像素艺术，规则放大可能产生蜡块；
- 字体可能锐利但笔画形状改变；
- 不同仓库的 xBR/xBRZ 许可证不同，选定代码前必须逐仓审核，不能只凭算法名称判断。

适配评价：**中高优先级对照**。适合验证“像素规则算法是否比通用空间超分更符合暗黑2”。

### 8.4 Tencent ncnn：真正 AI 超分的移动推理底座

官方仓库：https://github.com/Tencent/ncnn

许可证：BSD 3-Clause。官方提供 Android、ARM NEON、FP16、INT8 和 Vulkan 支持，无需大型
第三方运行时。

推荐模型方向：

- FSRCNN 2×：结构小、延迟相对低；
- ESPCN 2×：使用亚像素卷积，适合固定 2×；
- 专门为暗黑2训练的轻量残差模型；
- 模型输入只处理 800×600 游戏区，不处理 PAD UI；
- tile 推理并保留重叠边界，避免一次性中间张量过大。

推荐接入方式：

```text
XServer GL 纹理
  → Vulkan/共享图像或尽量少拷贝的输入
  → ncnn FP16 2× 模型
  → 高分辨率纹理
  → 轻量 RCAS（可选）
  → Android Surface
```

第一版可以先允许一次 GPU→CPU→GPU 回读，用来验证画质，但这不能作为最终实时架构。最终必须
尽量让 X 图像、ncnn Vulkan 和合成共享 GPU 侧资源，否则 30 FPS 很可能被拷贝和 fence 吃掉。

适配评价：**真正 AI 路线的首选底座**，但开发量和驱动风险明显高于 FSR1/NIS。

### 8.5 Real-ESRGAN：离线画质上限与训练数据工具

官方仓库：https://github.com/xinntao/Real-ESRGAN

许可证：代码为 BSD 3-Clause；具体模型权重必须单独检查其许可和来源。

本项目不建议直接把通用 x4 Real-ESRGAN 作为实时默认，原因：

- 800×600 每帧推理量过高；
- x4 后再缩到 1600×1200浪费计算；
- 通用模型可能虚构石墙、草地和角色纹理；
- 位图中文容易被改形；
- 帧与帧之间可能生成不同细节，运动时产生闪烁。

适合用途：

- 对固定截图离线处理，建立“理论画质上限”；
- 生成对照图，让团队决定希望保留哪类细节；
- 在版权和数据许可明确的前提下，辅助构造训练对；
- 用其退化模型思路训练更小的暗黑2专用 2× 网络。

适配评价：**研究/离线工具**，不是当前 PAD 实时交付方案。

### 8.6 Anime4K / Anime4KCPP：可研究，但要谨慎

参考仓库：

- https://github.com/bloc97/Anime4K
- https://github.com/TianZerL/Anime4KCPP

它们擅长动画线条和大块平涂内容。暗黑2是预渲染写实像素画面，纹理特征不同，因此只能作为
局部思路参考，不能因为“实时高清”名称就直接采用。

特别注意：Anime4KCPP 仓库存在 GPLv3 许可文件。若把其代码直接链接或合并进闭源/商业 APK，
会带来明显的开源义务。没有完成法律和依赖方式评审前，不应直接并入发布分支。

适配评价：**低到中优先级研究**。可以借鉴边缘细化、去振铃思路，直接移植需谨慎。

### 8.7 cnc-ddraw shader：工程内已有可复用参考

工程资产目录：`app/src/main/assets/dxwrapper/cnc-ddraw-6.6/Shaders/interpolation/`

现有内容包括：

- `fsr.glsl`：FSR EASU 参考；
- `catmull-rom-bilinear.glsl`：较少采样的 Catmull-Rom/bicubic 方案；
- 其他插值 shader 可用于建立低成本对照。

这些 shader 更接近 Windows wrapper 渲染链路。可以提取数学部分，但不能默认它们能原样用于
Android GLSurfaceView；仍需处理 uniform、纹理坐标、GLES 版本和许可证归属。

适配评价：**高价值本地参考**，尤其适合增加 Bicubic/Catmull-Rom 基线。

## 9. 不适合当前项目直接移植的路线

### 9.1 FSR2/FSR3、DLSS、XeSS 等时间超分

时间超分通常需要：

- 当前低分辨率颜色；
- 运动矢量；
- 深度；
- 曝光；
- 抖动偏移；
- 上一帧历史；
- HUD/透明粒子的特殊处理。

当前链路只能取得暗黑2最终 800×600 合成帧，拿不到精灵、地图、粒子和 UI 的运动矢量，也无法
从原版游戏可靠注入相机 jitter。通过光流“猜”运动矢量的成本和伪影风险都过高。

因此它们不能像 FSR1 一样简单放在最终 X11 纹理后面。除非未来替换/重写游戏渲染引擎，否则
不作为主线。

### 9.2 帧插值

暗黑2游戏逻辑约 25 Hz。单纯插值到 60/120 FPS 不会让输入和逻辑真的变快，还可能造成鼠标、
人物和特效错位。当前目标应是稳定 30 Hz、低延迟和清晰，而不是制造虚假高帧率。

### 9.3 大型 GAN/Transformer 每帧推理

SwinIR、Real-ESRGAN 大模型等在桌面 GPU 上适合离线图片恢复，但对该老 Mali PAD 的持续实时
预算过重。即使平均帧率勉强达到，温控、音频抢占、驱动稳定和 99% 帧时也可能不可交付。

## 10. 推荐的后续技术路线

### 阶段 A：把现有 FSR1 做完整

1. 为游戏区建立 1600×1200 region FBO；
2. RCAS 只处理游戏区域；
3. Android 操作层保持原始高清渲染；
4. FBO 创建失败、shader 编译失败、GL error 时自动切回 Scale2x/最近邻；
5. 加入隐藏的开发者 A/B 选项：NEAREST、SCALE2X、EASU、EASU+RCAS；
6. 在 30 Hz 稳定后测试 32/35 Hz，但任何撕裂、驱动退出都回到 30 Hz。

这是交付优先路线。

### 阶段 B：增加低风险算法对照

1. 加入 Catmull-Rom/Bicubic；
2. 移植 NIS GLES 版本；
3. 统一输入、输出、截图和帧时采集；
4. 用中文、地图、角色、雨天、背包五组场景盲评；
5. 不以单张截图决定默认方案，要结合动态闪烁和帧时。

### 阶段 C：xBRZ CPU 对照

1. 固定 2×，避免任意比例复杂度；
2. JNI/C++ 实现，预分配所有缓冲；
3. 4/6/8 线程分别测量；
4. 使用 ARM NEON；
5. 记录 CPU 处理、纹理上传、GPU 合成三个独立耗时；
6. 验证音频 underrun 和输入延迟。

### 阶段 D：真正 AI 原型

1. 用合法持有的暗黑2画面建立训练/验证集；
2. 保留原始 800×600 和高质量离线目标；
3. 中文、地图和 UI 建立独立损失/验证集；
4. 训练 FSRCNN/ESPCN 或小型残差 2× 模型；
5. 导出 ONNX/PyTorch，再用 pnnx 转为 ncnn；
6. 先离线对图，再做 ARM NEON 与 Vulkan 两套 benchmark；
7. 目标是单帧推理给合成留出余量，而不是刚好卡在 33 ms；
8. 模型效果不稳定时允许 EASU+RCAS 作为默认和回退。

## 11. AI 专用模型训练建议

### 11.1 数据构造

不能简单拿 800×600 用双三次放大当“高清真值”。推荐：

- 从合法游戏素材或更高质量渲染源建立高分辨率目标；
- 构造接近 Wine/X11 输出的降采样、色彩和压缩退化；
- 单独采样中文、数字、地图线、物品框、角色轮廓和透明特效；
- 训练集按场景划分，测试集不能与训练帧连续相邻，防止记忆。

### 11.2 损失函数

建议从保真优先开始：

- L1/Charbonnier：保证颜色和结构；
- edge loss：保护地图和文字边缘；
- perceptual loss：低权重，用于场景纹理；
- temporal consistency：如果训练连续帧，用于抑制闪烁；
- 不建议一开始加入强 GAN loss，它最容易生成不存在的细节。

### 11.3 模型约束

- 只做固定 2×；
- 尽量少通道、少残差块；
- 支持 FP16；
- INT8 必须逐场景验证文字和暗部，量化误差可能吞掉细线；
- tile 之间至少保留与感受野匹配的 overlap；
- 输出要做颜色范围和 gamma 一致性检查。

## 12. 验收矩阵

默认算法进入交付版的硬门槛是：同一存档、同一相机位置完成 A/B；画质提升明确；P95 帧时
不超过 40 ms；连续 10 分钟无音频回归、驱动退出和明显输入延迟。任一项不通过，就回退最近邻、
Scale2x 或最近的稳定版本，不能只凭单张截图交付。

### 12.1 画质

- 主菜单中文笔画完整；
- 角色页名字与属性数字清晰；
- 背包格线和物品数量不粘连；
- 自动地图细线连续；
- 人物、木栅栏、石墙斜边阶梯减少；
- 雨、火焰和奔跑时无明显闪烁；
- 暗部没有被锐化成彩噪；
- 不出现持续白边/黑边。

### 12.2 性能

- 连续 10 分钟动态场景；
- 平均、P95、P99 帧时；
- 不低于稳定 30 Hz 目标；
- 无 Mali driver crash、GL error、ANR；
- 无音频 underrun；
- 温度升高后仍稳定；
- 切后台、双屏键盘、恢复前台后 GL 上下文正常。

### 12.3 操作

- 摇杆到人物开始移动的延迟；
- 真实鼠标移动与游戏手形光标同步；
- 鼠标左/右键、触摸、技能和拾取在超分开关前后行为一致；
- 操作层不被 RCAS 锐化或裁剪；
- 4:3 画面坐标与 1920×1280 触摸坐标严格对应。

### 12.4 回退

- shader 编译失败自动回退；
- FBO incomplete 自动回退；
- 驱动黑名单可禁用 AI/Vulkan；
- 用户存档和资源不依赖某一种超分算法；
- 回退后仍能进入游戏和完成基本操作。

## 13. 许可证与商业发布清单

每引入一个项目，需要同时记录：

1. 仓库 URL、具体 commit/tag；
2. 代码许可证全文；
3. 修改过的文件清单；
4. APK About/NOTICE 中的归属；
5. 模型结构许可证；
6. 模型权重许可证；
7. 训练数据来源及使用权；
8. 是否存在 GPL/AGPL 的链接或派生义务；
9. 商标名称是否可用于产品宣传。

特别注意：推理框架是 BSD/MIT，不代表下载的模型权重也能商用；算法论文公开，也不等于任意
第三方实现都采用相同许可证。

## 14. 最终建议

对当前 PAD，建议按以下优先级投入：

1. **立即做：**裁剪到 1600×1200 游戏区的 EASU+RCAS，保持稳定 30 Hz；
2. **紧接着做：**NIS 和 Catmull-Rom 对照，建立可重复 A/B；
3. **验证价值：**xBRZ/像素 shader，重点看中文、地图和角色轮廓；
4. **研究原型：**ncnn/Vulkan + FSRCNN/ESPCN 专用 2× 模型；
5. **只做离线参考：**Real-ESRGAN；
6. **暂不投入：**FSR2/3、DLSS、XeSS 时间超分和实时大型 GAN。

产品表述也应准确：当前版本是“GPU 实时边缘自适应超分 + 对比度自适应锐化”；只有在 ncnn
模型真正接入并通过动态帧稳定性验收后，才适合称为“AI 超分”。

## 15. 当前实施状态

1. **已完成：**封存 v13 高清 UI 基线；标签 `backup/v13-hd-ui-20260806`，commit `e5ffa28`；
2. **已完成：**接入可回退的 FSR1 EASU，完成启动、进存档和截图验证；
3. **已完成：**EASU 后接 RCAS 第二 pass，确定 0.75 stops 和 30 Hz 稳定边界；
4. **进行中：**建立 NEAREST / SCALE2X / EASU / EASU+RCAS 的可重复 A/B 与帧时采集；
5. **下一阶段：**把全屏 FBO 裁成 1600×1200 游戏区，减少约 22% 处理像素，再测 32/35 Hz；
6. **对照阶段：**接入 NIS、Catmull-Rom 和 ARM64 xBRZ，不凭主观观感决定默认算法；
7. **研究阶段：**离线评估 Real-ESRGAN，再决定是否训练专用轻量 2× 模型并接入 ncnn/Vulkan；
8. **交付闸门：**中文、地图、动态场景、音频、鼠标、触摸和摇杆全部通过后才发布超分版本。
