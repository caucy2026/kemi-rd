# KEMI远程桌面 - 隐私政策弹窗 设计开发文档

> 本文档供 AI 编码助手直接阅读并执行，目标是在 Android App 首次启动时弹出隐私政策授权弹窗。
> **只含「隐私政策」**

---

## 一、设计参考与 UI 规范

### 1.1 参考来源

参考 **ToDesk「个人信息保护指引」弹窗** 的设计风格：

- **整体风格**：简洁、克制、专业，白底卡片 + 半透明遮罩
- **布局结构**：标题 → 正文说明 → 隐私政策链接 → 同意按钮 + 拒绝文字按钮
- **交互方式**：强制模态，点击遮罩不可关闭，用户必须二选一

### 1.2 UI 设计稿说明

```
┌─────────────────────────────────────────────┐
│                                             │
│          （半透明黑色遮罩 alpha 0.6）         │
│                                             │
│   ┌─────────────────────────────────────┐    │
│   │                                     │    │
│   │        个人信息保护指引              │    │
│   │                                     │    │
│   │  感谢您使用 KEMI远程桌面！我们非常重视  │    │
│   │  您的个人信息和隐私保护。在您使用    │    │
│   │  本应用前，请仔细阅读               │    │
│   │  《隐私政策》。                      │    │
│   │                                     │    │
│   │  我们仅收集实现文件传输功能所必需    │    │
│   │  的设备与网络信息，采用端到端加密    │    │
│   │  技术保障您的数据安全。服务器不存    │    │
│   │  储任何文件内容。                    │    │
│   │                                     │    │
│   │  如您同意，请点击"同意"按钮继续；    │    │
│   │  如您不同意，将无法使用本应用。      │    │
│   │                                     │    │
│   │  《隐私政策》（蓝色高亮可点击链接）  │    │
│   │                                     │    │
│   │  ┌───────────────────────────────┐  │    │
│   │  │        同        意             │  │    │
│   │  └───────────────────────────────┘  │    │
│   │                                     │    │
│   │        不同意（灰色文字按钮）         │    │
│   │                                     │    │
│   └─────────────────────────────────────┘    │
│                                             │
└─────────────────────────────────────────────┘
```

### 1.3 详细样式规范

#### 弹窗容器
| 属性 | 值 | 说明 |
|------|-----|------|
| 背景遮罩 | 半透明黑色，alpha = 0.6 | 覆盖全屏，禁止点击穿透 |
| 弹窗背景 | 白色 `#FFFFFF` | 圆角 16dp |
| 弹窗宽度 | 屏幕宽度 × 0.85，最大 400dp | 支持 Pad 适配 |
| 弹窗位置 | 屏幕正中央 | 垂直水平居中 |
| 内边距 | 24dp | 四周统一 |
| 阴影 | elevation 8dp | 浮起效果 |

#### 标题
| 属性 | 值 |
|------|-----|
| 文案 | `个人信息保护指引` |
| 字号 | 20sp（Pad 上 22sp） |
| 字重 | Bold |
| 颜色 | `#333333` |
| 对齐 | 左对齐 |
| 底部间距 | 16dp |

#### 正文
| 属性 | 值 |
|------|-----|
| 字号 | 14sp |
| 颜色 | `#666666` |
| 行高 | 1.6 倍 |
| 对齐 | 左对齐 |
| 底部间距 | 12dp |

#### 正文模板文案
```
感谢您使用 KEMI远程桌面！我们非常重视您的个人信息和隐私保护。
在您使用本应用前，请仔细阅读《隐私政策》。

我们仅收集实现文件传输功能所必需的设备与网络信息，采用端到端
加密技术保障您的数据安全。服务器不存储任何文件内容。

如您同意，请点击"同意"按钮继续；如您不同意，将无法使用本应用。
```

#### 隐私政策链接
| 属性 | 值 |
|------|-----|
| 文案 | `《隐私政策》` |
| 颜色 | 蓝色 `#007AFF`（与同意按钮同色系） |
| 字号 | 14sp |
| 下划线 | 无（仅靠颜色区分） |
| 点击效果 | 按下时颜色变深 `#0056B3` |
| 点击事件 | 打开浏览器/WebView 加载隐私政策 URL |

