# 设备适配依据

## 2026-10-05 APK 对比

| 项目 | 一加 Ace 6（PLQ110） | 一加 15（PLK110） |
| --- | --- | --- |
| SystemUI 版本 | 17.99.02 | 17.99.02 |
| APK SHA-256 | `29ff19621cb37017cfd195109e2b4ed202175c72c1a0d247519ec866d1dabcbe` | `31a3d5ad620488c64a77ac8a08b051686b2e177d84445c614cb904a129391a77` |
| 面板背景字段 | `OplusVolumeDialogView.mVolumeBackgroundBlurDrawable` | `OplusVolumeDialogView.blurHostHelper.panelBackground` |
| 滑块可见边界接口 | `OplusVolumeBarMaterialHost.resolveVisualCapsuleInto(Rect)` | `OplusVolumeBarMaterialHost.resolveVisualShapeInto(Rect)` |
| 原生背景创建 | `getVolumePanelBackground(Context, boolean, View, int, float)`，调用时 blurRadius=800 | `getVolumePanelBackground(Context, boolean, View, float)`，内部使用 blurRadius=250 |
| 材质管理 | AutoBlur / Motion | `VolumePlatformBlurSession`，由 ROM 选择 PlatformStatic / Motion / solid |
| 独立描边类 | `OplusVolumeSettingsButtonMaterialHost` 存在 | 该类不存在，保留系统平台材质渲染 |
| 收起时更多按钮容器 | `LinearLayout` | `OplusVolumeSideAccessoryButton : FrameLayout` |

0.3.5 将收起按钮容器、缓存键和触摸命中检查统一为 `ViewGroup`；两者共同的父级按钮栏仍是 `LinearLayout`，继续使用其排列和布局参数。修复一加 15 因容器转换失败而提前返回、不创建快捷按钮的问题。QS 图标类由 SystemUI 类加载器加载。

模块按 `Build.MODEL` 优先选择上述设备配置；别名支持 OnePlus 15、一加15、OnePlus Ace 6、一加Ace6。0.3.7 对其他型号自动尝试一加 15、Ace 6 两套接口；已知型号的首选方案不匹配时也尝试另一套。检测背景字段链、滑块边界方法和必要容器字段；两套都不匹配时不安装 SystemUI hooks。尝试结果和缺失接口写入带版本标记的日志。接口匹配不等于该机型通过实机验证。

两份 APK 的 `vertical_volume_row.xml` 在将资源 ID 还原为资源名后完全相同。关键资源值相同：行宽 56dp、滑块宽 46dp、列表高 224dp、纵向间距资源 11dp、横屏原生留白 45dp、圆角 23dp、阴影上/下留白 4dp/8dp。因此不人为添加机型尺寸偏移，布局继续读取当前 ROM 资源。

一加 15 的背景仍为 AutoBlurDrawable，可通过 `getViewBlurProxy()` 修改面板圆角。模块保留 blurRadius、捕获会话及原生混色模式。0.3.11 允许调节展开面板混色层的 RGB 深浅，保留原透明度、混合模式和动画参数；中点恢复原始颜色，每次收起归还原配置，不累积修改。低高斯/省电模式的纯色背景保持系统行为。

0.3.11 新接口描边使用 `VolumeBlurLightParams.resolveSideBarBackgroundStroke()` 的原生参数，通过 `adaptVolumeStrokeCorner()` 匹配外框圆角和尺寸，再由 `VolumeBlurManager.hotUpdateStaticPlatformLightAndStroke()` 同步已缓存的平台模糊 Drawable。收起恢复原描边及圆角参数。旧接口继续使用独立的原生描边渲染器。

设置页分别控制响铃切换、勿扰、耳机模式按钮，默认均开启。两个接口共用设置；隐藏按钮使用 GONE，不保留空位和触摸区域，关闭耳机按钮停止耳机状态监听。设置在下次显示/展开时读取，无需为每次设置变动重启 SystemUI。0.3.11 视觉与设备交互验证由用户进行。

0.3.12 将底色设置与描边接口失败隔离。BlurConfig 是可变数据类，不能用其会变化的 hashCode 作为缓存索引；底色、圆角、描边快照改用 IdentityHashMap，保证重复应用和收起恢复可找到同一个配置。滑块数值变化立即保存。日志新增设置读取结果、实际 blurType 和两套混色参数，以及调节失败或纯色背景无法调节的原因。回归测试使用会随颜色变化 hashCode 的配置对象。

