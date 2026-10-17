# MBGA · libxposed API 102 打包说明

把上游 [cledwynl/mbga](https://github.com/cledwynl/mbga)（YukiHookAPI + 传统 Xposed API 模块）改造为
**libxposed API 102（Modern Xposed API）标准模块** 后的源码与构建产物。

## 1. 目录结构

```
src/mbga-master/                      改造后的完整工程（Gradle 工程根目录）
  app/src/main/resources/META-INF/xposed/
    module.prop                       libxposed 模块元数据（minApiVersion/targetApiVersion = 102）
    java_init.list                    模块入口类清单：top.trangle.mbga.hook.HookEntry
    scope.list                        注入范围：com.bilibili.app.in / tv.danmaku.bili
  app/src/main/java/top/trangle/mbga/xposed/
    HookEnv.kt                        运行环境（模块实例、classloader、RemotePreferences）
    ModulePrefs.kt                    带默认值的配置读取封装
    Finders.kt                        类/方法/字段/构造器查找 DSL（替代 YukiHookAPI member finder）
    Hooks.kt                          before/after/replaceAny/replaceUnit → libxposed intercept 链
    YLog.kt                           日志（写入 Xposed 日志）
  app/src/main/java/top/trangle/mbga/hook/
    HookEntry.kt                      入口，继承 io.github.libxposed.api.XposedModule
dist/
  MBGA-v1.5.0-libxposed102.apk        构建产物（已签名）
  VERIFY.md                           产物校验报告
```

## 2. 规范符合性（libxposed API 102）

| 规范要求 | 本工程实现 |
| --- | --- |
| 入口继承 `io.github.libxposed.api.XposedModule` | `top.trangle.mbga.hook.HookEntry`（`HookEntry` 的父类已由 dexdump 确认） |
| 入口清单位于 `META-INF/xposed/java_init.list` | 1 行 `top.trangle.mbga.hook.HookEntry`，LF 结尾、无 BOM |
| 元数据 `META-INF/xposed/module.prop` | `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`、`exceptionMode=protective` |
| 作用域 `META-INF/xposed/scope.list` | `com.bilibili.app.in`（国内版）、`tv.danmaku.bili` |
| 名称/描述来自 Android 资源 | `android:label="@string/app_name"`、`android:description="@string/xposed_desc"` |
| 编译期依赖 `io.github.libxposed:api:102.0.0` | `compileOnly`，不打进 APK |
| 不得调用 `de.robv.android.xposed` 传统 API | 已彻底移除（dex 中 0 处引用） |
| 生命周期回调 | `onModuleLoaded`；`onPackageReady` 里安装 `Instrumentation.callApplicationOnCreate` / `Application.onCreate` 钩子，在宿主 Application 创建前装载全部 hooker（此时才能拿到宿主 Context 判断 B 站版本） |
| 配置读取 | `XposedModule.getRemotePreferences("top.trangle.mbga_preferences")` |

`minApiVersion=102` 意味着：需要支持 libxposed API 102 的框架（LSPosed 的 Modern API 实现等）才能加载，
这是“按 API 102 标准打包”的必然结果。

## 3. 与上游的改动

### 3.1 依赖与插件
* 移除 `de.robv.android.xposed:api:82`、`com.highcapable.yukihookapi:api:1.2.1`、
  `ksp-xposed`（KSP 插件）、`kotlinter` 插件。
* 新增 `compileOnly("io.github.libxposed:api:102.0.0")`。
* AGP 8.6.1 / Kotlin 2.0.0 / JDK 17 / compileSdk 34 / minSdk 27 保持不变。

### 3.2 Hook 层
上游代码全部基于 YukiHookAPI 的 DSL（`"类名".toClass()`、`.method {}.hook { before/after }`、
`replaceUnit { callOriginal() }`、`field.get(obj).string()` 等）。本次在
`top.trangle.mbga.xposed` 中实现了一套等价的轻量 DSL，直接落在 libxposed 的拦截器链上：

* `before` / `after` → 同一个 `intercept(chain)` 中 `chain.proceed()` 的前后处理；
  参数可写（`args[i] = ...` 通过 `chain.proceed(args)` 生效），`result` 可改写（`resultFalse()` 等）。
* `replaceAny` / `replaceUnit` + `callOriginal()` → `chain.proceed()`，未调用即“吞掉”原方法。
* 成员查找：按 名称 / 参数类型 / 参数个数 / 参数谓词 / 返回类型 / 修饰符（`modifiers { isStatic }`）
  在类及其父类中匹配；找不到时只记录日志，不影响其他 hook。
* 8 个 hooker 的业务逻辑（B 站各版本适配分支）逐行保留，仅替换 API 调用方式。

### 3.3 功能取舍（无法在 libxposed API 下等价实现的部分）
1. **YukiHookAPI `dataChannel`（宿主 → 模块 App 的 IPC）**：上游用它把首页底部 Tab 列表传给设置界面，
   动态生成“隐藏某个 Tab”的开关。libxposed 只提供宿主读模块文件的单向通道（`openRemoteFile`），
   没有反方向通道，故移除 `addBottomTabPrefs()`、`setupTabProvider()`、`sendTabToModule()` 与
   `BottomTab` 数据结构。`HomeViewHooker` 中 `tabs_disable#<scheme>` 的过滤逻辑保留（如通过其他方式写入该键仍然生效）。
2. **`injectModuleAppResources()`（把模块资源注入宿主 Activity）**：设置入口本来就是跳转模块自己的
   `SettingsActivity`，宿主内并不渲染模块布局，故移除资源注入钩子。
3. **`ModulePreferenceFragment` → `PreferenceFragmentCompat`**：设置界面改为标准 AndroidX 实现，
   直接读写模块自己的 `top.trangle.mbga_preferences`。
4. **`ModuleApplication` → `Application`**：不再依赖 YukiHookAPI 的模块 Application。

### 3.4 签名
工程内附带自签名测试证书 `keystore/mbga-release.jks`（`mbga` / `mbga123456`，RSA 2048，10000 天）。
`app/build.gradle.kts` 在证书存在时自动用于 release 构建；可用环境变量 `STORE_PASSWORD`、`KEY_ALIAS`、
`KEY_PASSWORD` 覆盖。**正式分发请替换为你自己的证书。**

## 4. 构建

环境（本机已就绪，均在 `tools/` 下）：OpenJDK 17.0.2、Android SDK（platform 34 / build-tools 34.0.0）、Gradle 8.7。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

或手动：

```powershell
$env:JAVA_HOME       = "$PWD\tools\jdk\jdk-17.0.2"
$env:ANDROID_HOME    = "$PWD\tools\android-sdk"
$env:GRADLE_USER_HOME= "$PWD\tools\gradle-home"
.\tools\gradle-8.7\bin\gradle.bat -p src\mbga-master assembleRelease
```

产物：`src/mbga-master/app/build/outputs/apk/release/app-release.apk`

`build.ps1` 会把签名后的 APK 复制到 `dist/`。

## 4.1 校验产物

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\verify.ps1
```

生成的 `dist/VERIFY.md` 会逐项核对：`META-INF/xposed/*` 三个文件的字节形态、Manifest 的 label/description、
入口类的父类（dexdump）、dex 中对 `io.github.libxposed.api` 与传统 `de.robv.android.xposed` 的引用计数、
以及 APK 签名方案。

## 5. 安装与使用

1. 设备安装支持 libxposed API 102 的框架（LSPosed 等）。
2. 安装 `dist/MBGA-v1.5.0-libxposed102.apk`，在框架管理器中启用模块（作用域已由 `scope.list` 固定为 B 站）。
3. 打开模块 App 勾选需要的功能；首次注入到 B 站时会自动弹出设置界面提示。

上游 README 中列出的功能开关、所需 B 站版本分支（3.18.2 ~ 3.20.1）均未改动。

## 6. 已知限制

* 未做签名校验 / 反调试对抗（上游也没有）。
* `isMinifyEnabled = false`：R8 会重命名类，而本模块大量依赖字符串类名反射，故关闭混淆。
* 底部 Tab 动态开关见 3.3。

## 7. 闪退修复记录（build tag `MBGA libxposed102-r3`）

用户提供的 LSPosed 日志包（`LSPosed_20261017_131158.zip`）已确认崩溃点，`dropbox/data_app_crash@*` 共 4 份，
内容完全一致：

```
Process: top.trangle.mbga (v1.5.0)
java.lang.SecurityException: MODE_WORLD_READABLE no longer supported
    at android.app.ContextImpl.checkMode(ContextImpl.java:4034)
    at android.app.ContextImpl.getSharedPreferences(ContextImpl.java:669)
    at top.trangle.mbga.views.SettingsActivity$MySettingsFragment.onCreatePreferences(SettingsActivity.kt:68)
```

同批日志同时证明模块本体在 B 站进程里工作正常（没有任何 E/W 级模块日志）：

```
(com.bilibili.app.in)[top.trangle.mbga,MBGA,...] hooking com.bilibili.app.in
(com.bilibili.app.in)[top.trangle.mbga,MBGA,...] all hookers loaded for com.bilibili.app.in
(com.bilibili.app.in:ijkservice / :pushservice / :download) ... 同样加载成功
```

也就是说：**B 站没有崩，崩的是被自动拉起的模块设置页**，随后每次重试都会再崩一次，看起来就是"打开软件闪退"。

修复内容：

1. **设置界面必崩（主因）**
   `SettingsActivity.MySettingsFragment.onCreatePreferences()` 里照抄了上游的
   `getSharedPreferences("${packageName}_preferences", MODE_WORLD_READABLE)`。
   从 Android 7.0（API 24）开始，只要 `targetSdkVersion >= 24`，该调用会直接抛
   `SecurityException: MODE_WORLD_READABLE no longer supported`，设置页一起就崩；
   而 `MainActivityHooker` 会在首次进入 B 站时自动拉起设置页，所以表现成“激活后打开 App 闪退”。
   修复：删除该调用，改为在 `onPause` 里对 prefs 文件做一次 `setReadable(true, false)`
   的尽力而为操作（读配置已改走 `getRemotePreferences`，不再需要 world-readable）。

2. **宿主侧兜底**
   * `HookEntry.onModuleLoaded / onPackageReady` 整体 try/catch，任何异常只记日志，不抛回框架；
   * `HookSpec.dispatch()` 对 `before` / `after` / `replaceAny` / `replaceUnit` 全部 try/catch，
     `replace` 失败时回退执行原方法（未执行过原方法的前提下），杜绝“模块代码把宿主带崩”；
   * 自动拉起设置页加了 `AtomicBoolean` 只做一次 + `runCatching`，即使模块 App 不可用也不会影响 B 站。

若仍有闪退，请抓这两份日志定位（模块自身日志 tag 为 `MBGA`，框架日志在 LSPosed 管理器的“日志”页）：

```bash
adb logcat -b crash -d                      # 崩溃堆栈
adb logcat -d | findstr /i "MBGA AndroidRuntime"
adb shell su -c "logcat -d -b all | grep -iE 'MBGA|libxposed|Xposed'"
```

把 `FATAL EXCEPTION` 里第一段 `Caused by:` 与进程名（`com.bilibili.app.in` 还是 `top.trangle.mbga`）发我，
可以进一步定位。

## 8. 配置不生效 / 首启弹框反复（build tag `MBGA libxposed102-r4`）

日志里 `module prefs: ok(0 keys)` 说明框架侧读到的配置组是**空的**，于是：

* 每个开关都读到 `false` → 功能全部失效；
* `prefs.all.isEmpty()` 为真 → 每次冷启动都认为"没配置过"，反复弹首启对话框。

原因是配置通道用错了：

* 之前宿主用 `getRemotePreferences("top.trangle.mbga_preferences")`，指望框架去同步模块 App 的 prefs 文件；
* 实际上 libxposed 的 `getRemotePreferences(group)` 读的是**框架数据库里的组**，这个组只有在模块 App 通过
  `XposedService.getRemotePreferences(group)` 写入时才会被创建（对应 LSPosed 的
  `LSPModuleService.updateRemotePreferences`）。

修复：

1. 模块 App 引入 `implementation("io.github.libxposed:service:102.0.0")`。
   该 AAR 自带 `XposedProvider`，合并进清单后为：`io.github.libxposed.service.XposedProvider`，
   `authorities="top.trangle.mbga.XposedService"`，`exported=true`。
2. 新增 `top.trangle.mbga.prefs.RemotePrefsBridge`：在 `DefaultApplication.onCreate` 里
   `XposedServiceHelper.registerListener`，绑定成功后取 `service.getRemotePreferences("config")`，
   把本地 `SharedPreferences` 的**全部键值**推送进去；之后每次本地配置变化都重新推送
   （这样被删除的键也会同步过去）。
3. 宿主侧 `HookEnv` 改为优先读组 `config`（保留旧的文件名组做兜底），并打印
   `module prefs: ok(N keys)` 便于排查。

安装后日志里应分别出现（模块 App 进程）`framework service bound: ...` 和（宿主进程）`module prefs: ok(N keys)`。

### 8.1 与 B 站 3.20.4 的兼容性核对

用提供的 `哔哩哔哩_3.20.4.apks`（base.apk 内的 29 个 dex）逐个核对了 hook 目标，结论是**基本都还在**：

| hook 目标 | 3.20.4 |
| --- | --- |
| `tv.danmaku.bili.MainActivityV2#onCreate` | 在 |
| `com.bilibili.app.preferences.BiliPreferencesActivity#onCreate` | 在 |
| `com.bilibili.pegasus.api.model.BasicIndexItem#getUri` | 在 |
| `com.bilibili.pegasus.api.modelv2.Config#getAutoRefreshTimeBy*` | 在（3 个都在） |
| `com.bilibili.pegasus.api.BaseTMApiParser` | 在 |
| `tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment#onResume` | 在 |
| `com.bilibili.search2.api.SearchSquareType#getType` | 在 |
| `com.bilibili.app.comment3.viewmodel.CommentViewModel#k3` | 在（参数类型为 `comment3.action.c`） |
| `com.bilibili.app.comm.supermenu.share.v2.ShareTargetTask$f#l` | 在 |
| `...chronosrpc.methods.send.GestureEventReceived$Request#setLocation` | 在 |
| `com.bapis.bilibili.*`（DmViewReply / MainListReply / Label / Relates / SearchMoss / StoryEntrance / SpecificPlayConfig 等） | 在 |
| `tv.danmaku.bili.ui.main2.MainFragment$a#a` | 在 |
| `com.bilibili.ship.theseus.united.page.tab.TheseusTabPagerService` | 在 |

版本门限也匹配（3.20.4 的 versionCode 大于 3.20.1 的 8230500），因此 3.20.4 上"功能全失效"
不是版本适配问题，而是配置没有传过来。

核对工具：`tools/py/dexindex.py`（自带的最小 DEX 解析器）+ `bili3204/targets.json`。


### 8.2 r4 实机日志验证（`LSPosed_20261017_142303.zip`）

```
14:22:39.237 (com.bilibili.app.in)            module prefs: ok(0 keys)     <- 模块 App 还没运行过
14:22:40.463 wm_create_activity: top.trangle.mbga/.views.SettingsActivity  <- 模块 App 启动，bridge 绑定并推送
14:22:41.110 (com.bilibili.app.in:download)    module prefs: ok(38 keys)
14:22:45.465 (com.bilibili.app.in)            module prefs: ok(38 keys)
14:22:45.938 (com.bilibili.app.in:ijkservice) module prefs: ok(38 keys)
14:22:45.943 (com.bilibili.app.in:pushservice)module prefs: ok(38 keys)
```

结论：配置通道已经打通（38 个键），模块日志里没有 W/E 级记录，dropbox 里也没有新增崩溃。
首个进程读到的 0 键是时序问题（模块 App 尚未 push），之后所有进程都是 38 键。


### 8.3 versionCode 170（build tag `MBGA libxposed102-r5`）

`versionCode` 由 1 提升为 170（`versionName` 仍为 1.5.0），用于覆盖安装 / 框架识别新版本：

* `app/build.gradle.kts`：`val appVersionCode = (System.getenv("BUILD_ID")?.toIntOrNull() ?: 170)`
* `META-INF/xposed/module.prop`：`versionCode=170`（与清单保持一致）

产物改名为 `dist/MBGA-v1.5.0-libxposed102-vc170.apk`，校验值见 `dist/VERIFY.md`。