#### 同意按钮
| 属性 | 值 |
|------|-----|
| 文案 | `同意` |
| 背景色 | 蓝色 `#007AFF` |
| 文字色 | 白色 `#FFFFFF` |
| 字号 | 16sp |
| 字重 | Medium |
| 高度 | 48dp |
| 圆角 | 8dp |
| 宽度 | 弹窗内容区全宽 |
| 顶部间距 | 24dp |
| 按下效果 | 背景色变深 `#0056B3` |

#### 拒绝按钮
| 属性 | 值 |
|------|-----|
| 文案 | `不同意` |
| 背景 | 无（文字按钮） |
| 文字色 | 灰色 `#999999` |
| 字号 | 14sp |
| 高度 | 40dp |
| 顶部间距 | 12dp |
| 按下效果 | 文字颜色变深 `#666666` |

---

## 二、交互流程

### 2.1 主流程

```
App 启动（SplashActivity / MainActivity）
  │
  ▼
读取 SharedPreferences: has_agreed_privacy
  │
  ├── true  → 直接进入主界面（无弹窗）
  │
  └── false / 不存在
        │
        ▼
  ┌──────────────────────────────────────┐
  │     显示隐私政策弹窗（全屏模态）       │
  └──────────────────────────────────────┘
        │
        ├── 用户点击「同意」
        │     → 保存 has_agreed_privacy = true
        │     → 记录同意时间
        │     → 关闭弹窗，进入主界面
        │
        ├── 用户点击「不同意」
        │     → 弹出确认对话框
        │     → "确定要退出吗？退出后将无法使用本应用"
        │           ├── 确定退出 → finishAffinity() + System.exit(0)
        │           └── 继续使用 → 回到弹窗（仍可操作）
        │
        └── 用户点击「《隐私政策》」链接
              → 打开浏览器/WebView 加载隐私政策网页
              → 用户浏览后可返回弹窗
              → 弹窗状态保持不变（未同意前不会自动关闭）
```

### 2.2 关键约束

| 约束项 | 说明 |
|--------|------|
| 强制模态 | 弹窗显示时，禁止用户与底层页面交互 |
| 遮罩不可关闭 | 点击遮罩区域无任何反应 |
| 返回键拦截 | 按系统返回键不关闭弹窗（仅"不同意"的确认对话框可响应返回键） |
| 仅检查一次 | 每次冷启动只检查一次授权状态，不在应用运行中反复弹窗 |
| 同意状态持久化 | 使用 SharedPreferences 永久保存，卸载后自动清除 |

---

## 三、存储逻辑

### 3.1 存储方案

| 项目 | 值 |
|------|-----|
| 存储方式 | `SharedPreferences` |
| 文件名 | `kemi_privacy_prefs` |
| Key | `has_agreed_privacy` |
| 类型 | `Boolean` |
| 默认值 | `false` |

### 3.2 存储时机

- **写入**：用户点击「同意」按钮时，立即写入 `true`
- **读取**：每次 App 冷启动，在 `SplashActivity` 的 `onCreate()` 中读取
- **清除**：用户卸载 App 时，SharedPreferences 随应用数据一并清除 → 重装后弹窗再次出现

### 3.3 扩展字段（可选）

| Key | 类型 | 说明 |
|-----|------|------|
| `privacy_agreed_time` | `String` (ISO 8601) | 记录用户同意的时间，便于审计 |
| `privacy_policy_version` | `String` | 记录同意时的隐私政策版本号，版本更新后可重新弹窗 |

---

## 四、隐私政策链接配置

### 4.1 URL 配置

> ⚠️ **严禁硬编码**，必须在 `res/values/strings.xml` 中统一定义。

```xml
<!-- res/values/strings.xml -->
<string name="privacy_policy_url">https://www.aikemi.cn/ai-web/kemi-remote-desktop/privacy-policy</string>
```

### 4.2 打开方式

| 方式 | 适用场景 | 实现 |
|------|---------|------|
| **外部浏览器** | 推荐，简单可靠 | `Intent.ACTION_VIEW` 打开系统默认浏览器 |
| **内嵌 WebView** | 体验更好，留在应用内 | 新建一个 `PrivacyActivity`，内部用 `WebView` 加载 URL |

