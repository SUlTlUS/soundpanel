package dev.glass.soundbar

import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.util.WeakHashMap
import java.util.IdentityHashMap

/** Geometry only. Never replace a ROM Drawable, seekbar or touch listener. */
internal object NativePanelLayout {
    private var activePaddingPx = 0f
    private var activeSliderWidthPx = 0f

    fun targetRowTranslationPx(position: Int, rowWidth: Int, naturalRowLeft: Int): Float? {
        if (activeRoot == null || activePaddingPx <= 0f || activeSliderWidthPx <= 0f || rowWidth <= 0) return null
        val sideInset = (rowWidth - activeSliderWidthPx).coerceAtLeast(0f) / 2f
        val desiredRowLeft = activePaddingPx - sideInset + position * (activeSliderWidthPx + activePaddingPx)
        return desiredRowLeft - naturalRowLeft.toFloat()
    }
    private val originals = WeakHashMap<View, ViewGroup.LayoutParams>()
    private val biases = WeakHashMap<View, Float>()
    private val collapsedCapsules = WeakHashMap<View, Rect>()
    private val collapsedRootBounds = WeakHashMap<View, Rect>()
    private data class CornerState(val original: List<Float>, var radius: Float)
    private val radii = IdentityHashMap<Any, CornerState>()
    private val paddingScales = WeakHashMap<View, Float>()
    private val toneAmounts = WeakHashMap<View, Float>()
    private val radiusScales = WeakHashMap<View, Float>()
    private data class LabelState(val height: Int, val visibility: Int, val minimumHeight: Int)
    private val labelStates = WeakHashMap<TextView, LabelState>()
    private val recyclerHeights = WeakHashMap<View, Int>()
    private val hiddenLabels = WeakHashMap<View, Boolean>()
    private var labelExtentPx = 0
    var activeNative: Any? = null
        private set
    private val corners = listOf("LeftTopCornerRadius", "RightTopCornerRadius", "LeftBottomCornerRadius", "RightBottomCornerRadius")
    var activeRoot: ViewGroup? = null
        private set

    fun restoreMaterialAfterNativeRefresh(config: Any) {
        if (activeRoot == null) return
        val shared = NativeVolumeSurface.restoreAfterNativeRefresh(config)
        radii[config]?.let { state -> corners.forEach { Reflect.call(config, "set$it", state.radius) } }
        if (NativeMaterial.restoreAfterNativeRefresh(config) && !shared) {
            NativePanelTone.apply(config, NativeSurfacePreset.panelAdjustment(toneAmounts[activeRoot] ?: 0f))
        }
    }

    fun apply(native: Any, settings: PanelSettings) {
        val root = Reflect.field(native, "mDialogView") as ViewGroup
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as View
        if (!settings.enabled) {
            restore(native)
            return
        }
        val tone = settings.toneFor(root.context)
        captureCollapsedCapsule(native, root, tone)
        NativeActions.hide(native)
        val original = root.layoutParams
        if (original !is FrameLayout.LayoutParams) return
        originals.putIfAbsent(root, FrameLayout.LayoutParams(original))
        // Do not move the dialog yet. Some OnePlus SystemUI builds differ in
        // private fields/material wiring; if a later probe fails, changing the
        // gravity first would strand the stock panel at (top, right). Apply the
        // final geometry atomically only after every required measurement is valid.
        paddingScales[root] = settings.size
        toneAmounts[root] = tone
        radiusScales[root] = settings.radiusScale
        hiddenLabels[root] = settings.hideLabels
        ModuleDebugLog.i("GlassSoundbar", "Panel settings read: tone=$tone uiMode=${root.resources.configuration.uiMode} lightTone=${settings.tone} darkTone=${settings.darkTone} radiusScale=${settings.radiusScale} profile=${DeviceProfile.current}")
        activeRoot = root
        activeNative = native
        NativePanelMotion.prepareExpand(root)
        biases.putIfAbsent(recycler, Reflect.field(recycler.layoutParams, "verticalBias") as Float)
        Reflect.set(recycler.layoutParams, "verticalBias", 0f)
        recycler.requestLayout()
        alignCorners(native, root, beforeSelection = true)
    }