用户提供的 0.3.12 日志确认 ONEPLUS_15 已加载，tone=-0.70706105 / 0.8178077 均成功读入，但平台混色始终为原始 #92000000 / #66999999，且无调节异常。0.3.13 取消按 javaClass.simpleName 判定混色类型，改用零参数颜色 getter 和单参数 copy 的接口契约，避免缺少嵌套类元数据或运行时类名不同导致静默跳过。回归测试覆盖两个接口使用不同运行时类名时仍能修改颜色并恢复原对象。设置页移除重复说明和开关旁的已开启/已关闭，继续使用框架的 LiquidToggle。

0.3.18 修复新版原生混色刷新覆盖描边：`refreshVolumePlatformMixColors()` 调用 `VolumeBlurConfigWriter.writePanelPlatform()`，随后 `applyPanelPlatformDefaults()` 会清空渐变描边并关闭圆角裁剪。模块在该配置写入结束后，仅对当前展开面板已登记的同一个 BlurConfig 恢复原生描边及圆角，并重新应用底色调节；保留当前原生 blurAmount，使淡出过程中刷新不会恢复成不透明描边。未登记的滑块、按钮和收起背景不受影响，旧接口保持原流程。测试覆盖原生刷新、淡出中刷新及淡出终点后的再次显示；视觉验证交由用户。

0.3.19 在当前展开面板的 AutoBlurDrawable.draw 前，通过原生 getBlurDrawable(defaultDrawable) 取得实际绘制对象，仅在它是 PlatformBlurDrawable 时向其底层 BlurDrawable 调用 setEnableBlurShader(true)、setGradientStrokeLineParams() 和 setCornerParams()；不创建额外背景或自绘描边。淡出沿用该代理当前 blurAmount，收起归还原 Shader 开关。日志记录实际渲染对象缺失、Shader 是否有效、透明度、原生描边与圆角参数，每个显隐阶段仅记录一次。此前用户再次提供的同一路径日志仍是 0.3.12，因此无法证明 0.3.18 的实际运行失败点；0.3.19 的视觉结果仍待实机确认。

最新提供的 0.3.18 日志已确认 SystemUI 加载 0.3.18(24)、ONEPLUS_15 匹配且使用 BlurTypePlatformStatic，展开配置无描边异常；但没有原生刷新后恢复描边的记录，因此该次流程不能证明 0.3.18 假设的覆盖点就是实际原因。保存于 evidence/op15-0318-no-stroke.log。0.3.19 构建、Lint、26 项单元测试及签名验证通过，实际描边效果待用户验证。

0.3.20 对照新版 OplusVolumeSeekBar.draw → OplusVolumeBarMaterialHost.syncBeforeDraw → syncVolumeBarPlatformLightBounds → writeBarPlatform，外框改用相同的展开音量条 LightTemplate：随亮暗主题选择 BAR_STROKE_LIGHT/DARK，包含原生 OpticsParams 和 InnerShadowParams，通过 applyTemplate 匹配外框尺寸和圆角。此前使用收起侧边条的描边预设，且未带完整光学包。模块外框描边在系统全局描边开关关闭时仍使用原生预设，但不修改该开关。保留原面板混色、模糊半径和代理；恢复时归还光学包原值，绘制时用原生 Drawable alpha 同步整体光学包淡出，避免描边透明度重复相乘。原生 COUISpotLightEffectDrawable 仅负责按压聚光，未添加到外框。实机效果待用户验证。

## 验证范围

2026-10-06：Ace 6 安装上述一加 15 APK 后，实机确认自动选择 ONEPLUS_15，收起按钮可见、四条滑块对齐。0.3.8 开启新版静态模糊的圆角裁剪，并通过 `assignSideAccessoryBackground` 单独绑定新增按钮背景；实机截图确认圆角及模糊恢复。按钮点击切换尚未完成验收。0.3.10 新接口外侧快捷图标跟随原生更多按钮的实时 imageTintList；系统清除 tint 时使用原生 more_row_stream_system 矢量的默认 #4D4D4D。勿扰开启仍为原蓝色，旧接口颜色保持不变。

0.3.10 实机颜色验证：`evidence/op15-0310-adaptive.png` 和 `evidence/op15-0310-restore-relaunch.png` 确认普通图标与原生更多按钮一致；`evidence/op15-0310-dnd-active.png` 确认勿扰开启仍为蓝色。测试结束已确认响铃 NORMAL、勿扰 OFF。当前实机仅验证了平台模糊生效时的颜色；清除 tint 时的默认颜色由该 APK 矢量资源确认。

- 已从两份 APK 反编译核对背景字段、边界方法及音量面板字段。
- 型号匹配单元测试覆盖两组代码/别名，以及 15R、Ace 6T 等相似名称不能误匹配的情况。
- 本次未连接实机；一加 15 的展开/收起、圆角稳定性、亮暗主题和横屏效果仍需设备验证。