> 优先使用**内嵌 WebView**方案，实现简单且不占用应用内存。

### 4.3 打开后弹窗状态

- 用户点击「《隐私政策》」链接 → 跳转浏览器/WebView
- 用户浏览完毕后返回 App → **弹窗仍在，状态不变**
- 用户仍需点击「同意」或「不同意」才能完成操作

---

## 五、代码实现规范

### 5.1 目录结构建议

```
app/src/main/
├── java/com/kemi/send/
│   ├── SplashActivity.java          # 启动页，检查隐私状态
│   ├── MainActivity.java            # 主界面
│   ├── privacy/
│   │   ├── PrivacyDialog.java      # 隐私弹窗核心类
│   │   └── PrivacyExitDialog.java  # 退出确认弹窗
│   └── util/
│       └── PrivacyPrefs.java       # SharedPreferences 工具类
├── res/
│   ├── layout/
│   │   └── dialog_privacy.xml      # 弹窗布局
│   ├── values/
│   │   └── strings.xml             # 文案 + URL 配置
│   └── drawable/
│       └── bg_privacy_dialog.xml   # 弹窗背景（圆角白底）
```

### 5.2 SharedPreferences 工具类

```java
// PrivacyPrefs.java
public class PrivacyPrefs {
    private static final String PREFS_NAME = "kemi_privacy_prefs";
    private static final String KEY_AGREED = "has_agreed_privacy";
    private static final String KEY_AGREED_TIME = "privacy_agreed_time";
    private static final String KEY_POLICY_VERSION = "privacy_policy_version";

    public static boolean hasAgreed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AGREED, false);
    }

    public static void setAgreed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_AGREED, true)
            .putString(KEY_AGREED_TIME, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()))
            .putString(KEY_POLICY_VERSION, "1.0")
            .apply();
    }
}
```

### 5.3 弹窗布局文件

```xml
<!-- res/layout/dialog_privacy.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_privacy_dialog"
    android:orientation="vertical"
    android:padding="24dp">

    <!-- 标题 -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="个人信息保护指引"
        android:textSize="20sp"
        android:textStyle="bold"
        android:textColor="#333333"
        android:layout_marginBottom="16dp" />

    <!-- 正文 -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="14sp"
        android:textColor="#666666"
        android:lineSpacingMultiplier="1.6"
        android:layout_marginBottom="12dp"
        android:text="感谢您使用 KEMI远程桌面！我们非常重视您的个人信息和隐私保护。在您使用本应用前，请仔细阅读《隐私政策》。

我们仅收集实现文件传输功能所必需的设备与网络信息，采用端到端加密技术保障您的数据安全。服务器不存储任何文件内容。

如您同意，请点击&quot;同意&quot;按钮继续；如您不同意，将无法使用本应用。" />

    <!-- 隐私政策链接 -->
    <TextView
        android:id="@+id/tv_privacy_link"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="《隐私政策》"
        android:textSize="14sp"
        android:textColor="#007AFF"
        android:layout_marginBottom="24dp" />

    <!-- 同意按钮 -->
    <Button
        android:id="@+id/btn_agree"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:text="同意"
        android:textSize="16sp"
        android:textColor="#FFFFFF"
        android:background="@drawable/bg_button_agree"
        android:layout_marginBottom="12dp" />

    <!-- 不同意按钮 -->
    <TextView
        android:id="@+id/btn_disagree"
        android:layout_width="wrap_content"
        android:layout_height="40dp"
        android:text="不同意"
        android:textSize="14sp"
        android:textColor="#999999"
        android:gravity="center"
        android:layout_gravity="center" />

</LinearLayout>
```

### 5.4 弹窗背景 Drawable

```xml
<!-- res/drawable/bg_privacy_dialog.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#FFFFFF" />
    <corners android:radius="16dp" />
</shape>
```

### 5.5 同意按钮背景 Drawable

```xml
<!-- res/drawable/bg_button_agree.xml -->
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 按下状态 -->
    <item android:state_pressed="true">
        <shape>
            <solid android:color="#0056B3" />
            <corners android:radius="8dp" />
        </shape>
    </item>
    <!-- 默认状态 -->
    <item>
        <shape>
            <solid android:color="#007AFF" />
            <corners android:radius="8dp" />
        </shape>
    </item>
</selector>
```