    fun restore(native: Any) {
        val root = Reflect.field(native, "mDialogView") as? ViewGroup ?: return
        NativePanelMotion.cancel(root)
        originals.remove(root)?.let { root.layoutParams = it }
        paddingScales.remove(root)
        toneAmounts.remove(root)
        radiusScales.remove(root)
        NativeVolumeSurface.restore()
        hiddenLabels.remove(root)
        labelStates.forEach { (label, state) ->
            label.layoutParams = label.layoutParams.apply { height = state.height }
            label.minimumHeight = state.minimumHeight
            label.visibility = state.visibility
        }
        labelStates.clear()
        labelExtentPx = 0
        collapsedCapsules.remove(root)
        collapsedRootBounds.remove(root)
        NativeActions.hide(native)
        NativeMaterial.detachEdgeStroke(root)
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as? View
        if (recycler != null) {
            recyclerHeights.remove(recycler)?.let { height ->
                recycler.layoutParams = recycler.layoutParams.apply { this.height = height }
            }
            biases.remove(recycler)?.let { Reflect.set(recycler.layoutParams, "verticalBias", it) }
            recycler.translationY = 0f
            recycler.requestLayout()
        }
        runCatching {
            material(native)?.let { proxy ->
                val config = Reflect.call(proxy, "getBlurConfig")!!
                NativePanelTone.restore(config)
                runCatching { NativeMaterial.restorePlatformCorners(config) }.onFailure {
                    ModuleDebugLog.w("GlassSoundbar", "Panel material restore failed", it)
                }
                radii.remove(config)?.let { values ->
                    corners.forEachIndexed { i, corner -> Reflect.call(config, "set$corner", values.original[i]) }
                    Reflect.call(proxy, "applyBlurConfig")
                    NativeMaterial.syncPlatformStroke(proxy)
                }
            }
        }.onFailure {
            ModuleDebugLog.w("GlassSoundbar", "Material restore skipped on this ROM", it)
        }
        activeRoot = null
        activeNative = null
        activePaddingPx = 0f
        activeSliderWidthPx = 0f
    }

    private fun material(native: Any): Any? {
        val profile = DeviceProfile.current ?: return null
        val owner = profile.backgroundOwner?.let { Reflect.field(native, it) } ?: native
        val drawable = Reflect.field(owner, profile.backgroundField) ?: return null
        // Low-Gaussian/power-saving mode uses a solid drawable on both ROMs.
        if (drawable.javaClass.simpleName != "AutoBlurDrawable") return null
        return Reflect.call(drawable, "getViewBlurProxy")
    }

