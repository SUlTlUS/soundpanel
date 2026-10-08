package dev.glass.soundbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.util.WeakHashMap

/** Real SystemUI controller actions inserted into the stock collapsed side rail. */
internal object NativeActions {
    private class Action(
        val frame: ViewGroup,
        val icon: ImageView,
        var normalTint: Int,
        var currentResource: String? = null,
        var currentTint: Int = normalTint
    )

    private class Controls(
        val native: Any,
        val container: ViewGroup,
        val originalContainerHeight: Int,
        val originalContainerBackground: Drawable?,
        val originalClipChildren: Boolean,
        val originalClipToPadding: Boolean
    ) {
        val actions = mutableListOf<Action>()
        var moreFrame: ViewGroup? = null
        var buttonSurface: Drawable? = null
        var headset: EarphoneModes.State? = null
        var earphoneModes: EarphoneModes? = null
        var lastMode = -1
        var lastZen = -1
        var dismissing = false
        var visualSyncGeneration = 0
        var settings = PanelSettings()
    }

    private val controls = WeakHashMap<ViewGroup, Controls>()
    private val nativeButtons = WeakHashMap<View, Boolean>()

    fun isAddedNativeButton(view: Any?): Boolean = view is View && nativeButtons.containsKey(view)

    fun refreshButtonEffects(native: Any) {
        if (DeviceProfile.current != DeviceProfile.ONEPLUS_15) return
        val container = Reflect.field(native, "mMoreRowStreamLl") as? ViewGroup ?: return
        val item = controls[container] ?: return
        val defaultTheme = Reflect.field(native, "mIsDefaultVolumeTheme") as Boolean
        item.actions.forEach { action ->
            Reflect.call(action.frame, "refreshBlurLightEnableState", defaultTheme)
            Reflect.call(action.frame, "applySpotLightPolicy")
        }
    }

    fun setWindowTouchThrough(native: Any, enabled: Boolean) {
        val window = runCatching { Reflect.field(native, "mWindow") as? android.view.Window }.getOrNull()
            ?: return
        val flag = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (enabled) window.addFlags(flag) else window.clearFlags(flag)
        android.util.Log.i(
            "GlassSoundbar",
            "Volume window touch-through=${if (enabled) "ON" else "OFF"} flags=0x${window.attributes.flags.toString(16)}"
        )
    }