### 5.6 弹窗核心逻辑

```java
// PrivacyDialog.java
public class PrivacyDialog {

    public static void show(Context context, OnResultCallback callback) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_privacy);

        // 强制模态：禁止点击遮罩关闭、禁止返回键关闭
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        // 设置弹窗宽度（屏幕 85%，最大 400dp）
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = (int) (400 * context.getResources().getDisplayMetrics().density);
            int targetWidth = (int) (screenWidth * 0.85);
            params.width = Math.min(targetWidth, maxWidth);
            window.setAttributes(params);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // 隐私政策链接
        TextView tvLink = dialog.findViewById(R.id.tv_privacy_link);
        tvLink.setOnClickListener(v -> {
            String url = context.getString(R.string.privacy_policy_url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        });

        // 同意按钮
        Button btnAgree = dialog.findViewById(R.id.btn_agree);
        btnAgree.setOnClickListener(v -> {
            // 保存同意状态
            PrivacyPrefs.setAgreed(context);
            dialog.dismiss();
            callback.onAgreed();
        });

        // 不同意按钮
        TextView btnDisagree = dialog.findViewById(R.id.btn_disagree);
        btnDisagree.setOnClickListener(v -> {
            showExitConfirm(context, dialog, callback);
        });

        dialog.show();
    }

    private static void showExitConfirm(Context context, Dialog parentDialog, OnResultCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle("提示")
            .setMessage("确定要退出吗？退出后将无法使用本应用")
            .setPositiveButton("确定退出", (d, which) -> {
                parentDialog.dismiss();
                callback.onDisagreed();
            })
            .setNegativeButton("继续使用", null) // 回到弹窗
            .setCancelable(false)
            .show();
    }

    public interface OnResultCallback {
        void onAgreed();
        void onDisagreed();
    }
}
```

### 5.7 启动页集成

```java
// SplashActivity.java
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 延迟 500ms 显示，避免闪屏
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (PrivacyPrefs.hasAgreed(this)) {
                // 已同意，直接进入主界面
                navigateToMain();
            } else {
                // 未同意，显示隐私弹窗
                PrivacyDialog.show(this, new PrivacyDialog.OnResultCallback() {
                    @Override
                    public void onAgreed() {
                        navigateToMain();
                    }

                    @Override
                    public void onDisagreed() {
                        // 完全退出 App
                        finishAffinity();
                        System.exit(0);
                    }
                });
            }
        }, 500);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
```

---

## 六、Pad 大屏适配

### 6.1 适配要点

| 项目 | 手机 | Pad（≥600dp 宽度） |
|------|------|-------------------|
| 弹窗宽度 | 屏幕 × 0.85 | 固定 400dp |
| 标题字号 | 20sp | 22sp |
| 正文字号 | 14sp | 15sp |
| 按钮高度 | 48dp | 52dp |

### 6.2 实现方式

在 `res/values-sw600dp/strings.xml` 中可覆盖字号等配置（如需），弹窗宽度的自适应逻辑已在 5.6 节代码中通过 `Math.min()` 实现，无需额外适配。

---

## 七、验收标准

| 编号 | 验收项 | 操作步骤 | 预期结果 |
|------|--------|---------|---------|
| 1 | 首次启动弹窗 | 安装后首次打开 App | 弹窗显示，背景变暗，无法点击弹窗以外区域 |
| 2 | 遮罩不可关闭 | 点击弹窗外的遮罩区域 | 无任何反应，弹窗不关闭 |
| 3 | 返回键不关闭 | 按系统返回键 | 弹窗不关闭（仅退出确认框可响应） |
| 4 | 隐私政策链接 | 点击「《隐私政策》」 | 打开浏览器/WebView，加载 `https://www.aikemi.cn/ai-web/kemi-remote-desktop/privacy-policy` |
| 5 | 浏览后返回 | 打开链接后按返回键回到 App | 弹窗仍在，状态不变 |
| 6 | 点击同意 | 点击「同意」按钮 | 弹窗关闭，进入主界面 |
| 7 | 重启已同意 | 同意后再启动 App | 不再弹出弹窗，直接进入主界面 |
| 8 | 点击不同意 | 点击「不同意」 | 弹出确认对话框"确定要退出吗？" |
| 9 | 确认退出 | 确认对话框点"确定退出" | App 完全退出，进程结束 |
| 10 | 取消退出 | 确认对话框点"继续使用" | 回到隐私弹窗 |
| 11 | 卸载重装 | 卸载 App 后重新安装 | 弹窗再次出现（SharedPreferences 已清除） |
| 12 | Pad 适配 | 在 Pad 上启动 App | 弹窗宽度不超过 400dp，居中显示，不撑满屏幕 |

