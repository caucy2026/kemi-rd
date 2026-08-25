# 双屏壁纸

面向 KEMI S1 双屏设备的静态多显示壁纸服务。系统会为 D2、D0 分别创建壁纸引擎，两块 1920×1280 屏幕显示同一主题的不同画面。

## 功能

- 内置三套双屏主题，默认使用第 3 套“两个智慧”。
- D2 使用 `setN_d2.png`，D0 使用 `setN_d0.png`。
- 每张素材均为 1920×1280，点对点居中绘制。
- 静态渲染，无动画、定时器、网络权限和后台轮询。
- 壁纸设置页可在三套主题之间切换。
- 安装后会在应用列表显示“**双屏壁纸**”，无需从系统隐藏的动态壁纸入口寻找。
- `android:supportsMultipleDisplays="true"`，让 Android 为两块物理屏幕分别创建 Engine。

## 目录

```text
AndroidManifest.xml
assets/                         # 三套 D2/D0 壁纸素材
res/xml/wallpaper.xml           # 多显示与设置页声明
src/com/kemi/dualwallpaper/
  DualWallpaperService.java     # 按 Display ID 加载不同素材
  SettingsActivity.java         # 三套主题选择页
build-release.sh                # 无 Gradle 的可复现构建脚本
```

## 构建

需要 Android SDK 31+、JDK、`zip`，并通过环境变量提供正式签名信息：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export KEMI_WALLPAPER_KEYSTORE=/path/to/release.keystore
export KEMI_WALLPAPER_STORE_PASSWORD='***'
export KEMI_WALLPAPER_KEY_ALIAS=androiddebugkey
export KEMI_WALLPAPER_KEY_PASSWORD='***'
./build-release.sh
```

产物位于 `release/双屏壁纸-v1.0.1-release.apk`。

## 安装与打开

```bash
adb install -r release/双屏壁纸-v1.0.1-release.apk
```

安装完成后，在系统应用列表中找到并打开“**双屏壁纸**”：

1. 选择一套主题（默认第 3 套“两种智慧”）。
2. 点击“应用到双屏”。
3. 在 Android 系统壁纸确认页完成设置。

也可以从命令行直接打开选择页：

```bash
adb shell am start -n com.kemi.dualwallpaper/.SettingsActivity
```

本应用只注册独立的壁纸服务与设置入口，不替换 Launcher，也不修改 Office、浏览器等业务应用。

## 设备映射

当前 KEMI S1 真机映射：

- Display 2：上屏
- Display 0：下屏

如果后续硬件 Display ID 改变，需要同步调整 `DualWallpaperService.loadForDisplay()`。
