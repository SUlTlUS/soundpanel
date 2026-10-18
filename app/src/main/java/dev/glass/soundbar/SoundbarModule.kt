package dev.glass.soundbar

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** The ROM owns the dialog, materials, rows, gestures and audio policy. */
class SoundbarModule : XposedModule() {
    private fun hookLog(priority: Int, tag: String, message: String, error: Throwable? = null) {
        if (ModuleDebugLog.accepts(priority)) log(priority, tag, message, error)
    }
    @Volatile private var blockAppVolume = false
    private var frameworkPreferences: SharedPreferences? = null
    private val frameworkPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "enabled" || key == "block_app_volume") updateVolumeKeyPolicy()
    }

    private fun updateVolumeKeyPolicy() {
        val preferences = frameworkPreferences ?: return
        blockAppVolume = preferences.getBoolean("enabled", true) &&
            preferences.getBoolean("block_app_volume", false)
        ModuleDebugLog.i("GlassSoundbar", "Native volume key routing enabled=$blockAppVolume")
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        runCatching {
            frameworkPreferences = getRemotePreferences("panel").also {
                ModuleDebugLog.bind(it)
                it.registerOnSharedPreferenceChangeListener(frameworkPreferenceListener)
            }
            val policy = Class.forName("com.android.server.policy.PhoneWindowManager", false, param.classLoader)
            val dispatch = policy.getDeclaredMethod("dispatchDirectAudioEvent", KeyEvent::class.java).apply {
                isAccessible = true
            }
            val intercept = policy.declaredMethods.single {
                it.name == "interceptKeyBeforeDispatching" && KeyEvent::class.java in it.parameterTypes &&
                    (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Long::class.javaPrimitiveType)
            }
            val eventIndex = intercept.parameterTypes.indexOf(KeyEvent::class.java)
            val booleanResult = intercept.returnType == Boolean::class.javaPrimitiveType
            updateVolumeKeyPolicy()
            var loggingInitialized = false
            hook(intercept).intercept { chain ->
                // Native policy handles combinations, delays and consumed keys first.
                val result = chain.proceed()
                val event = chain.getArg(eventIndex) as KeyEvent
                if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                    event.keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) return@intercept result
                runCatching {
                    if (!loggingInitialized) {
                        ModuleDebugLog.initialize(Reflect.field(chain.thisObject!!, "mContext") as Context)
                        loggingInitialized = true
                        ModuleDebugLog.i("GlassSoundbar", "Volume key policy installed: $intercept; enabled=$blockAppVolume")
                    }
                    val passesToApp = if (booleanResult) result == false else result == 0L
                    if (!blockAppVolume || !passesToApp) return@runCatching result
                    dispatch.invoke(chain.thisObject, event)
                    if (booleanResult) true else -1L
                }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Native volume key dispatch failed", it) }
                    .getOrDefault(result)
            }
            hookLog(Log.INFO, "GlassSoundbar", "System framework volume key hook installed: $intercept")
        }.onFailure { hookLog(Log.ERROR, "GlassSoundbar", "System framework volume key hook unavailable", it) }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName == GameMultiAppVolume.packageName) {
            runCatching { PanelSettings.connectRemote(getRemotePreferences("panel")) }
                .onFailure { ModuleDebugLog.w("GlassSoundbar", "Game settings IPC unavailable; using provider fallback", it) }
            runCatching {
                val show = Class.forName(GameMultiAppVolume.automaticShow, false, param.classLoader)
                check(show.getDeclaredField("label").type == Int::class.javaPrimitiveType)
                hook(show.getDeclaredMethod("invokeSuspend", Any::class.java)).intercept { chain ->
                    val seed: Any? = chain.getArg(0)
                    val suppress = runCatching { GameMultiAppVolume.shouldSuppress(chain.thisObject!!, seed) }
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Game app-volume overlay check failed", it) }
                        .getOrDefault(false)
                    if (suppress) {
                        ModuleDebugLog.i("GlassSoundbar", "Game automatic app-volume overlay suppressed")
                        seed
                    } else chain.proceed()
                }
                hookLog(Log.INFO, "GlassSoundbar", "Game automatic app-volume overlay hook installed")
            }.onFailure { hookLog(Log.ERROR, "GlassSoundbar", "Game app-volume overlay hook unavailable", it) }
            return
        }
        if (param.packageName != "com.android.systemui") return
        runCatching { PanelSettings.connectRemote(getRemotePreferences("panel")) }
            .onFailure { ModuleDebugLog.e("GlassSoundbar", "LSPosed preferences unavailable; using persistent snapshot fallback", it) }
        val profile = DeviceProfile.detect(param.classLoader)
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val systemUiContext = activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
            systemUiContext?.let(ModuleDebugLog::initialize)
        }.onFailure { ModuleDebugLog.w("GlassSoundbar", "Debug log startup deferred", it) }
        hookLog(Log.INFO, "GlassSoundbar", DeviceProfile.detectionSummary)
        if (profile == null) {
            ModuleDebugLog.w("GlassSoundbar", "Neither volume contract matched; keeping stock panel. ${DeviceProfile.detectionSummary}")
            return
        }
        // Optional multi-app UI hooks must not affect audio data or prevent the main hooks.
        runCatching {
            val dialog = Class.forName("com.oplus.systemui.volume.OplusVolumeDialogImpl", false, param.classLoader)
            val row = Class.forName("com.oplus.systemui.volume.view.OplusVolumeRow", false, param.classLoader)
            check(row.getDeclaredField("isMultiMediaVolume").type == Boolean::class.javaPrimitiveType)
            val visible = dialog.getDeclaredMethod("shouldBeVisibleH", row, row, Boolean::class.javaPrimitiveType)
            check(visible.returnType == Boolean::class.javaPrimitiveType)
            hook(visible).intercept { chain ->
                val result = chain.proceed()
                val hide = runCatching {
                    NativeMultiAppVolume.shouldHideRow(chain.thisObject!!, chain.getArg(0), chain.getArg(2) as Boolean)
                }.onFailure { ModuleDebugLog.e("GlassSoundbar", "App-volume slider visibility failed; keeping native result", it) }
                    .getOrDefault(false)
                if (hide) false else if (result == true) result else {
                    val keepAlarm = runCatching {
                        NativePanelRows.shouldKeepAlarm(chain.thisObject!!, chain.getArg(0), chain.getArg(2) as Boolean)
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Call alarm visibility check failed; keeping native result", it) }
                        .getOrDefault(false)
                    if (keepAlarm) true else result
                }
            }
            ModuleDebugLog.i("GlassSoundbar", "App-volume visibility and call alarm retention hooks installed")
        }.onFailure { ModuleDebugLog.e("GlassSoundbar", "App-volume slider visibility hook unavailable; keeping stock rows", it) }
        runCatching {
            val adapter = Class.forName("com.oplus.systemui.volume.VolumeListAdapter", false, param.classLoader)
            val filter = adapter.getDeclaredMethod("filterSideList")
            check(filter.returnType == Boolean::class.javaPrimitiveType)
            check(List::class.java.isAssignableFrom(adapter.getDeclaredField("sideShowList").type))
            hook(filter).intercept { chain ->
                val result = chain.proceed()
                val removed = runCatching { NativeMultiAppVolume.filterSideRows(chain.thisObject!!) }
                    .onFailure { ModuleDebugLog.e("GlassSoundbar", "App-volume side-list filter failed; keeping native rows", it) }
                    .getOrDefault(false)
                if (removed) true else result
            }
            ModuleDebugLog.i("GlassSoundbar", "Automatic app-volume slider side-list hook installed")
        }.onFailure { ModuleDebugLog.w("GlassSoundbar", "App-volume side-list hook unavailable", it) }
        runCatching {
            val adapter = Class.forName("com.oplus.systemui.volume.VolumeListAdapter", false, param.classLoader)
            val select = adapter.getDeclaredMethod("switchToDataSourcePanel", Boolean::class.javaPrimitiveType)
            check(adapter.getDeclaredField("panelShowList").type == java.util.ArrayList::class.java)
            hook(select).intercept { chain ->
                val owner = chain.thisObject!!
                val selection = runCatching { NativePanelRows.prepare(owner) }
                    .onFailure { ModuleDebugLog.e("GlassSoundbar", "Call panel row selection unavailable; keeping native list", it) }
                    .getOrNull()
                try {
                    chain.proceed()
                } finally {
                    if (selection != null) runCatching { NativePanelRows.finish(owner, selection) }
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Call panel display cache restore failed", it) }
                }
            }
            ModuleDebugLog.i("GlassSoundbar", "Call panel alarm retention hook installed")
        }.onFailure { ModuleDebugLog.w("GlassSoundbar", "Call panel alarm retention unavailable", it) }
        // Optional color hooks must never prevent installation of the main panel hooks.
        runCatching {
            var iconType: Class<*>? = Class.forName("com.oplus.anim.EffectiveAnimationView", false, param.classLoader)
            var draw: java.lang.reflect.Method? = null
            while (iconType != null && draw == null) {
                draw = iconType.declaredMethods.firstOrNull {
                    it.name == "onDraw" && it.parameterTypes.contentEquals(arrayOf(android.graphics.Canvas::class.java))
                }
                iconType = iconType.superclass
            }
            hook(checkNotNull(draw) { "Animated icon draw entry missing" }).intercept { chain ->
                NativeVolumeIconColors.draw(chain.thisObject as android.view.View,
                    chain.getArg(0) as android.graphics.Canvas) { chain.proceed() }
            }
            val ability = Class.forName("com.oplus.systemui.volume.ability.IconAbility", false, param.classLoader)
            val updates = ability.declaredMethods.filter { it.name == "updateVolumeRowH" && it.parameterCount == 3 }
            check(updates.isNotEmpty()) { "Volume icon update entry missing" }
            updates.forEach { method ->
                hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching { NativeVolumeIconColors.update(chain.getArg(0)) }
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Volume icon color update failed", it) }
                    result
                }
            }
            ModuleDebugLog.i("GlassSoundbar", "Volume icon color hooks installed")
        }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Volume icon colors unavailable; keeping native colors", it) }
        try {
            val feature = Class.forName("com.oplusos.systemui.common.feature.FeatureOption", false, param.classLoader)
            val sideMethod = feature.getDeclaredMethod("isOplusVolumeKeyInRight")
            val stockRight = sideMethod.invoke(null) as Boolean
            var onRight = stockRight
            val layoutSides = java.util.WeakHashMap<Any, Boolean>()
            hook(sideMethod).intercept { onRight }
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
                        if (method.name == "showH") {
                            val settings = PanelSettings.read(context)
                            onRight = if (settings.enabled) !settings.leftSide else stockRight
                            if (layoutSides[native] != onRight) {
                                if (NativePanelLayout.activeNative === native) NativePanelLayout.restore(native)
                                Reflect.set(target, "mConfigChanged", true)
                            }
                            NativeActions.setWindowTouchThrough(native, false)
                        }
                        if (method.name == "expandPanel" && Reflect.call(native, "isDismissing") != true) {
                            val settings = PanelSettings.read(Reflect.field(target, "mContext") as Context)
                            NativePanelLayout.apply(native, settings)
                        } else if (method.name == "showH" && Reflect.field(target, "mExpanded") != true &&
                            NativePanelLayout.activeNative === native) {
                            NativePanelLayout.restore(native)
                        }
                        if (method.name == "showH" && Reflect.field(target, "mExpanded") != true) {
                            NativeVolumeLength.apply(native, PanelSettings.read(context))
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
                        runCatching {
                            (Reflect.field(target, "mRows") as? List<*>)?.filterNotNull()?.forEach {
                                NativeVolumeIconColors.update(it, settings)
                            }
                        }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Volume icon colors refresh failed", it) }
                        NativeVolumeLength.prepareShow(view, settings)
                        root.post {
                            if (settings.enabled && Reflect.field(target, "mExpanded") != true) NativeActions.showCollapsed(view, settings)
                            else NativeActions.hide(view)
                            NativePanelPosition.apply(view, settings)
                        }
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Collapsed controls failed", it) }
                    result
                }
            }
            type.declaredMethods.filter { it.name == "startThickAnim" && it.parameterCount == 1 }.forEach { method ->
                hook(method).intercept { chain ->
                    if (chain.getArg(0) as Boolean) {
                        val context = Reflect.field(chain.thisObject!!, "mContext") as Context
                        val settings = PanelSettings.read(context)
                        if (settings.enabled && !settings.autoThin) return@intercept null
                    }
                    chain.proceed()
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
                            NativePanelLayout.targetRowTranslationPx(position, itemView)?.also { endX ->
                                if (NativePanelLayout.isLeadingRow(position)) NativePanelLayout.activeRoot?.let {
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
                val hosts = Class.forName("com.oplus.systemui.volume.view.OplusVolumeDialogView\$blurHostHelper\$1", false, param.classLoader)
                hook(hosts.getDeclaredMethod("sideAccessoryHosts")).intercept { chain ->
                    @Suppress("UNCHECKED_CAST")
                    val stock = chain.proceed() as List<android.view.View>
                    runCatching {
                        NativeActions.materialHosts(Reflect.field(chain.thisObject!!, "this\$0")!!, stock)
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Side accessory material hosts failed", it) }
                        .getOrDefault(stock)
                }
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
                        val settings = PanelSettings.read(root.context)
                        if (settings.enabled) {
                            if (chain.getArg(1) as Boolean) {
                                NativePanelPosition.apply(native, settings)
                                NativePanelMotion.show(root)
                            }
                            else NativePanelMotion.hide(root, NativePanelLayout.collapsedPanelWidth())
                        }
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Panel morph failed", it) }
                    result
                }
            }
            view.declaredMethods.filter { it.name == "initDialog" }.forEach { method ->
                hook(method).intercept { chain ->
                    val native = chain.thisObject!!
                    val settings = PanelSettings.read(Reflect.field(native, "context") as Context)
                    onRight = if (settings.enabled) !settings.leftSide else stockRight
                    if (NativePanelLayout.activeNative === native) NativePanelLayout.restore(native)
                    else {
                        NativeActions.hide(native)
                        (Reflect.field(native, "mDialogView") as? android.view.View)?.let(NativePanelMotion::cancel)
                    }
                    val result = chain.proceed()
                    layoutSides[native] = onRight
                    NativePanelMotion.setSide(Reflect.field(native, "mDialogView") as android.view.View, !onRight)
                    NativePanelPosition.attach(native)
                    NativeVolumeLength.apply(native, settings)
                    ModuleDebugLog.i("GlassSoundbar", "Native volume layout side=${if (onRight) "right" else "left"}")
                    result
                }
            }
            view.declaredMethods.filter { it.name == "updateAppVolumeAdjust" }.forEach { method ->
                hook(method).intercept { chain ->
                    val native = chain.thisObject!!
                    val collapsed = NativePanelLayout.activeNative !== native
                    // The ROM recalculates its own landscape length here. Start from
                    // the unscaled height, then scale the newly calculated native value.
                    if (collapsed) runCatching { NativeVolumeLength.restore(native) }
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Native volume length restore failed", it) }
                    val result = chain.proceed()
                    if (collapsed) runCatching {
                        NativeVolumeLength.apply(native, PanelSettings.read(Reflect.field(native, "context") as Context))
                    }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Native volume length failed", it) }
                    result
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
                        .onFailure { ModuleDebugLog.e("GlassSoundbar", "Window touch restore failed", it) }
                    chain.proceed()
                }
            }
            view.declaredMethods.filter { it.name == "dismissH" }.forEach { method ->
                hook(method).intercept { chain ->
                    runCatching {
                        val native = chain.thisObject!!
                        val root = Reflect.field(native, "mDialogView") as android.view.View
                        if (!PanelSettings.read(root.context).enabled) return@runCatching
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
            hookLog(Log.INFO, "GlassSoundbar", "Native-only volume hooks installed; no overlay or simulated controls")
        } catch (t: Throwable) { hookLog(Log.ERROR, "GlassSoundbar", "Stock panel retained", t) }
    }
}
