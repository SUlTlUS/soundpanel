# 颜色面板承载与原生方案回退记录

2026-10-17 按用户要求移除 ColorOS 17 颜色面板调用，删除 `NativeColorPickerSheet.kt`，两个外观模式统一使用框架 `LiquidDialogLayer`：开启液态玻璃时保留原材质，关闭时传入空 backdrop 使用框架纯色 fallback。原有中文、预设增删存储以及 240ms 入场 / 180ms 退场动画保留。ColorOS 17 设置控件和底部导航保留；导航图标使用原生标题的 ColorStateList，随深浅色及选中状态变化。

网格选框使用最大 29.5dp 宽度及 1:1 宽高比，在窄色格内同步缩小两边并保持居中，避免单独压缩宽度而拉成长方形；网格触摸和选色逻辑不变。

本次已覆盖安装并在 PLQ110 实测四个颜色入口的框架面板打开与关闭，返回、工具栏关闭及外部点击均正常。实拍选框为 92 x 92 像素；底栏深浅色截图正常。64 项测试、构建、签名和框架一致性检查通过，最终安装包哈希匹配。证据在 `evidence/framework-picker-tabbar-20261017/`。

以下是已移除原生面板方案的历史诊断记录，不代表当前颜色面板实现。

## 接口依据

- 本地框架 `docs/coloros17-extracted/settings-17.0.0/sources/com/coui/appcompat/panel/COUIBottomSheetDialog.java` 原样反编译源确认了 `COUIBottomSheetDialog(Context)`、`setContentView(View)`、`dismiss()`、`dismiss(boolean)`；`dismiss()` 转入原生可打断退场动画，`dismiss(false)` 用于配置销毁等立即清理。
- `ColorOs17SettingsComponents` 已登记该面板，其演示通过原 Settings 资源上下文构造并使用宿主 `Activity` 作为 owner。音量模块复用同一会话的公开 `context`，保留 ROM、设置包版本及平台签名门槛，不打包 COUI 类或伪造原生材质。
- 本次检查 `evidence/device-Settings.apk` 的 DEX 类定义，确认存在该面板类；同时存在呼吸灯、氛围灯等业务专用的颜色选择类。未把这些业务 Fragment 当作可直接嵌入的通用 COUI 颜色选择接口。

## 窗口和生命周期

`NativeColorPickerSheet.kt` 将共用颜色内容放入应用自己的 `ComposeView`，显式绑定应用的生命周期、ViewModel 和状态保存 owner，避免使用 Settings 类加载器里的另一份 AndroidX owner。面板本身由原 COUI 类构造，设置 owner Activity 后显示。普通关闭使用原生 `dismiss()`；退场的 dismiss 回调才释放内容并通知宿主卸载。配置变化/Activity 销毁立即清理会话自有窗口。

2026-10-16 设备堆栈确认：共享会话的 `NativeUiContext` 以宿主 Activity 为 base，`COUIPanelMultiWindowUtils.contextToActivity` 沿链取到此 Activity，随后 `getPanelNormalVisibleHeight` 使用宿主 Resources 解析 Settings 的 `coui_panel_min_padding_top`（该设备 ID `0x7f07061d`），在首次 WindowInsets 分发中抛出 `Resources$NotFoundException`。这是异步测量阶段的错误，构造外层的 try/catch 无法接住。

面板现在使用专用 `NativeSheetResourceContext`：base 使用宿主 Application，使原 COUI 走无 Activity 的资源计算分支；Resources、Assets、Theme、ClassLoader 及资源包名保持来自原会话。LayoutInflater 克隆到这个 Context，窗口和输入服务仍用真实宿主，owner Activity 与 Compose 生命周期绑定保留。宿主 Application 同时提供 AppCompat 的应用配置及 COUI 的组件回调注册。不能以外部 Settings 包 Context 为 base：该 Context 没有运行中的 Application，会在 AppCompat 配置读取或 COUI attach 时的 `registerComponentCallbacks` 触发空指针。此修正只在音量应用的面板入口生效，不改共享框架和系统设置包。

不向原生分支传递玻璃外壳、仿制圆角、渐变、iOS 位移或透明度动画。构造/显示失败明确显示原因。

## 验证边界

源码/API/路由静态检查及构建不能证明外部类在设备 attach/draw 阶段正常。需要用户在 ColorOS 17 上验证显示、系统材质、拖动/返回/外部关闭、连续打开关闭、横竖屏和预设增删。未完成这些真机项目时不宣称原生面板视觉或动画验收通过。

2026-10-16 已在 `192.168.1.148:46661` 的 PLQ110、`PLQ110_17.0.0.104(CN01)`、Settings 17.0.0 上复现旧版崩溃，并覆盖安装资源 Context 修复版。关闭液态玻璃时媒体、铃声、通知、闹钟四个入口均打开成功，媒体重复打开成功；返回键、工具栏关闭和外部点击关闭均成功，整个复测进程 PID 为 11219，未出现该进程的新 FATAL EXCEPTION。已安装 APK 的 SHA-256 与本地发布包一致：`1fdf4565a0043886642ea77e50534a9dbd5581e1d3d2655e06fe452cafb94a7a`。构建、64 项 JVM 测试、签名及框架一致性检查通过。证据在 `evidence/native-sheet-resource-crash/`。本次没有验证横屏、多窗口、预设增删及所有动画视觉细节。

## 无 ADB 时采集闪退

诊断构建 `native-sheet-crashdiag-20261016-1` 在应用启动时安装 Java 未捕获异常记录器，退出前同步保存异常链、线程、设备/构建信息和最近阶段，再交给 Android 原异常处理器；不吞掉异常继续运行。原生面板的构造、内容绑定、attach、首次组合、show 返回、关闭和销毁分别留下阶段记录。记录只写应用私有目录，不经 Hook Provider 异步队列。

覆盖安装诊断构建后复现一次，再重新打开应用，从“设置 → 日志 → 导出日志”导出；原日志导出会附加 `Application diagnostics` 和 `Last application crash`。旧版本已经发生但未记录的异常无法追溯恢复；JNI/系统强杀也不保证产生 Java 异常栈。此改动提供诊断证据，不代表已经确定或修复本次闪退原因。