    private fun alignCorners(native: Any, root: ViewGroup, beforeSelection: Boolean = false) {
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as ViewGroup
        val adapter = Reflect.field(native, "volumeListAdapter")!!
        val rows = if (beforeSelection) {
            (Reflect.call(adapter, "getVolumeRowList") as List<*>).filterNotNull().filter {
                if (Reflect.field(adapter, "isSystemPanel") == true) {
                    Reflect.field(it, "isShowInPanel") == true && Reflect.field(it, "isMultiMediaVolume") != true
                } else {
                    (Reflect.field(it, "isMultiMediaVolume") == true || Reflect.call(it, "getStream") == 3) &&
                        (Reflect.call(it, "getView") as? View)?.visibility == View.VISIBLE
                }
            }.take(4)
        } else Reflect.call(adapter, "getPanelShowList") as List<*>
        val first = rows.firstOrNull() ?: return
        val row = Reflect.call(first, "getView") as View
        val slider = Reflect.call(first, "getSlider") as View
        rows.filterNotNull().forEach { NativeVolumeSurface.bindSlider(Reflect.call(it, "getSlider") as View) }
        // Match the ROM animator's settled spacing using this device's own
        // layout/resources. If SystemUI has not produced reliable dimensions
        // yet, leave the stock layout untouched instead of applying a guessed
        // 56dp/12dp geometry that can break on another OnePlus model.
        val rowWidth = row.layoutParams.width.takeIf { it > 0 }
            ?: row.measuredWidth.takeIf { it > 0 }
            ?: return
        val sliderWidth = slider.layoutParams.width.takeIf { it > 0 }
            ?: slider.measuredWidth.takeIf { it > 0 }
            ?: return
        val romPadding = dimenPx(root, "volume_dialog_recycleview_padding_left")
            ?: recycler.paddingLeft.toFloat().takeIf { it > 0f }
            ?: ((rowWidth - sliderWidth).coerceAtLeast(0) / 2f).takeIf { it > 0f }
            ?: return
        val padding = romPadding * (paddingScales[root] ?: 1f)
        val rowSideInset = (rowWidth - sliderWidth).coerceAtLeast(0).toFloat()
        // Visible outer padding and visible inter-slider gap use the same ROM
        // spacing value, but no density- or model-specific constant.
        val targetSliderGap = padding
        val rowGap = targetSliderGap - rowSideInset
        val contentWidth = rows.size * rowWidth + (rows.size - 1) * rowGap
        val panelWidth = (contentWidth - (rowWidth - sliderWidth) + 2f * padding).toInt()
        val inset = ((panelWidth - contentWidth) / 2f + (rowWidth - sliderWidth) / 2f).coerceAtLeast(0f)
        val sliderFrame = Reflect.call(first, "getSliderFrame") as ViewGroup
        val sliderTopInset = sliderFrame.paddingTop
        val sliderBottomInset = sliderFrame.paddingBottom
        val recyclerTranslationY = inset - sliderTopInset
        val rowsHeight = recycler.layoutParams.height.takeIf { it > 0 }
            ?: recycler.measuredHeight.takeIf { it > 0 }
            ?: dimenPx(root, "volume_vertical_recyclerview_height")?.toInt()
            ?: return
        val visibleSliderHeight = (rowsHeight - labelExtentPx - sliderTopInset - sliderBottomInset).coerceAtLeast(0)
        val panelHeight = if (hiddenLabels[root] == true) {
            PanelContentGeometry.symmetricHeight(visibleSliderHeight, inset)
        } else (recyclerTranslationY + rowsHeight + inset).toInt()
        val expandedSliderCenterLocal = inset + visibleSliderHeight / 2f

        // All required geometry must exist before touching the dialog layout.
        // This prevents a ROM-specific reflection/material failure from leaving
        // the panel stranded at TOP|RIGHT with zero margins.
        val anchor = collapsedCapsules[root] ?: return
        val parent = root.parent as? View ?: return
        if (parent.width <= 0) return
        val currentLp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        val parentLocation = IntArray(2)
        parent.getLocationOnScreen(parentLocation)
        val parentTop = parentLocation[1]
        val parentLeft = parentLocation[0]
        val desiredRight = (anchor.right - parentLeft + padding).toInt()
        val finalLp = FrameLayout.LayoutParams(currentLp).apply {
            width = panelWidth
            height = panelHeight
            gravity = Gravity.TOP or Gravity.RIGHT
            topMargin = (anchor.exactCenterY() - parentTop - expandedSliderCenterLocal).toInt().coerceAtLeast(0)
            rightMargin = (parent.width - desiredRight).coerceAtLeast(0)
        }

        activePaddingPx = padding
        activeSliderWidthPx = sliderWidth.toFloat()
        recycler.translationY = recyclerTranslationY
        root.layoutParams = finalLp
        val radiusId = row.resources.getIdentifier("volume_vertical_row_radius_os17", "dimen", "com.android.systemui")
        val sliderRadius = if (radiusId != 0) row.resources.getDimension(radiusId) else sliderWidth / 2f
        val radius = ((inset + sliderRadius) * (radiusScales[root] ?: 1f))
            .coerceAtMost(minOf(panelWidth, panelHeight) / 2f)

        // Geometry diagnostics must survive even when a vendor moves or replaces
        // the private blur implementation (OnePlus 15 does this).
        ModuleDebugLog.i(
            "GlassSoundbar",
            "Geometry before first frame: radius=$radius inset=$inset internalSliderGap=$targetSliderGap padding=$padding rowGap=$rowGap rows=${rows.size} panel=${panelWidth}x$panelHeight rowsHeight=$rowsHeight anchor=$anchor collapsedRoot=${collapsedRootBounds[root]} romPadding=$romPadding"
        )
        logGeometrySnapshot(native, root, "before-first-frame", rows)

        runCatching {
            val proxy = material(native)
            if (proxy == null) {
                ModuleDebugLog.w("GlassSoundbar", "Panel tone unavailable: native background has no blur proxy")
            } else {
                val config = Reflect.call(proxy, "getBlurConfig")!!
                radii.getOrPut(config) { CornerState(corners.map { Reflect.call(config, "get$it") as Float }, radius) }
                    .radius = radius
                runCatching { NativeMaterial.apply(proxy, root, radius) }.onFailure {
                    ModuleDebugLog.w("GlassSoundbar", "Panel stroke setup failed; applying tone independently", it)
                }
                val amount = toneAmounts[root] ?: 0f
                runCatching {
                    if (!NativeVolumeSurface.bindPanel(proxy)) NativePanelTone.apply(config, NativeSurfacePreset.panelAdjustment(amount))
                }.onFailure {
                    ModuleDebugLog.e("GlassSoundbar", "Panel tone failed: amount=$amount", it)
                }
                corners.forEach { Reflect.call(config, "set$it", radius) }
                Reflect.call(proxy, "applyBlurConfig")
                ModuleDebugLog.i(
                    "GlassSoundbar",
                    "Panel tone config: amount=$amount type=${Reflect.call(proxy, "getBlurType")} " +
                        "platform=${Reflect.call(config, "getPlatformMixConfig")} motion=${Reflect.call(config, "getMotionBlurMixConfig")}",
                )
                runCatching { NativeMaterial.syncPlatformStroke(proxy) }.onFailure {
                    ModuleDebugLog.w("GlassSoundbar", "Panel stroke cache sync failed", it)
                }
            }
        }.onFailure {
            ModuleDebugLog.w("GlassSoundbar", "Custom panel radius/material skipped on this ROM", it)
        }
        root.invalidate()
    }

