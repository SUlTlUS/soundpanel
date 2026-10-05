# 玻璃音量条

用于当前 PLQ110 ColorOS SystemUI 的 LSPosed 模块。应用设置页使用
`C:/IDE/Android/liquidglass_sketch/liquidglass`，系统 Hook 不加载该框架。

## 原生实现

- 保留 `OplusVolumeDialogImpl.showH` 的原生单条音量条。点击原生更多按钮时，
  在系统 `expandPanel` 流程中调整展开面板，不自动展开。
- 保留原生 `OplusVolumeDialogView`、`VolumeListAdapter`、`OplusVolumeRow`、
  `OplusVolumeSeekBar` 及所有音量、安全警告、触感和静音监听器。
- 保留系统 `VolumeBlurManager` 创建的背景、混色、描边和生命周期。
- 仅调整面板位置、尺寸和展开动画的布局坐标。当前实机展开面板展示媒体、铃声、通知、闹钟；
  原生其它情境音量行继续由系统决定。
- 外框四角使用同一原生模糊半径，与首列滑块按内边距同心对齐；再次显示单条时恢复原生参数。
- 0.2.2 在展开首帧前同步计算最终圆角与边距，不再在动画结束后延迟修改。
- 0.2.3 默认使用 12dp 外框内边距，宽高按原生内容计算；设置入口紧接音量区域，取消按屏幕百分比预留高度。
- 0.3.1 延续当前原生单条音量条旁的按钮布局、材质和收起动画，提供响铃模式、勿扰模式及耳机模式切换。
  耳机按钮直接切换系统支持的降噪、自适应、通透（支持时也包含关闭），不再打开蓝牙设置。
  AirPods 使用“我的设备”同款 `OplusBluetoothDevice` 能力位和状态接口，其他适配耳机使用
  SystemUI `EarphoneController` 同款 Melody Provider；未连接、无控制能力或无法读取状态时隐藏入口。
  图标复用系统三种人像模式图标，状态以设备回读为准，所有查询在后台进行。
- 不添加模拟的音量定时或断开连接功能。
- 不创建悬浮窗、不抓取屏幕、不自绘音量滑块、不在模块中实现音量定时任务。
- 横屏使用系统展开布局。关闭开关后恢复系统布局，下次按音量键生效。

## 构建

使用 Android Studio JBR 21：`gradlew.bat :app:assembleDebug :app:lintDebug`。
产物：`app/build/outputs/apk/debug/app-debug.apk`。

LSPosed 作用域：`com.android.systemui`。安装更新后须重启系统界面。
内部 ROM 类会随系统版本变更；此模块并非所有 Android ROM 通用。

## 验证记录

`evidence/native-volume.png`：原生全屏展开面板。
`evidence/native-compact2.png`：原生三列紧凑布局（锁屏背景，非桌面验收）。
`evidence/native-build.log`：构建及 Lint 记录。

2026-10-05 实机验证：三根原生滑块拖动分别改变系统读数为媒体 113/160、
铃声 7/16、闹钟 8/16。测试后恢复媒体 140、铃声 11、闹钟 12。
面板外点击关闭正常。`evidence/native-drag.png` 显示锁屏上的原生背景材质。
`evidence/native-final-build.log` 为 0.2.0 构建与 Lint 通过记录。
`evidence/actions.xml` 记录了 0.3.0 的四列音量行与底部操作区；`evidence/actions-live.png` 为对应实机画面。
响铃操作已在系统 `dumpsys audio` 历史中记录到 SystemUI 发起的 0 → 1 → 2 模式切换。
桌面效果及应用开关交互仍需解锁后验证。
