package dev.glass.soundbar

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** The ROM owns the dialog, materials, rows, gestures and audio policy. */
class SoundbarModule : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != "com.android.systemui") return
        runCatching { PanelSettings.connectRemote(getRemotePreferences("panel")) }
            .onFailure { Log.e("GlassSoundbar", "LSPosed preferences unavailable; using persistent snapshot fallback", it) }
        val profile = DeviceProfile.detect(param.classLoader)
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val systemUiContext = activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
            systemUiContext?.let(ModuleDebugLog::initialize)
        }.onFailure { Log.w("GlassSoundbar", "Debug log startup deferred", it) }
        log(Log.INFO, "GlassSoundbar", DeviceProfile.detectionSummary)
        if (profile == null) {
            ModuleDebugLog.w("GlassSoundbar", "Neither volume contract matched; keeping stock panel. ${DeviceProfile.detectionSummary}")
            return
        }
        try {
            val type = Class.forName("com.oplus.systemui.volume.OplusVolumeDialogImpl", false, param.classLoader)
            val dialogHooks = type.declaredMethods.filter { it.name == "showH" || it.name == "expandPanel" }
            check(dialogHooks.any { it.name == "showH" } && dialogHooks.any { it.name == "expandPanel" }) {
                "Required native volume dialog hooks were not found"
            }
            dialogHooks.forEach { method ->
                hook(method).intercept { chain ->
                    var native: Any? = null
                    runCatching {
                        val target = chain.thisObject!!
                        val context = Reflect.field(target, "mContext") as Context
                        ModuleDebugLog.initialize(context)
                        native = Reflect.field(target, "mOplusVolumeDialogView")!!
                        if (method.name == "showH") NativeActions.setWindowTouchThrough(native, false)
                        if (method.name == "expandPanel" && Reflect.call(native, "isDismissing") != true) {
                            val settings = PanelSettings.read(Reflect.field(target, "mContext") as Context)
                            NativePanelLayout.apply(native, settings)
                        } else if (method.name == "showH" && Reflect.field(target, "mExpanded") != true) {
                            NativePanelLayout.restore(native)
                        }
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Stock dialog retained", it) }
                    val result = chain.proceed()
                    if (method.name == "expandPanel") runCatching {
                        native?.let { NativePanelLayout.hideTitle(it) }
                        ModuleDebugLog.i("GlassSoundbar", "Expanded through original more-button flow")
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Native layout failed", it) }
                    if (method.name == "showH") runCatching {
                        val target = chain.thisObject!!
                        val view = native ?: Reflect.field(target, "mOplusVolumeDialogView")!!
                        val settings = PanelSettings.read(Reflect.field(target, "mContext") as Context)
                        val root = Reflect.field(view, "mDialogView") as android.view.ViewGroup
                        root.post {
                            if (settings.enabled && Reflect.field(target, "mExpanded") != true) NativeActions.showCollapsed(view, settings)
                            else NativeActions.hide(view)
                        }
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Collapsed controls failed", it) }
                    result
                }
            }
            // Both ROMs use this entry point exclusively for the two alert-slider
            // instructions, either as an expanded-panel tip or a collapsed toast.
            type.declaredMethods.filter {
                it.name == "isNeedToShowToastUi" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            }.forEach { method ->
                hook(method).intercept { chain ->
                    val enabled = runCatching {
                        PanelSettings.read(Reflect.field(chain.thisObject!!, "mContext") as Context).enabled
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Alert-slider tip settings read failed", it) }
                        .getOrDefault(false)
                    if (enabled) null else chain.proceed()
                }
            }
            type.declaredMethods.filter { it.name == "inRangeOfView" && it.parameterCount == 2 }.forEach { method ->
                hook(method).intercept { chain ->
                    val stock = chain.proceed() as Boolean
                    if (stock) true else runCatching {
                        NativeActions.isActionHit(
                            chain.getArg(1) as? android.view.View,
                            chain.getArg(0) as android.view.MotionEvent
                        )
                    }.getOrDefault(false)
                }
            }
            val util = Class.forName("com.oplus.systemui.volume.utils.VolumeLayoutUtil", false, param.classLoader)
            util.declaredMethods.filter { it.name == "getVolumePanelWidth" }.forEach { method ->
                hook(method).intercept { chain ->
                    NativePanelLayout.activeRoot?.layoutParams?.width ?: chain.proceed()
                }
            }
            val animator = Class.forName("com.oplus.systemui.volume.anim.VolumeItemAnimator", false, param.classLoader)
            animator.declaredMethods.filter { it.name == "getTranslationY" }.forEach { method ->
                hook(method).intercept { chain ->
                    if (NativePanelLayout.activeRoot != null) 0f else chain.proceed()
                }
            }
            animator.declaredMethods.filter { it.name == "getEndX" }.forEach { method ->
                hook(method).intercept { chain ->
                    if (NativePanelLayout.activeRoot == null) {
                        chain.proceed()
                    } else {
                        val target = runCatching {
                            val holder: Any = chain.getArg(0)
                            val animatorObject = chain.thisObject!!
                            val position = Reflect.call(animatorObject, "resolveHolderStaggerPosition", holder) as Int
                            val itemView = Reflect.field(holder, "itemView") as android.view.View
                            val rowWidth = itemView.width.takeIf { it > 0 } ?: itemView.layoutParams.width
                            NativePanelLayout.targetRowTranslationPx(position, rowWidth, itemView.left)?.also { endX ->
                                if (position == 0) NativePanelLayout.activeRoot?.let {
                                    NativePanelMotion.trackLeadingRow(it, itemView, endX)
                                }
                            }
                        }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Exact row placement failed", it) }.getOrNull()
                        target ?: chain.proceed()
                    }
                }
            }
            val adapter = Class.forName("com.oplus.systemui.volume.VolumeListAdapter", false, param.classLoader)
            adapter.declaredMethods.filter { it.name == "onBindViewHolder" && !it.isBridge }.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        NativePanelLayout.normalizeRows(Reflect.field(chain.getArg(0), "itemView") as android.view.View)
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Row alignment failed", it) }
                    result
                }
            }
            type.declaredMethods.filter { it.name in listOf("onStateChangedH", "onConnectionStateChanged", "onActiveDeviceChanged", "onBluetoothStateChanged") }.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val target = chain.thisObject!!
                        val native = Reflect.field(target, "mOplusVolumeDialogView")!!
                        val root = Reflect.field(native, "mDialogView") as android.view.ViewGroup
                        root.post {
                            runCatching {
                                NativeActions.refresh(native)
                                if (NativePanelLayout.activeRoot != null) NativePanelLayout.normalizeRows()
                            }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Native action state refresh failed", it) }
                        }
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Native action refresh dispatch failed", it) }
                    result
                }
            }
            val view = Class.forName("com.oplus.systemui.volume.view.OplusVolumeDialogView", false, param.classLoader)
            if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) {
                val platform = Class.forName("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable", false, param.classLoader)
                hook(platform.getDeclaredMethod("draw", android.graphics.Canvas::class.java)).intercept { chain ->
                    NativeMaterial.beforePlatformDraw(chain.thisObject!!, chain.getArg(0) as android.graphics.Canvas)
                    chain.proceed()
                }
                val button = Class.forName("com.oplus.systemui.volume.view.OplusVolumeSideAccessoryButton", false, param.classLoader)
                // These icons keep QS animation colors and the active DND tint.
                // Let the native control own touch/light, but only stock icons use its tint writer.
                hook(button.getDeclaredMethod("refreshIconTintForPlatformBlur")).intercept { chain ->
                    if (NativeActions.isAddedNativeButton(chain.thisObject)) null else chain.proceed()
                }
                view.declaredMethods.filter {
                    it.name == "refreshVolumeSpotLightType" || it.name == "access\$refreshVolumeSpotLight" ||
                        it.name == "access\$refreshVolumeBlurLight"
                }.forEach { method ->
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        runCatching {
                            val native = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) chain.getArg(0) else chain.thisObject!!
                            NativeActions.refreshButtonEffects(native)
                        }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Side accessory effects refresh failed", it) }
                        result
                    }
                }
                val writer = Class.forName("com.oplus.systemui.volume.utils.material.VolumeBlurConfigWriter", false, param.classLoader)
                writer.declaredMethods.filter {
                    it.name == "writePanelPlatform" || it.name == "writeBarPlatform" || it.name == "writeProgressPlatform"
                }.forEach { method ->
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        runCatching { NativePanelLayout.restoreMaterialAfterNativeRefresh(chain.getArg(0)) }
                            .onFailure { ModuleDebugLog.e("GlassSoundbar", "Panel material restore after native refresh failed", it) }
                        result
                    }
                }
                val slider = Class.forName("com.oplus.systemui.volume.OplusVolumeSeekBar", false, param.classLoader)
                slider.declaredMethods.filter { it.name == "ensureProgressPlatformBlur" }.forEach { method ->
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        runCatching { NativeVolumeSurface.bindSlider(chain.thisObject as android.view.View) }
                            .onFailure { ModuleDebugLog.e("GlassSoundbar", "Progress material binding failed", it) }
                        result
                    }
                }
            }
            val blurDrawable = Class.forName("com.oplusos.systemui.common.blurability.drawable.AutoBlurDrawable", false, param.classLoader)
            blurDrawable.declaredMethods.filter { it.name == "draw" && it.parameterCount == 1 }.forEach { method ->
                hook(method).intercept { chain ->
                    val background = chain.thisObject as android.graphics.drawable.Drawable
                    runCatching { NativeVolumeSurface.beforeDraw(background) }
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Expanded surface draw sync failed", it) }
                    NativePanelMotion.applyBackgroundBounds(background)
                    NativeMaterial.drawBackground(background) { chain.proceed() }
                }
            }
            view.declaredMethods.filter { it.name == "showOrHideAnimation" }.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val native = chain.thisObject!!
                        val root = Reflect.field(native, "mDialogView") as android.view.View
                        if (PanelSettings.read(root.context).enabled) {
                            if (chain.getArg(1) as Boolean) NativePanelMotion.show(root)
                            else NativePanelMotion.hide(root, NativePanelLayout.collapsedPanelWidth())
                        }
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Panel morph failed", it) }
                    result
                }
            }
            view.declaredMethods.filter { it.name == "initDialog" }.forEach { method ->
                hook(method).intercept { chain ->
                    (Reflect.field(chain.thisObject!!, "mDialogView") as? android.view.View)?.let(NativePanelMotion::cancel)
                    chain.proceed()
                }
            }
            val insetsListener = Class.forName(
                "com.oplus.systemui.volume.view.OplusVolumeDialogView\$getSeekbarInternalInsetsListener\$1",
                false,
                param.classLoader
            )
            insetsListener.declaredMethods.filter { it.name == "onComputeInternalInsets" && it.parameterCount == 1 }.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val owner = Reflect.field(chain.thisObject!!, "this\$0") ?: return@runCatching
                        val more = Reflect.field(owner, "mMoreRowStreamLl") as? android.view.View ?: return@runCatching
                        NativeActions.extendTouchableRegion(more, chain.getArg(0))
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Touchable region extension failed", it) }
                    result
                }
            }
            view.declaredMethods.filter { it.name == "showSliderVolume" }.forEach { method ->
                hook(method).intercept { chain ->
                    runCatching { NativeActions.setWindowTouchThrough(chain.thisObject!!, false) }
                        .onFailure { Log.e("GlassSoundbar", "Window touch restore failed", it) }
                    chain.proceed()
                }
            }
            view.declaredMethods.filter { it.name == "dismissH" }.forEach { method ->
                hook(method).intercept { chain ->
                    runCatching {
                        val native = chain.thisObject!!
                        NativeActions.setWindowTouchThrough(native, true)
                        // Keep injected collapsed controls visible and mirror the
                        // ROM accessory animation until SystemUI finishes hide.
                        NativeActions.beginDismiss(native)
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Dismiss animation sync failed", it) }
                    chain.proceed()
                }
            }
            view.declaredMethods.filter { it.name == "doAfterHide" }.forEach { method ->
                hook(method).intercept { chain ->
                    runCatching {
                        val native = chain.thisObject!!
                        (Reflect.field(native, "mDialogView") as? android.view.View)?.let(NativePanelMotion::cancel)
                        NativePanelLayout.detachMaterialEdge(native)
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Panel morph cleanup failed", it) }
                    val result = chain.proceed()
                    runCatching { NativeActions.hide(chain.thisObject!!) }
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Dismiss final cleanup failed", it) }
                    result
                }
            }
            view.declaredMethods.filter { it.name == "setExpandSuperVolumeAnim" }.forEach { method ->
                hook(method).intercept { chain ->
                    if (NativePanelLayout.activeNative === chain.thisObject) null else chain.proceed()
                }
            }
            log(Log.INFO, "GlassSoundbar", "Native-only volume hooks installed; no overlay or simulated controls")
        } catch (t: Throwable) { log(Log.ERROR, "GlassSoundbar", "Stock panel retained", t) }
    }
}
