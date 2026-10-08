# 新版 SystemUI 按钮材质接入

来源：项目根目录的一加 15 SystemUI 17.99.02 APK。以下路径来自该 APK 的反编译源码，均为 ROM 内部接口。

控制中心调用链：

- `PluginDrawableUtils.getSepQSInactiveBlurDrawableBuilder` / `getStdQSInactiveBlurDrawableBuilder` 创建 `MixColorPluginDrawable.BuilderConfig`。
- `QSBlurConfigProvider.getInactiveBlurConfig(night, stroke, mirrorScale)` 提供前景按钮混色及背景面板混色，两层由 `BlurMixMultiWithShader` 保存。`getDefaultBlurRadius()` 返回 ROM 的按钮模糊参数。
- `GradientStrokeLineAdapter.getQSPluginBlurLightConfig(night, true)` 提供原生渐变描边、光学效果和内阴影。
- `MixColorPluginDrawable` 通过 `AutoBlurDrawable`、`ViewBlurProxy` 和 `PlatformBlurDrawable` 绘制；`adaptStrokeLineParams` 和 `adaptStrokeCornerParams` 将光照及形状应用到 `BlurConfig`。

通知中心调用链：

- `ClearAllController.updatePlatformBlurDrawable` 调用 `ViewBlurManager.requireBlurProxyForView(..., CardType.CLEAR_ALL)`。
- `NotificationPlatFormBlurParamsManager` 提供按钮混色、`DecoratorsStrokeLineGroup` 及 `DecoratorsInnerShadowGroup`，最终也经由 `ViewBlurProxy` / `PlatformBlurDrawable` 绘制。

音量面板使用控制中心未选中按钮的原生光照包，采用面板自身圆角，不修改全局材质开关。默认混色和模糊深度以展开前实际显示的原生音量条背景为基准：展开入口先捕获背景 `BlurConfig` 的混色、颜色和模糊半径，再同步到底板和展开后的音量条背景。白色进度填充及图标不参与调色。

深浅调节仅作用于底板：默认先将混色的 RGB 向黑色偏移 8%，透明度保持原值。保存的滑块值仍为 -1 到 1，绘制时映射为 -0.25 到 0.25，设置页显示实际的 0–25% 调节幅度；两端只在默认底色附近调节，不再推至纯黑或纯白。该映射同时用于新版混色、旧版 tint 及原生材质回退路径。音量条始终保持原生基准，旧版动态音量条的 tint 和模糊参数不再跟随滑块调色。底板与每条音量条均持有独立的混色实例，避免共享可变 shader 缓存或淡出标志。缓存调色结果，避免动画每帧反复克隆混色对象。新版 `VolumeBlurConfigWriter.writePanelPlatform` / `writeBarPlatform` 写入之后重新应用各自的参数，避免系统展开混色覆盖设置。保持各自捕获区域、几何和不透明度配置；底板混色开启 `alphaWithBlurAmount`，保留原生消失淡出。`Surface bound` 日志记录各背景的角色、配置和混色对象标识及保存的调节值。

旧版静态模糊条直接复用其 `BlurConfig`；旧版动态模糊条通过 ROM 原有的背景查找器取得 `BackgroundBlurDrawable` 与原生 tint，使用该 APK 中 `getVolumeBarBackground` 的材质参数和模糊半径接口。底板在原生模糊层上叠加原生 tint，并与边框一起同步淡出和形变。退出展开模式时恢复每个背景自己的原参数，不把展开后的调色留在收起的音量条上。

诊断日志中的 `Collapsed surface baseline` 显示本次展开使用的基准，`Panel uses native QS button light` 确认新版原生光照路径。

新版描边同步在 `PlatformBlurDrawable.draw` 入口执行，由当前音量底板 `AutoBlurDrawable.draw` 的绘制作用域限定，其他原生背景不受影响。此时 ROM 已选定并准备实际 renderer，模块不再提前调用 `ViewBlurProxy.getBlurDrawable`；光照参数只在实际值不同的时候写入，透明度独立跟随原生淡出。面板跟随原生行形变时不启动第二个计时动画。以上是源码层面的开销削减，横屏帧率仍需目标设备验收。

发布构建和契约测试不能证明实机外观；需在目标 ROM 上验证亮暗主题、展开及消失、底色滑块和隐藏名称后的间距。
