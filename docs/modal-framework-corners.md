# 同步框架的模态面板圆角修复

日期：2026-10-17。依据框架 `C:/IDE/Android/liquidglass_sketch/docs/glass-panel-corners.md`。

## 接入范围

本项目的 `settings.gradle.kts` 将 `:liquidglass` 直接指向本地框架源码，未使用旧版 AAR，也没有复制着色器。重新编译时纳入框架已经完成的两项修复：

- `backdrop/highlight/HighlightModifier.kt` 使用 `shapeProvider.innerShape` 将原始圆角类型传给高光着色器，保留缓存轮廓作为裁切来源。
- `backdrop/Shaders.kt` 的两份折射、两份高光使用 `radiusAt(centeredCoord, cornerRadii)`，正确选择四角半径。

本项目实际有两条玻璃模态绘制链：

| 模态入口 | 依赖路径 |
| --- | --- |
| 媒体、铃声、通知、闹钟颜色面板 | `VolumeIconColorPicker → Ios27ColorPickerDialog → Ios27BottomSheetDialog → LiquidDialogLayer` |
| 玻璃外观的 Hook 日志级别检查面板 | `HookLogInspector → LiquidDialogLayer` |

两者均提供真实 `CornerBasedShape`，没有在宿主中包装为 GenericShape 或复制旧版渲染器，因此共用引擎修复会同时生效。颜色面板仍保留 32dp 顶角及设备底角、框架材质、外部阴影和中文/预设/动画；日志面板也保留现有参数。没有通过关闭高光、改为胶囊、加假描边或更改折射强度补偿。

关闭液态玻璃的颜色面板使用框架纯色 fallback；关闭液态玻璃的日志选择由 ColorOS 原组件承载。这两条分支不使用本地高光/折射着色器。

## 验证

框架圆角专项 Node 7 项测试、原始基线/登记修改哈希校验和本项目颜色面板框架一致性检查均已通过。框架 59 项和应用 64 项 JVM 测试通过，APK 内四份着色器均包含居中坐标调用，不含旧的局部坐标调用。已覆盖安装，深色模式四个颜色面板和玻璃日志面板打开、关闭无新增崩溃；浅色日志面板也已复查。证据在 `evidence/modal-framework-corners-20261017/`。这不等于所有光学细节与原稿逐像素一致。

## 移除面板背后遮罩

用户随后要求删除面板下方的遮罩。颜色面板改用 `Color.Transparent`，删除不再需要的 scrimAlpha 动画参数；玻璃日志面板原本已透明，原生日志弹窗显式清除 `FLAG_DIM_BEHIND` 并设 dimAmount 为零。外部点击关闭层保留交互，面板材质和阴影不变。应用诊断标记更新为 `framework-modal-no-dim-20261017`。