    fun showCollapsed(native: Any, settings: PanelSettings) {
        val target = Reflect.field(native, "volumeInteractor") ?: return
        if (Reflect.field(target, "mExpanded") == true) {
            hide(native)
            return
        }
        val container = Reflect.field(native, "mMoreRowStreamLl") as? ViewGroup ?: return
        val item = controls.getOrPut(container) { create(native, container) }
        item.settings = settings
        item.dismissing = false
        item.visualSyncGeneration++
        if (settings.headsetButton) item.earphoneModes?.start() else {
            item.earphoneModes?.stop()
            item.headset = null
        }
        // Do not touch mMoreRowStreamLl itself. The extra controls are real
        // siblings in its parent rail, so the stock More button keeps its own
        // ColorOS material/background/click handling intact.
        refresh(item)
        item.actions.forEachIndexed { index, action ->
            val visible = shouldShowAction(item, index)
            action.frame.animate().cancel()
            action.frame.alpha = 1f
            action.frame.scaleX = 1f
            action.frame.scaleY = 1f
            action.frame.translationX = 0f
            action.frame.translationY = 0f
            action.frame.isClickable = visible
            action.frame.isFocusable = visible
            action.frame.isEnabled = visible
            action.frame.visibility = if (visible) View.VISIBLE else View.GONE
        }
        startStockVisualSync(item)
        if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) {
            runCatching {
                val helper = Reflect.field(native, "blurHostHelper")!!
                val root = Reflect.field(native, "mDialogView") as View
                item.actions.forEach { Reflect.call(helper, "assignSideAccessoryBackground", root, it.frame) }
                refreshButtonEffects(native)
            }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Side accessory material binding failed", it) }
        }
        container.post {
            android.util.Log.i(
                "GlassSoundbar",
                "Collapsed rail: container=${container.width}x${container.height} children=${container.childCount} actions=${item.actions.size} " +
                    item.actions.joinToString { action ->
                        val rect = Rect()
                        action.frame.getGlobalVisibleRect(rect)
                        "${action.frame.width}x${action.frame.height}@${action.frame.left},${action.frame.top} global=$rect vis=${action.frame.visibility}"
                    }
            )
        }
    }

    fun hide(native: Any) {
        val container = Reflect.field(native, "mMoreRowStreamLl") as? ViewGroup ?: return
        controls[container]?.also {
            it.earphoneModes?.stop()
            it.headset = null
            it.dismissing = false
            it.visualSyncGeneration++
        }?.actions?.forEach { action ->
            action.icon.animate().cancel()
            runCatching { Reflect.call(action.icon, "cancelAnimation") }
            renderStatic(action)
            action.frame.animate().cancel()
            action.frame.alpha = 1f
            action.frame.scaleX = 1f
            action.frame.scaleY = 1f
            action.frame.translationX = 0f
            action.frame.translationY = 0f
            action.frame.visibility = View.GONE
            if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) {
                runCatching {
                    val helper = Reflect.field(native, "blurHostHelper")!!
                    Reflect.call(helper, "clearLayoutChangeListener", action.frame)
                    val manager = Class.forName("com.oplus.systemui.volume.utils.material.VolumeBlurManager", false, native.javaClass.classLoader)
                    manager.getMethod("releaseVolumePlatformAutoBlur", View::class.java).invoke(null, action.frame)
                    action.frame.background = null
                }.onFailure { ModuleDebugLog.w("GlassSoundbar", "Side accessory material release failed", it) }
            }
        }
    }

    /** Follow the ROM side-rail exit animation instead of disappearing early. */
    fun beginDismiss(native: Any) {
        val container = Reflect.field(native, "mMoreRowStreamLl") as? ViewGroup ?: return
        val item = controls[container] ?: return
        item.dismissing = true
        item.earphoneModes?.stop()
        item.visualSyncGeneration++
        val appAdjust = Reflect.field(native, "mAppVolumeAdjustFl") as? View
        val reference = appAdjust?.takeIf { it.visibility == View.VISIBLE } ?: container
        val startedAt = android.os.SystemClock.uptimeMillis()
        val loader = native.javaClass.classLoader
        val animUtil = runCatching {
            Class.forName("com.oplus.systemui.volume.utils.VolumeAnimUtil", false, loader)
        }.getOrNull()
        val hideDuration = runCatching {
            animUtil?.getField("DURATION_ROW_HIDE")?.getLong(null) ?: 500L
        }.getOrDefault(500L)
        val hideInterpolator = runCatching {
            animUtil?.getField("VOLUME_HIDE_INTERPOLATOR")?.get(null) as? android.animation.TimeInterpolator
        }.getOrNull()

        item.actions.forEachIndexed { index, action ->
            if (shouldShowAction(item, index) && action.frame.visibility == View.VISIBLE) {
                action.frame.isClickable = false
                action.frame.isFocusable = false
                action.frame.isEnabled = false
                action.frame.animate().cancel()
                action.frame.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(hideDuration)
                    .apply { if (hideInterpolator != null) setInterpolator(hideInterpolator) }
                    .withEndAction {
                        if (item.dismissing) action.frame.visibility = View.GONE
                    }
                    .start()
            }
        }

        fun mirrorFrame() {
            if (controls[container] !== item || !item.dismissing) return
            item.actions.forEachIndexed { index, action ->
                if (shouldShowAction(item, index) && action.frame.visibility == View.VISIBLE) {
                    action.frame.translationX = reference.translationX
                    action.frame.translationY = reference.translationY
                    if (reference.visibility != View.VISIBLE) action.frame.visibility = View.GONE
                }
            }
            if (android.os.SystemClock.uptimeMillis() - startedAt < hideDuration + 250L && reference.isAttachedToWindow) {
                reference.postOnAnimation { mirrorFrame() }
            }
        }

        mirrorFrame()
        forceTouchableRegionRefresh(native)
        android.util.Log.i(
            "GlassSoundbar",
            "Collapsed actions joined stock dismiss animation via ${reference.javaClass.simpleName}"
        )
    }

    fun refresh(native: Any) {
        val target = Reflect.field(native, "volumeInteractor") ?: return
        if (Reflect.field(target, "mExpanded") == true) {
            hide(native)
            return
        }
        val container = Reflect.field(native, "mMoreRowStreamLl") as? ViewGroup ?: return
        controls[container]?.let { item ->
            if (item.dismissing) return
            refresh(item)
            applyStockVisualState(item)
        }
    }

    private fun create(native: Any, container: ViewGroup): Controls {
        val context = container.context
        val result = Controls(
            native,
            container,
            container.layoutParams.height,
            container.background,
            container.clipChildren,
            container.clipToPadding
        )
        val templateIcon = Reflect.field(native, "mMoreStreamsButton") as? ImageView ?: return result
        // mMoreRowStreamLl is the More button itself. Its parent is the stock
        // vertical action rail; app_adjust_volume_fl is a sibling in that rail.
        // Insert our controls after the multi-app-volume button, never inside More.
        val rail = container.parent as? LinearLayout ?: return result
        val appAdjust = Reflect.field(native, "mAppVolumeAdjustFl") as? View
        val moreFrame = container
        val templateFrame = (appAdjust as? ViewGroup)?.takeIf { it.parent === rail } ?: moreFrame
        val anchor = appAdjust?.takeIf { it.parent === rail } ?: moreFrame
        val anchorIndex = rail.indexOfChild(anchor).takeIf { it >= 0 } ?: return result
        val moreLp = templateFrame.layoutParams
        val moreMargin = moreLp as? ViewGroup.MarginLayoutParams
        val stockButtonSurface = templateFrame.background ?: container.background ?: result.originalContainerBackground
        result.moreFrame = moreFrame
        result.buttonSurface = cloneDrawable(stockButtonSurface, context)
        // New material follows the stock tint; legacy material keeps its color.
        val stockIconTint = if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) nativeIconTint(templateIcon) else Color.rgb(76, 76, 76)
        val iconSize = (24f * context.resources.displayMetrics.density + 0.5f).toInt()

        repeat(3) { index ->
            val frame = createButtonFrame(context, native).apply {
                clipChildren = false
                clipToPadding = false
                layoutDirection = templateFrame.layoutDirection
                elevation = templateFrame.elevation
                outlineSpotShadowColor = templateFrame.outlineSpotShadowColor
                background = if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) null else cloneDrawable(stockButtonSurface, context)
                visibility = View.GONE
                isClickable = true
                isFocusable = true
            }
            val frameParams = LinearLayout.LayoutParams(moreLp.width, moreLp.height).apply {
                gravity = (moreLp as? LinearLayout.LayoutParams)?.gravity ?: Gravity.END
                marginStart = moreMargin?.marginStart ?: 0
                marginEnd = moreMargin?.marginEnd ?: 0
                topMargin = moreMargin?.topMargin ?: 0
                bottomMargin = moreMargin?.bottomMargin ?: 0
            }

            val icon = createQsIconView(context, native.javaClass.classLoader).apply {
                background = null
                scaleType = ImageView.ScaleType.FIT_CENTER
                imageAlpha = 255
                isClickable = false
                isFocusable = false
            }
            frame.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            result.actions += Action(frame, icon, stockIconTint)
            if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) nativeButtons[frame] = true
            rail.addView(frame, (anchorIndex + 1 + index).coerceAtMost(rail.childCount), frameParams)

            frame.setOnClickListener {
                runCatching {
                    val target = Reflect.field(native, "volumeInteractor")!!
                    val controller = Reflect.field(target, "mController")!!
                    val state = Reflect.field(target, "mState")!!
                    when (index) {
                        0 -> {
                            val currentMode = Reflect.field(state, "ringerModeInternal") as Int
                            val nextMode = nextRingerMode(currentMode)
                            Reflect.call(controller, "setRingerMode", nextMode, false)
                            runCatching {
                                Reflect.field(target, "mOplusRMChangeVibrateHelper")?.let { helper ->
                                    Reflect.call(helper, "vibrateOnRMChanged", nextMode, "GlassSoundbar")
                                }
                            }
                            // Use the same transition order as ColorOS ThreeStageRingerModeTile:
                            // normal -> vibrate -> silent -> normal.
                            updateRinger(result, nextMode)
                        }
                        1 -> {
                            val nextZen = if (Reflect.field(state, "zenMode") == 0) 1 else 0
                            Reflect.call(controller, "setZenMode", nextZen)
                            updateZen(result, nextZen)
                        }
                        2 -> result.earphoneModes?.cycle(result.headset ?: return@setOnClickListener)
                    }
                    Reflect.call(target, "rescheduleTimeoutH")
                    Reflect.call(controller, "getState")
                }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Native action failed", it) }
            }
        }
        result.earphoneModes = EarphoneModes(context) { state ->
            if (result.dismissing) return@EarphoneModes
            val previous = result.headset
            result.headset = state
            if (state != null && state != previous) {
                val action = result.actions[2]
                update(action, state.title, state.icon, false, native)
                action.icon.contentDescription = if (state.pending) "${state.title}，切换中" else
                    "${state.title}，点按切换为${EarphoneModes.title(state.next)}"
                action.frame.contentDescription = action.icon.contentDescription
            }
            if (state != previous) {
                applyStockVisualState(result)
                forceTouchableRegionRefresh(native)
            }
        }
        ModuleDebugLog.i(
            "GlassSoundbar",
            "Collapsed actions mounted: profile=${DeviceProfile.current} container=${container.javaClass.name} rail=${rail.javaClass.name} count=${result.actions.size} buttons=${result.actions.joinToString { it.frame.javaClass.simpleName }}"
        )
        return result
    }

    private fun stockReference(item: Controls): View {
        val appAdjust = Reflect.field(item.native, "mAppVolumeAdjustFl") as? View
        return appAdjust?.takeIf { it.visibility == View.VISIBLE } ?: item.container
    }

    private fun applyStockVisualState(item: Controls) {
        if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) {
            val tint = nativeIconTint(Reflect.field(item.native, "mMoreStreamsButton") as? ImageView)
            item.actions.forEach { action ->
                if (action.normalTint != tint) {
                    action.normalTint = tint
                    action.currentResource?.let { resource ->
                        val targetTint = actionTint(action, action.icon.isSelected)
                        if (action.currentTint != targetTint) setStaticIcon(action, resource, targetTint)
                    }
                }
            }
        }
        val reference = stockReference(item)
        val stockVisible = reference.visibility == View.VISIBLE && reference.alpha > 0.01f
        var touchStateChanged = false
        item.actions.forEachIndexed { index, action ->
            val shouldExist = shouldShowAction(item, index)
            if (!shouldExist) {
                action.frame.visibility = View.GONE
                action.frame.isClickable = false
                action.frame.isFocusable = false
                action.frame.isEnabled = false
                return@forEachIndexed
            }
            action.frame.visibility = if (stockVisible) View.VISIBLE else View.INVISIBLE
            action.frame.alpha = reference.alpha
            action.frame.scaleX = reference.scaleX
            action.frame.scaleY = reference.scaleY
            action.frame.translationX = reference.translationX
            action.frame.translationY = reference.translationY
            // Release the injected hit area as soon as the stock control starts
            // leaving its fully-visible state. Waiting until alpha is almost gone
            // keeps the old touchable region alive through most of the retract anim.
            val interactive = stockVisible && reference.alpha >= 0.999f && !item.dismissing
            if (action.frame.isEnabled != interactive) touchStateChanged = true
            action.frame.isClickable = interactive
            action.frame.isFocusable = interactive
            action.frame.isEnabled = interactive
        }
        if (touchStateChanged) forceTouchableRegionRefresh(item.native)
    }

    private fun startStockVisualSync(item: Controls) {
        val generation = item.visualSyncGeneration
        fun frame() {
            if (item.visualSyncGeneration != generation || item.dismissing) return
            val target = runCatching { Reflect.field(item.native, "volumeInteractor") }.getOrNull() ?: return
            if (runCatching { Reflect.field(target, "mExpanded") == true }.getOrDefault(false)) return
            applyStockVisualState(item)
            if (item.container.isAttachedToWindow) {
                item.container.postOnAnimation { frame() }
            }
        }
        item.container.postOnAnimation { frame() }
    }

    private fun forceTouchableRegionRefresh(native: Any) {
        val root = runCatching { Reflect.field(native, "mDialogView") as? View }.getOrNull() ?: return
        root.requestApplyInsets()
        root.requestLayout()
        root.invalidate()
        (root.rootView as? View)?.let { decor ->
            decor.requestApplyInsets()
            decor.requestLayout()
            decor.invalidate()
        }
        root.postOnAnimation {
            root.requestApplyInsets()
            root.requestLayout()
        }
    }

    private fun shouldShowAction(item: Controls, index: Int): Boolean {
        val enabled = when (index) {
            0 -> item.settings.ringerButton
            1 -> item.settings.dndButton
            else -> item.settings.headsetButton
        }
        if (!enabled) return false
        val landscape = item.container.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (landscape) {
            index == 2 && item.headset != null
        } else {
            index != 2 || item.headset != null
        }
    }

    private fun refresh(item: Controls) {
        val target = Reflect.field(item.native, "volumeInteractor")!!
        val state = Reflect.field(target, "mState") ?: return
        val mode = Reflect.field(state, "ringerModeInternal") as Int
        val zen = Reflect.field(state, "zenMode") as Int
        if (mode != item.lastMode) updateRinger(item, mode)
        if (zen != item.lastZen) updateZen(item, zen)
    }

    private fun updateRinger(item: Controls, mode: Int) {
        val titles = listOf("静音", "振动", "响铃")
        val icons = listOf(
            "status_bar_qs_mute_active",
            "status_bar_qs_vibrate_on",
            "status_bar_qs_mute_inactive"
        )
        val safeMode = mode.coerceIn(0, 2)
        val action = item.actions[0]
        val previousMode = item.lastMode
        val title = titles[safeMode]
        val resource = icons[safeMode]
        val tint = action.normalTint
        val asset = when (safeMode) {
            0 -> "qs/qs_three_stage_vibrate_to_silent_lottie_active_sep_dual_light.json"
            1 -> "qs/qs_three_stage_normal_to_vibrate_lottie_active_sep_dual_light.json"
            else -> "qs/qs_three_stage_silent_to_normal_lottie_active_sep_dual_light.json"
        }
        val animated = previousMode in 0..2 && previousMode != safeMode && action.frame.isShown &&
            playQsLottie(action, asset, tint, tint, resource)
        if (!animated) setStaticIcon(action, resource, tint)
        action.icon.contentDescription = "$title，点按切换为${titles[nextRingerMode(safeMode)]}"
        action.frame.contentDescription = title
        action.icon.isSelected = false
        action.frame.isSelected = false
        item.lastMode = safeMode
    }

    private fun updateZen(item: Controls, zen: Int) {
        val action = item.actions[1]
        val selected = zen != 0
        val title = if (selected) "勿扰已开启" else "勿扰"
        val resource = if (selected) "status_bar_qs_dnd_active" else "status_bar_qs_dnd_inactive"
        val tint = actionTint(action, selected)
        val previousSelected = item.lastZen > 0
        val asset = if (selected) {
            "qs/qs_dnd_lottie_active_sep_dual_light.json"
        } else {
            "qs/qs_dnd_lottie_inactive_sep_dual_light.json"
        }
        val startTint = actionTint(action, previousSelected)
        val animated = item.lastZen >= 0 && previousSelected != selected && action.frame.isShown &&
            playQsLottie(action, asset, startTint, tint, resource)
        if (!animated) setStaticIcon(action, resource, tint)
        action.icon.contentDescription = title
        action.frame.contentDescription = title
        action.icon.isSelected = selected
        action.frame.isSelected = selected
        item.lastZen = zen
    }

    private fun update(action: Action, title: String, resource: String, selected: Boolean, native: Any) {
        val tint = actionTint(action, selected)
        setStaticIcon(action, resource, tint)
        action.icon.contentDescription = title
        action.frame.contentDescription = title
        action.icon.isSelected = selected
        action.frame.isSelected = selected
    }

    private fun actionTint(action: Action, selected: Boolean): Int =
        if (selected) Color.rgb(80, 160, 255) else action.normalTint

    private fun nativeIconTint(icon: ImageView?): Int {
        // The stock more_row_stream_system vector uses #4D4D4D when the ROM
        // clears its platform-blur tint; otherwise follow its live tint/state.
        val tint = icon?.imageTintList
        return tint?.getColorForState(icon.drawableState, tint.defaultColor) ?: Color.rgb(77, 77, 77)
    }

    private fun nextRingerMode(mode: Int): Int = when (mode) {
        0 -> 2
        1 -> 0
        else -> 1
    }

    private fun createButtonFrame(context: Context, native: Any): FrameLayout {
        if (DeviceProfile.current != DeviceProfile.ONEPLUS_15) return FrameLayout(context)
        val type = Class.forName(
            "com.oplus.systemui.volume.view.OplusVolumeSideAccessoryButton", false, native.javaClass.classLoader
        )
        return (type.getConstructor(Context::class.java).newInstance(context) as FrameLayout).also {
            Reflect.set(it, "isDefaultVolumeTheme", Reflect.field(native, "mIsDefaultVolumeTheme"))
        }
    }

    private fun createQsIconView(context: Context, hostClassLoader: ClassLoader?): ImageView = runCatching {
        val loader = hostClassLoader ?: context.javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        val type = Class.forName(
            "com.oplus.systemui.qs.base.tile.QSLottieAnimationView",
            false,
            loader
        )
        (type.getConstructor(Context::class.java).newInstance(context) as ImageView).also { icon ->
            android.util.Log.i("GlassSoundbar", "Using native QS icon view: ${icon.javaClass.name}")
            runCatching {
                val renderMode = Class.forName("com.airbnb.lottie.RenderMode", false, loader)
                val software = renderMode.getField("SOFTWARE").get(null)
                Reflect.call(icon, "setRenderMode", software)
            }
        }
    }.getOrElse {
        ModuleDebugLog.w("GlassSoundbar", "QS Lottie icon unavailable; using ImageView", it)
        ImageView(context)
    }

    private fun setStaticIcon(action: Action, resource: String, tint: Int) {
        val context = action.icon.context
        val id = context.resources.getIdentifier(resource, "drawable", "com.android.systemui")
        if (id == 0) {
            ModuleDebugLog.w("GlassSoundbar", "Missing SystemUI drawable: $resource")
            return
        }
        runCatching { Reflect.call(action.icon, "cancelAnimation") }
        action.icon.setImageResource(id)
        action.icon.imageTintList = ColorStateList.valueOf(tint)
        action.icon.imageAlpha = 255
        action.icon.alpha = 1f
        action.icon.scaleX = 1f
        action.icon.scaleY = 1f
        action.icon.visibility = View.VISIBLE
        action.icon.drawable?.alpha = 255
        action.currentResource = resource
        action.currentTint = tint
    }

    private fun renderStatic(action: Action) {
        action.currentResource?.let { setStaticIcon(action, it, action.currentTint) }
    }

    private fun playQsLottie(
        action: Action,
        asset: String,
        startTint: Int,
        endTint: Int,
        finalResource: String
    ): Boolean = runCatching {
        if (action.icon.javaClass.name != "com.oplus.systemui.qs.base.tile.QSLottieAnimationView") {
            return@runCatching false
        }
        val loader = action.icon.javaClass.classLoader
        val configType = Class.forName(
            "com.oplus.systemui.qs.base.tile.QSLottieAnimationView\$ColorfulConfig",
            false,
            loader
        )
        val config = configType
            .getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .newInstance(1f, 1f, startTint, endTint)
        action.icon.animate().cancel()
        Reflect.call(action.icon, "cancelAnimation")
        action.icon.imageTintList = null
        action.icon.clearColorFilter()
        action.icon.alpha = 1f
        action.icon.scaleX = 1f
        action.icon.scaleY = 1f
        Reflect.call(action.icon, "setAnimation", asset)
        Reflect.call(action.icon, "setTintColorfulConfig", asset, config)
        Reflect.call(action.icon, "setRepeatCount", 0)
        Reflect.call(action.icon, "setProgress", 0f)
        Reflect.call(action.icon, "playAnimation")
        action.currentResource = finalResource
        action.currentTint = endTint
        android.util.Log.i("GlassSoundbar", "Playing native QS Lottie: $asset")
        true
    }.onFailure {
        ModuleDebugLog.e("GlassSoundbar", "Native QS Lottie failed: $asset", it)
    }.getOrDefault(false)

    fun extendTouchableRegion(container: View, internalInsetsInfo: Any) {
        val item = controls[container] ?: return
        if (item.dismissing) return
        val region = Reflect.field(internalInsetsInfo, "touchableRegion") as? Region ?: return
        val rect = Rect()
        var added = 0
        item.actions.forEach { action ->
            if (action.frame.visibility == View.VISIBLE && action.frame.isEnabled && action.frame.alpha > 0.01f && action.frame.getGlobalVisibleRect(rect)) {
                region.op(rect, Region.Op.UNION)
                added++
            }
        }
        if (added > 0) {
            android.util.Log.i("GlassSoundbar", "Touchable region extended for $added collapsed actions")
        }
    }

    fun isActionHit(view: View?, event: MotionEvent): Boolean {
        val container = view as? ViewGroup ?: return false
        val item = controls[container] ?: return false
        if (item.dismissing) return false
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        val rect = Rect()
        return item.actions.any { action ->
            action.frame.visibility == View.VISIBLE && action.frame.isEnabled && action.frame.alpha > 0.01f &&
                action.frame.getGlobalVisibleRect(rect) &&
                rect.contains(x, y)
        }
    }

    private fun cloneDrawable(source: Drawable?, context: Context): Drawable? =
        source?.constantState?.newDrawable(context.resources)?.mutate() ?: source?.mutate()

}