    private fun captureCollapsedCapsule(native: Any, root: View, tone: Float) {
        runCatching {
            val rootRect = Rect()
            if (root.getGlobalVisibleRect(rootRect) && !rootRect.isEmpty) {
                collapsedRootBounds[root] = Rect(rootRect)
            }
            val interactor = Reflect.field(native, "volumeInteractor") ?: return@runCatching
            val activeRow = Reflect.call(interactor, "getActiveRow") ?: return@runCatching
            val slider = Reflect.call(activeRow, "getSlider") as? View ?: return@runCatching
            NativeVolumeSurface.capture(slider, tone, material(native))
            val rect = visualCapsuleOnScreen(slider) ?: return@runCatching
            collapsedCapsules[root] = Rect(rect)
            ModuleDebugLog.i(
                "GlassSoundbar",
                "Collapsed geometry: capsule=$rect centerY=${rect.exactCenterY()} root=${collapsedRootBounds[root]} ${deviceSummary(root)}"
            )
            logView("collapsed-root", root)
            logView("collapsed-slider", slider)
        }.onFailure {
            ModuleDebugLog.w("GlassSoundbar", "Unable to capture collapsed capsule", it)
        }
    }

    private fun visualCapsuleOnScreen(slider: View): Rect? = runCatching {
        Reflect.call(slider, "preCalcMaterialClipPath")
        val local = Rect()
        val host = Reflect.field(slider, "mMaterialHost")
        if (host != null) {
            val profile = DeviceProfile.current ?: return@runCatching null
            Reflect.call(host, profile.visualBoundsMethod, local)
        }
        if (local.isEmpty) Reflect.call(slider, "copyBackgroundRect", local)
        if (local.isEmpty && slider.width > 0 && slider.height > 0) local.set(0, 0, slider.width, slider.height)
        if (local.isEmpty) return@runCatching null
        val location = IntArray(2)
        slider.getLocationOnScreen(location)
        local.offset(location[0], location[1])
        local
    }.onFailure {
        ModuleDebugLog.w("GlassSoundbar", "Cannot resolve slider bounds for ${DeviceProfile.current}", it)
    }.getOrNull()