---

## 八、注意事项与踩坑点

### ❌ 绝对不能做

1. **不能默认勾选或自动同意**
   - 华为审核会检测，发现默认同意直接驳回
   - 用户必须手动点击「同意」按钮

2. **不能在用户不同意时仍然进入主界面**
   - 必须完全阻止使用，或退出 App
   - 不能"先进入再弹窗"

3. **不能在弹窗显示前就申请敏感权限**
   - 必须在用户同意隐私政策**之后**，才能申请存储、麦克风等权限
   - 弹窗阶段只做"同意/不同意"二选一

4. **不能把 URL 硬编码在代码中**
   - 必须使用 `strings.xml` 管理
   - 后续更换域名只需改配置文件

5. **不能让弹窗可被遮罩点击或返回键关闭**
   - 这是强制授权弹窗，不是普通通知弹窗
   - 用户必须明确选择「同意」或「不同意」

### ✅ 推荐做法

1. **延迟 300-500ms 再显示弹窗**
   - 让启动页/闪屏先展示，避免弹窗"闪现"感
2. **隐私政策网页需支持移动端浏览**
   - 确认 `https://www.aikemi.cn/ai-web/kemi-remote-desktop/privacy-policy` 在手机浏览器中排版正常
3. **同意状态写入时机要早**
   - 在 `dialog.dismiss()` **之前**就写入 SharedPreferences
   - 防止用户在写入前杀掉进程导致下次还要再弹

---

## 九、给 AI 编码助手的执行指令

如果你是 AI 编码助手，请按以下顺序执行：

### Step 1：读取本文件
- 理解全部 UI 规范和交互逻辑
- 确认只含「隐私政策」，不含「用户服务协议」

### Step 2：检查现有代码
- 查看 `AndroidManifest.xml`，确认 `SplashActivity` 是否存在
- 检查是否已有隐私弹窗相关代码
- 确认 `strings.xml` 中是否已有 `privacy_policy_url`

### Step 3：创建工具类
- 创建 `PrivacyPrefs.java`（SharedPreferences 读写工具）
- 确保 Key 命名统一为 `has_agreed_privacy`

### Step 4：创建弹窗
- 创建布局文件 `dialog_privacy.xml`（参考第五节 5.3）
- 创建 `PrivacyDialog.java`（参考第五节 5.6）
- 创建 Drawable 资源 `bg_privacy_dialog.xml` 和 `bg_button_agree.xml`

### Step 5：修改启动流程
- 在 `SplashActivity` 中插入隐私状态检查逻辑
- 已同意 → 直接进入主界面
- 未同意 → 显示弹窗 → 同意进主界面 / 不同意退 App

### Step 6：配置 URL
- 在 `res/values/strings.xml` 中添加：
  ```xml
  <string name="privacy_policy_url">https://www.aikemi.cn/ai-web/kemi-remote-desktop/privacy-policy</string>
  ```
- 全局搜索是否有硬编码的 URL，统一替换

### Step 7：测试验证
- 逐项核对第七节「验收标准」（共 12 项）
- 重点测试：首次启动、同意后再启动、不同意退出、卸载重装
- 测试 Pad 横屏/竖屏适配

### Step 8：输出修改清单
- 列出所有新增文件和修改文件
- 标注每个文件的作用

---

**文档版本**：v1.0
**最后更新**：2026-08-11
**维护者**：caucy2026
**适用范围**：KEMI远程桌面 Android 端（支持 Pad 适配）
**核心原则**：只含隐私政策、不含用户服务协议、强制模态、最小必要
