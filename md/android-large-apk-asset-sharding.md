# Android 超大 APK Asset 分片兼容指南

## 目的

当 Android 应用需要把数百 MB 至数 GB 的离线数据放进单一 APK 时，不要把一个超大文件直接
放入 `assets/`。部分厂商系统能够安装 APK，却无法通过 `AssetManager.open()` 稳定读取其中的
超大单个条目。本指南记录暗黑2 PAD 项目的真实故障、通用分片方案和验收方法，供其他项目复用。

本文所说的“分片”是 APK 内资源的字节分片，不是 Android App Bundle 的 Split APK，也不是
按地图或业务内容拆包。用户最终仍然只安装一个 APK。

## 已复现故障

- 设备：华为 `BKY-W00`，Android 12，ARM64。
- APK 可以安装，应用也能启动。
- 首次运行准备本地数据时失败，界面显示“运行环境安装失败”。
- 失败位置为 `game/game-data.zip.part.aa`。
- 该资源确实存在于 APK 中，旧分片大小恰好为 512 MiB。
- 同一个 APK 的 512 MiB asset 在原 KEMI PAD 上可读取，因此该问题具有厂商实现差异，不能只在
  单一设备上验证。

重要判断：APK 安装成功不等于 APK 内所有超大 asset 都能被应用读取。遇到上述错误时，应同时
检查 ZIP 目录和设备端 `AssetManager` 读取能力，不应直接判定资源漏打包或反复清数据重试。

## 推荐结构

```text
原始离线目录
    ↓ 生成一个确定性的 ZIP 数据流
game-data.zip
    ↓ 按 128 MiB 机械切分
game-data.zip.part.aa
game-data.zip.part.ab
game-data.zip.part.ac
...
    ↓ 连同清单放入 APK assets/game/
单一 APK
    ↓ 首次运行按清单顺序串联输入流
SHA-256/长度校验
    ↓ 直接流式解压到 staging 目录
原子切换为正式数据目录
```

建议默认使用 128 MiB 分片。它不是 Android 官方规定的硬上限，而是本项目对厂商兼容性、分片
数量和打包效率的保守折中。不要再次使用已经在华为 Android 12 上失败的 512 MiB 分片。

## 构建端实现

先把允许发布的离线文件打成一个 ZIP。已经压缩的媒体和 MPQ 等格式建议使用 `zip -0`，避免
重复压缩浪费构建时间和首次安装 CPU：

```bash
zip -0 -q -r game-data.zip .
split -b 128m -a 2 game-data.zip game-data.zip.part.
```

切分只是按字节截断，不会修改原始数据。所有分片严格按文件名顺序连接后，与原始 ZIP
逐字节一致。

同时生成清单：

```properties
schema=3
content=offline-core
bytes=1513648987
sha256=<完整 game-data.zip 的 SHA-256>
files=348
parts=game-data.zip.part.aa,game-data.zip.part.ab,...
```

清单必须记录完整数据流的长度、SHA-256、解压文件数量以及明确的分片顺序。不要只校验单个
分片，也不要依赖文件系统的未排序遍历结果。

## Android 运行时拼接

运行时不需要先额外生成一个同样大小的临时 ZIP。依次打开各分片，并通过
`SequenceInputStream` 暴露为一条连续输入流：

```java
List<InputStream> streams = new ArrayList<>();
for (String part : orderedParts) {
    streams.add(context.getAssets().open("game/" + part));
}
InputStream archive = new SequenceInputStream(
        Collections.enumeration(streams));
```

上层 `ZipInputStream` 会把这条输入流当作原始 ZIP。ZIP 头或单个文件内容跨越分片边界也没有
问题，因为 `SequenceInputStream` 会在当前分片 EOF 后继续读取下一分片。

## 安全安装流程

1. 在后台线程打开分片，禁止在 Activity 主线程逐个探测大资源。
2. 校验合并数据流的总字节数和完整 ZIP 的 SHA-256。
3. 解压到应用私有目录中的临时目录，例如 `game.installing`。
4. 对 ZIP 条目做 canonical path 检查，阻止 `../` 路径穿越。
5. 校验解压文件数量，并至少确认关键可执行文件和核心数据存在。
6. 全部成功后，将临时目录原子改名为正式目录。
7. 失败时保留明确错误阶段，但清理不完整 staging；不要覆盖已有可用数据。

当前项目为了给用户更快的错误反馈，先完整读取一次做 SHA-256，再第二遍解压。这会把 1.5 GB
数据读取两遍。后续可以用 `DigestInputStream + ZipInputStream` 在一次解压过程中同时计算摘要，
但只有在验证失败时能可靠回滚 staging 的前提下才建议合并为一遍。

## 常见误区

- 误区：APK 已安装，所以 asset 一定可读。
  - 实际：PackageManager 能解析/安装，并不代表厂商 `AssetManager` 能流式读取超大条目。
- 误区：分片是多个 APK。
  - 实际：这些分片只是同一个 APK `assets/` 下的多个文件。
- 误区：首次运行要先在磁盘拼成一个完整 ZIP。
  - 实际：顺序输入流可以直接校验和解压，避免额外占用 1.5 GB 临时空间。
- 误区：分片越大越快。
  - 实际：超大条目会损失跨厂商兼容性；十几个 128 MiB 分片的额外切换成本很小。
- 误区：只显示“安装失败”即可。
  - 实际：界面必须显示当前分片或阶段，日志应保留异常类型和堆栈，否则容易误判为卡死。

## 发布前验收

至少在原目标 PAD 和一个不同厂商的 Android 12+ ARM64 设备上执行：

1. 卸载旧版，确认应用私有数据已清空。
2. 安装完整单 APK，不能依赖设备遗留的游戏文件。
3. 点击首次本地数据安装，确认进度持续更新。
4. 校验每个清单分片都可以打开。
5. 校验合并字节数、SHA-256 和解压文件数量。
6. 安装中途强制结束一次，再启动确认能够安全重试。
7. 完成后启动游戏、进入实际地图，并重启应用再次读档。
8. 检查设备剩余空间；首次安装需同时容纳 APK、系统安装暂存和解压后的数据。

通过 USB 安装华为设备时，还可能停在系统 `InstallStaging` 页面等待本机确认。ADB 没有返回
`Success` 且焦点位于 PackageInstaller 时，应让用户在已解锁设备上确认“允许 USB 安装”；
不得把等待系统确认误判为 APK 传输失败。

## 本项目对应实现

- 构建脚本：`tools/package_streaming_game_data.sh`
- 通用打包脚本：`tools/package_game_data.sh`
- Android 安装器：`app/src/main/java/cn/newlink/kemi/diablo2/runtime/GameDataInstaller.java`
- 分片清单：`app/src/full/assets/game/game-data.properties`

本次修复将分片从 512 MiB 调整为 128 MiB。华为兼容版完整 APK 已成功构建，后续必须在
`BKY-W00` 上完成系统安装确认和首次解压闭环，才能把该设备验收标记为通过。