    private fun syncExpandedCapsule(native: Any, root: ViewGroup): Boolean {
        val anchor = collapsedCapsules[root] ?: return false
        val adapter = Reflect.field(native, "volumeListAdapter") ?: return false
        val rows = (Reflect.call(adapter, "getPanelShowList") as? List<*>)?.filterNotNull() ?: return false
        val first = rows.firstOrNull() ?: return false
        val slider = Reflect.call(first, "getSlider") as? View ?: return false
        val capsule = visualCapsuleOnScreen(slider) ?: return false
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as? View ?: return false
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        val padding = activePaddingPx.takeIf { it > 0f } ?: return false
        val currentRootTop = rootLocation[1]
        val lp = root.layoutParams as? FrameLayout.LayoutParams ?: return false
        val parent = root.parent as? View ?: return false
        val parentLocation = IntArray(2)
        parent.getLocationOnScreen(parentLocation)
        val desiredTopMargin = PanelContentGeometry.topMargin(anchor.top, padding, parentLocation[1])
        val rootDelta = desiredTopMargin - lp.topMargin
        // Calibrate settled layout, not an intermediate row pose. Landscape
        // moves can animate vertically while pre-draw calibration is running.
        var rowTranslationY = 0f
        var child: View? = slider
        while (child != null && child !== root && child !== recycler) {
            rowTranslationY += child.translationY
            child = child.parent as? View
        }
        val capsuleLocalTop = capsule.top - currentRootTop - rowTranslationY
        val recyclerDelta = padding - capsuleLocalTop
        // The native material's visible capsule can differ from its View/frame
        // bounds. Hidden labels leave no content below it, so size the exterior
        // from the actual drawn capsule and equal padding on both ends.
        val desiredHeight = if (hiddenLabels[root] == true) {
            PanelContentGeometry.symmetricHeight(capsule.height(), padding)
        } else lp.height
        val heightDelta = desiredHeight - lp.height
        val changed = kotlin.math.abs(rootDelta) > 1 || kotlin.math.abs(recyclerDelta) > 1f || heightDelta != 0
        if (!changed) return false

        if (kotlin.math.abs(recyclerDelta) > 1f) recycler.translationY += recyclerDelta
        if (kotlin.math.abs(rootDelta) > 1 || heightDelta != 0) {
            lp.topMargin = desiredTopMargin
            lp.height = desiredHeight
            root.layoutParams = lp
        }
        ModuleDebugLog.i(
            "GlassSoundbar",
            "Pre-draw capsule sync: collapsed=$anchor expanded=$capsule padding=$padding panelHeight=$desiredHeight top=${lp.topMargin} right=${lp.rightMargin} rootDelta=$rootDelta recyclerDelta=$recyclerDelta heightDelta=$heightDelta recyclerY=${recycler.translationY}"
        )
        return true
    }

    fun collapsedPanelWidth(): Float? = activeSliderWidthPx.takeIf { it > 0f }?.let { it + activePaddingPx * 2f }

    fun detachMaterialEdge(native: Any) {
        val root = Reflect.field(native, "mDialogView") as? View ?: return
        NativeMaterial.detachEdgeStroke(root)
    }

