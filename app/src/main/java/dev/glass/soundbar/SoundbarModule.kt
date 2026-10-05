package dev.glass.soundbar

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** The ROM owns the dialog, materials, rows, gestures and audio policy. */
class SoundbarModule : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != "com.android.systemui") return
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val systemUiContext = activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
            if (systemUiContext != null) ActivationStatus.startHeartbeat(systemUiContext)
        }.onFailure { Log.w("GlassSoundbar", "Activation heartbeat startup deferred", it) }
        try {
            val type = Class.forName("com.oplus.systemui.volume.OplusVolumeDialogImpl", false, param.classLoader)
            type.declaredMethods.filter { it.name == "showH" || it.name == "expandPanel" }.forEach { method ->
                hook(method).intercept { chain ->
                    var native: Any? = null
                    runCatching {
                        val target = chain.thisObject!!
                        ActivationStatus.startHeartbeat(Reflect.field(target, "mContext") as Context)
                        native = Reflect.field(target, "mOplusVolumeDialogView")!!
                        if (method.name == "showH") NativeActions.setWindowTouchThrough(native!!, false)
                        if (method.name == "expandPanel" && Reflect.call(native!!, "isDismissing") != true) {
                            val settings = PanelSettings.read(Reflect.field(target, "mContext") as Context)
                            NativePanelLayout.apply(native!!, settings)
                        } else if (method.name == "showH" && Reflect.field(target, "mExpanded") != true) {
                            NativePanelLayout.restore(native!!)
                        }
                    }.onFailure { Log.e("GlassSoundbar", "Stock dialog retained", it) }
                    val result = chain.proceed()
                    if (method.name == "expandPanel") runCatching {
                        native?.let { NativePanelLayout.hideTitle(it) }
                        Log.i("GlassSoundbar", "Expanded through original more-button flow")
                    }.onFailure { Log.e("GlassSoundbar", "Native layout failed", it) }
                    if (method.name == "showH") runCatching {
                        val target = chain.thisObject!!
                        val view = native ?: Reflect.field(target, "mOplusVolumeDialogView")!!
                        val enabled = PanelSettings.read(Reflect.field(target, "mContext") as Context).enabled
                        val root = Reflect.field(view, "mDialogView") as android.view.ViewGroup
                        root.post {
                            if (enabled && Reflect.field(target, "mExpanded") != true) NativeActions.showCollapsed(view)
                            else NativeActions.hide(view)
                        }
                    }.onFailure { Log.e("GlassSoundbar", "Collapsed controls failed", it) }
                    result
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
                            NativePanelLayout.targetRowTranslationPx(position, rowWidth, itemView.left)
                        }.onFailure { Log.e("GlassSoundbar", "Exact row placement failed", it) }.getOrNull()
                        target ?: chain.proceed()
                    }
                }
            }
            val adapter = Class.forName("com.oplus.systemui.volume.VolumeListAdapter", false, param.classLoader)
            adapter.declaredMethods.filter { it.name == "onBindViewHolder" && !it.isBridge }.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching { NativePanelLayout.normalizeRows() }.onFailure { Log.e("GlassSoundbar", "Row alignment failed", it) }
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
                            }.onFailure { Log.e("GlassSoundbar", "Native action state refresh failed", it) }
                        }
                    }.onFailure { Log.e("GlassSoundbar", "Native action refresh dispatch failed", it) }
                    result
                }
            }
            val view = Class.forName("com.oplus.systemui.volume.view.OplusVolumeDialogView", false, param.classLoader)
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
                    }.onFailure { Log.e("GlassSoundbar", "Touchable region extension failed", it) }
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
                        NativePanelLayout.detachMaterialEdge(native)
                        // Keep injected collapsed controls visible and mirror the
                        // ROM accessory animation until SystemUI finishes hide.
                        NativeActions.beginDismiss(native)
                    }.onFailure { Log.e("GlassSoundbar", "Dismiss animation sync failed", it) }
                    chain.proceed()
                }
            }
            view.declaredMethods.filter { it.name == "doAfterHide" }.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching { NativeActions.hide(chain.thisObject!!) }
                        .onFailure { Log.e("GlassSoundbar", "Dismiss final cleanup failed", it) }
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
