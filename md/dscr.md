# 双屏录制核心实现 — SurfaceControl.Transaction

> 平台：HiSilicon V900, Android 12 | 权限：`sharedUserId="android.uid.system"` + 平台签名
> 无 MediaProjection、无 screenrecord、无用户授权弹窗

---

## 原理

利用 Android SurfaceFlinger 的 LayerStack 机制，通过隐藏 API `SurfaceControl.Transaction` 将物理屏的图层栈直接路由到 `MediaCodec` 的 input Surface，系统自动持续灌帧。

```
物理屏 → LayerStack → VirtualDisplay → MediaCodec Surface → H.264 → MP4
```

## 核心三步

### 1. 获取物理 Display Token

```kotlin
val sc = Class.forName("android.view.SurfaceControl")
val physIds = sc.getMethod("getPhysicalDisplayIds").invoke(null) as LongArray
// V900: [0, 1] — 主屏物理ID=0, 副屏物理ID=1

val physToken = sc.getMethod("getPhysicalDisplayToken", Long::class)
    .invoke(null, physId) as IBinder
```

### 2. 创建编码器 → 绑定 LayerStack

```kotlin
// MediaCodec H.264 编码器
val codec = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC)
codec.configure(format, null, null, CONFIGURE_FLAG_ENCODE)
val surface = codec.createInputSurface()
codec.start()

// 创建虚拟 Display
val virtualToken = sc.getMethod("createDisplay", String::class, Boolean::class)
    .invoke(null, "Recorder", false) as IBinder

// Transaction 绑定
val tx = Class.forName("android.view.SurfaceControl\$Transaction")
    .getConstructor().newInstance()

tx.getMethod("setDisplayLayerStack", IBinder::class, Int::class)
    .invoke(tx, virtualToken, layerStack)  // D0=0, D2=2

tx.getMethod("setDisplaySurface", IBinder::class, Surface::class)
    .invoke(tx, virtualToken, surface)

tx.getMethod("setDisplayProjection", IBinder::class, Int::class, Rect::class, Rect::class)
    .invoke(tx, virtualToken, 0, Rect(0,0,w,h), Rect(0,0,w,h))

tx.getMethod("apply").invoke(tx)
// ← 此时 SurfaceFlinger 开始自动灌帧
```

### 3. Drain → Muxer → MP4

```kotlin
val muxer = MediaMuxer(file, MUXER_OUTPUT_MPEG_4)
val drain = Thread {
    while (running) {
        val idx = codec.dequeueOutputBuffer(info, 100_000)
        when {
            infoChanged -> { track = muxer.addTrack(format); muxer.start() }
            idx >= 0 -> { muxer.writeSampleData(track, buf, info); codec.releaseOutputBuffer(idx, false) }
        }
    }
}
drain.start()
```

### 停止

```kotlin
running = false
drain.join(3000)
codec.signalEndOfInputStream()
codec.stop(); codec.release()
muxer.stop(); muxer.release()
sc.getMethod("destroyDisplay", IBinder::class).invoke(null, virtualToken)
```

## LayerStack 映射

| 屏 | 物理 ID | 逻辑 ID | LayerStack |
|----|:------:|:------:|:----------:|
| 主屏 | 0 | 0 | 0 |
| 副屏 | 1 | 2 | 2 |

## 完整 API 链

```
SurfaceControl.getPhysicalDisplayIds()                    → LongArray
SurfaceControl.getPhysicalDisplayToken(Long)              → IBinder
SurfaceControl.createDisplay(String, Boolean)             → IBinder
SurfaceControl.destroyDisplay(IBinder)

Transaction()                                             → Transaction
Transaction.setDisplayLayerStack(IBinder, Int)
Transaction.setDisplaySurface(IBinder, Surface)
Transaction.setDisplayProjection(IBinder, Int, Rect, Rect)
Transaction.apply()
```

## 对比

| | 本方案 | MediaProjection | screenrecord |
|--|:--:|:--:|:--:|
| 用户授权 | ❌ 不需要 | ✅ 需要弹窗 | ❌ 不需要 |
| 前台 Service | ❌ | ✅ | ❌ |
| 双屏并发 | ✅ 原生支持 | ❌ 不支持 | ✅ 多进程 |
| 平台签名 | ✅ 需要 | ❌ | ❌ |
| API 稳定性 | ⚠️ 隐藏 API | ✅ 公开 | ✅ 公开 |
| 子进程管理 | ❌ | ❌ | ✅ 需管理 |