    fun hideTitle(native: Any) {
        if (activeRoot !== Reflect.field(native, "mDialogView")) return
        listOf("mVolumePanelTitleIcon", "mVolumePanelTitleText", "mSettingsButton", "mVolumeSettingText").forEach {
            (Reflect.field(native, it) as? View)?.visibility = View.GONE
        }
        val root = activeRoot ?: return
        normalizeRows()
        alignCorners(native, root)
        // Resolve the final vertical geometry before the first visible frame.
        // If calibration changes layout, cancel that pre-draw and let Android
        // run layout again; this removes the visible one-frame upward jump.
        val observer = root.viewTreeObserver
        observer.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            var passes = 0
            override fun onPreDraw(): Boolean {
                if (!root.viewTreeObserver.isAlive) return true
                val changed = syncExpandedCapsule(native, root)
                passes++
                if (changed && passes < 4) return false
                root.viewTreeObserver.removeOnPreDrawListener(this)
                if (activeRoot === root && Reflect.call(native, "isDismissing") != true) {
                    collapsedPanelWidth()?.let { NativePanelMotion.expand(root, it) }
                }
                return true
            }
        })
    }

    fun normalizeRows(boundItem: View? = null) {
        val native = activeNative ?: return
        val root = activeRoot ?: return
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as ViewGroup
        val adapter = Reflect.field(native, "volumeListAdapter") ?: return
        val rows = (Reflect.call(adapter, "getPanelShowList") as? List<*>)?.filterNotNull() ?: return
        rows.forEach { NativeVolumeSurface.bindSlider(Reflect.call(it, "getSlider") as View) }
        val items = (0 until recycler.childCount).map(recycler::getChildAt) + listOfNotNull(boundItem)
        val labelId = root.resources.getIdentifier("volume_text", "id", "com.android.systemui")
        val labels = items.mapNotNull { it.findViewById<TextView>(labelId) }.distinct()
        if (labels.isNotEmpty()) {
            val originalHeight = recyclerHeights.getOrPut(recycler) { recycler.layoutParams.height }
            var originalExtent = 0
            var compactExtent = 0
            labels.forEach { label ->
                val state = labelStates.getOrPut(label) {
                    LabelState(label.layoutParams.height, label.visibility, label.minimumHeight)
                }
                val margins = label.layoutParams as? ViewGroup.MarginLayoutParams
                val margin = (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
                originalExtent = maxOf(originalExtent, state.height.coerceAtLeast(0) + margin)
                val hidden = hiddenLabels[root] == true
                val visibility = if (hidden) View.GONE else View.VISIBLE
                if (label.visibility != visibility) label.visibility = visibility
                if (label.minimumHeight != 0) label.minimumHeight = 0
                if (label.layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    label.layoutParams = label.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
                }
                if (!hidden) {
                    val width = label.layoutParams.width.takeIf { it > 0 } ?: label.width
                    label.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    compactExtent = maxOf(compactExtent, label.measuredHeight + margin)
                }
            }
            labelExtentPx = compactExtent
            if (originalHeight > 0 && originalExtent > 0) {
                val height = PanelContentGeometry.compactHeight(originalHeight, originalExtent, compactExtent)
                val delta = height - recycler.layoutParams.height
                if (delta != 0) {
                    recycler.layoutParams = recycler.layoutParams.apply { this.height = height }
                    root.layoutParams = root.layoutParams.apply { this.height += delta }
                    root.requestLayout()
                    ModuleDebugLog.i("GlassSoundbar", "Panel label layout: hidden=${hiddenLabels[root]} label=$originalExtent->$compactExtent recycler=$originalHeight->$height")
                }
            }
        }
        if (rows.isEmpty()) return
        val signatures = rows.mapNotNull { row ->
            val frame = Reflect.call(row, "getSliderFrame") as? View ?: return@mapNotNull null
            val lp = frame.layoutParams as? ViewGroup.MarginLayoutParams ?: return@mapNotNull null
            "h=${lp.height},topMargin=${lp.topMargin},pad=${frame.paddingLeft}/${frame.paddingTop}/${frame.paddingRight}/${frame.paddingBottom}"
        }.distinct()
        if (signatures.size > 1) {
            ModuleDebugLog.w("GlassSoundbar", "ROM row frames differ; preserving native geometry: $signatures")
        }
    }

    private fun dimenPx(view: View, name: String): Float? {
        val id = view.resources.getIdentifier(name, "dimen", "com.android.systemui")
        if (id == 0) return null
        return runCatching { view.resources.getDimension(id) }.getOrNull()
    }

    private fun deviceSummary(view: View): String {
        val dm = view.resources.displayMetrics
        val orientation = view.resources.configuration.orientation
        val systemUiVersion = runCatching {
            view.context.packageManager.getPackageInfo("com.android.systemui", 0).versionName
        }.getOrNull()
        return "profile=${DeviceProfile.current} device=${Build.MANUFACTURER}/${Build.MODEL}/${Build.DEVICE} sdk=${Build.VERSION.SDK_INT} display=${dm.widthPixels}x${dm.heightPixels}@${dm.densityDpi} density=${dm.density} orientation=$orientation systemUi=$systemUiVersion"
    }

    private fun rectOnScreen(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun logView(label: String, view: View) {
        val lp = view.layoutParams
        val margins = (lp as? ViewGroup.MarginLayoutParams)?.let {
            " margins=${it.leftMargin}/${it.topMargin}/${it.rightMargin}/${it.bottomMargin}"
        } ?: ""
        ModuleDebugLog.i(
            "GlassSoundbar",
            "$label class=${view.javaClass.name} screen=${rectOnScreen(view)} size=${view.width}x${view.height} measured=${view.measuredWidth}x${view.measuredHeight} lp=${lp?.width}x${lp?.height}$margins pad=${view.paddingLeft}/${view.paddingTop}/${view.paddingRight}/${view.paddingBottom} trans=${view.translationX},${view.translationY} scale=${view.scaleX},${view.scaleY} alpha=${view.alpha} visibility=${view.visibility}"
        )
    }

    private fun logGeometrySnapshot(native: Any, root: ViewGroup, stage: String, rows: List<*>) {
        runCatching {
            val recycler = Reflect.field(native, "mVolumeRecyclerview") as? View ?: return@runCatching
            ModuleDebugLog.i(
                "GlassSoundbar",
                "Geometry snapshot[$stage] ${deviceSummary(root)} resources={padding=${dimenPx(root, "volume_dialog_recycleview_padding_left")}, paddingLand=${dimenPx(root, "volume_dialog_recycleview_padding_left_land")}, recyclerH=${dimenPx(root, "volume_vertical_recyclerview_height")}, radius=${dimenPx(root, "volume_vertical_row_radius_os17")}, seekWidth=${dimenPx(root, "volume_vertical_seek_bar_width")}, seekHeight=${dimenPx(root, "volume_vertical_seek_bar_height")}, elevTop=${dimenPx(root, "oplus_volume_elevation_padding_top")}, elevBottom=${dimenPx(root, "oplus_volume_elevation_padding_bottom")}}"
            )
            logView("$stage-root", root)
            logView("$stage-recycler", recycler)
            rows.forEachIndexed { index, rawRow ->
                val row = rawRow ?: return@forEachIndexed
                val rowView = Reflect.call(row, "getView") as? View
                val frame = Reflect.call(row, "getSliderFrame") as? View
                val slider = Reflect.call(row, "getSlider") as? View
                val stream = runCatching { Reflect.call(row, "getStream") }.getOrNull()
                if (rowView != null) logView("$stage-row[$index stream=$stream]", rowView)
                if (frame != null) logView("$stage-frame[$index]", frame)
                if (slider != null) {
                    logView("$stage-slider[$index]", slider)
                    ModuleDebugLog.i("GlassSoundbar", "$stage-capsule[$index]=${visualCapsuleOnScreen(slider)}")
                }
            }
        }.onFailure {
            ModuleDebugLog.w("GlassSoundbar", "Unable to record geometry snapshot[$stage]", it)
        }
    }
}
