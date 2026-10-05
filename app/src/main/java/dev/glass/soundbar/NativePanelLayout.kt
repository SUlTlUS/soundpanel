package dev.glass.soundbar

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.WeakHashMap

/** Geometry only. Never replace a ROM Drawable, seekbar or touch listener. */
internal object NativePanelLayout {
    private const val PANEL_PADDING_DP = 12f
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
    private val recyclerPaddings = WeakHashMap<View, IntArray>()
    private val radii = WeakHashMap<Any, List<Float>>()
    private val paddingScales = WeakHashMap<View, Float>()
    private val rowLayouts = WeakHashMap<View, IntArray>()
    var activeNative: Any? = null
        private set
    private val corners = listOf("LeftTopCornerRadius", "RightTopCornerRadius", "LeftBottomCornerRadius", "RightBottomCornerRadius")
    var activeRoot: ViewGroup? = null
        private set

    fun apply(native: Any, settings: PanelSettings) {
        val root = Reflect.field(native, "mDialogView") as ViewGroup
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as View
        if (!settings.enabled) {
            restore(native)
            return
        }
        captureCollapsedCapsule(native, root)
        NativeActions.hide(native)
        val original = root.layoutParams
        if (original !is FrameLayout.LayoutParams) return
        originals.putIfAbsent(root, FrameLayout.LayoutParams(original))
        val bounds = root.resources.displayMetrics
        val landscape = root.resources.configuration.orientation == 2
        root.layoutParams = FrameLayout.LayoutParams(
            original.width, original.height, Gravity.TOP or Gravity.RIGHT
        ).apply {
            topMargin = if (landscape) 0 else (bounds.heightPixels * .18f).toInt()
            rightMargin = (bounds.widthPixels * if (landscape) .025f else .04f).toInt()
        }
        paddingScales[root] = settings.size
        activeRoot = root
        activeNative = native
        biases.putIfAbsent(recycler, Reflect.field(recycler.layoutParams, "verticalBias") as Float)
        Reflect.set(recycler.layoutParams, "verticalBias", 0f)
        if (landscape) {
            recyclerPaddings.putIfAbsent(
                recycler,
                intArrayOf(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, recycler.paddingBottom)
            )
            recycler.setPadding(0, recycler.paddingTop, 0, recycler.paddingBottom)
        }
        recycler.requestLayout()
        alignCorners(native, root, beforeSelection = true)
    }

    fun restore(native: Any) {
        val root = Reflect.field(native, "mDialogView") as? ViewGroup ?: return
        originals.remove(root)?.let { root.layoutParams = it }
        paddingScales.remove(root)
        collapsedCapsules.remove(root)
        NativeActions.hide(native)
        rowLayouts.entries.toList().forEach { (view, saved) ->
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.height = saved[0]; lp.topMargin = saved[1]
            view.setPadding(saved[2], saved[3], saved[4], saved[5])
            view.layoutParams = lp
        }
        rowLayouts.clear()
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as? View
        if (recycler != null) {
            biases.remove(recycler)?.let { Reflect.set(recycler.layoutParams, "verticalBias", it) }
            recyclerPaddings.remove(recycler)?.let { saved ->
                recycler.setPadding(saved[0], saved[1], saved[2], saved[3])
            }
            recycler.translationY = 0f
            recycler.requestLayout()
        }
        material(native)?.let { proxy ->
            NativeMaterial.restore(proxy, root)
            val config = Reflect.call(proxy, "getBlurConfig")!!
            radii.remove(config)?.let { values ->
                corners.forEachIndexed { i, corner -> Reflect.call(config, "set$corner", values[i]) }
                Reflect.call(proxy, "applyBlurConfig")
            }
        }
        activeRoot = null
        activeNative = null
    }

    private fun material(native: Any): Any? {
        val drawable = Reflect.field(native, "mVolumeBackgroundBlurDrawable") ?: return null
        return if (drawable.javaClass.simpleName == "AutoBlurDrawable") Reflect.call(drawable, "getViewBlurProxy") else null
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
        // Match the ROM animator's settled spacing; animated screen coordinates
        // are not suitable here. This runs synchronously before the first draw.
        val density = row.resources.displayMetrics.density
        val rowWidth = row.layoutParams.width.takeIf { it > 0 } ?: (56f * density).toInt()
        val sliderWidth = slider.layoutParams.width.takeIf { it > 0 } ?: slider.measuredWidth
        val padding = PANEL_PADDING_DP * density * (paddingScales[root] ?: 1f)
        val rowSideInset = (rowWidth - sliderWidth).coerceAtLeast(0).toFloat()
        // Drive the geometry from the visible slider capsules, not from the
        // wider RecyclerView rows: outer padding == inter-slider gap.
        val targetSliderGap = padding
        val rowGap = targetSliderGap - rowSideInset
        activePaddingPx = padding
        activeSliderWidthPx = sliderWidth.toFloat()
        val contentWidth = rows.size * rowWidth + (rows.size - 1) * rowGap
        val panelWidth = (contentWidth - (rowWidth - sliderWidth) + 2f * padding).toInt()
        val inset = ((panelWidth - contentWidth) / 2f + (rowWidth - sliderWidth) / 2f).coerceAtLeast(0f)
        val sliderFrame = Reflect.call(first, "getSliderFrame") as ViewGroup
        // The native panel binder clears the collapsed frame's top margin.
        // Only its retained padding contributes to the expanded slider offset.
        val sliderTopInset = sliderFrame.paddingTop
        val sliderBottomInset = sliderFrame.paddingBottom
        recycler.translationY = inset - sliderTopInset
        val rowsHeight = recycler.layoutParams.height.takeIf { it > 0 } ?: recycler.measuredHeight
        // Measure the card from the visible seekbar capsule, not from the padded
        // row frame. This keeps top and bottom panel padding visually equal.
        val visibleSliderHeight = (rowsHeight - sliderTopInset - sliderBottomInset).coerceAtLeast(0)
        // Keep the complete row (including the bottom icon/label area) inside
        // the card. The visible capsule is aligned separately below; shrinking
        // the card to capsuleHeight + padding clips the row accessories.
        val panelHeight = (recycler.translationY + rowsHeight + inset).toInt()
        val expandedSliderCenterLocal = inset + visibleSliderHeight / 2f
        root.layoutParams = root.layoutParams.apply {
            width = panelWidth
            height = panelHeight
            if (this is FrameLayout.LayoutParams) {
                val anchor = collapsedCapsules[root]
                if (anchor != null) {
                    val parentTop = (root.parent as? View)?.let { parent ->
                        val location = IntArray(2)
                        parent.getLocationOnScreen(location)
                        location[1]
                    } ?: 0
                    topMargin = (anchor.exactCenterY() - parentTop - expandedSliderCenterLocal).toInt().coerceAtLeast(0)
                } else if (root.resources.configuration.orientation == 2) {
                    topMargin = ((root.resources.displayMetrics.heightPixels - panelHeight) / 2).coerceAtLeast(0)
                }
            }
        }
        val radiusId = row.resources.getIdentifier("volume_vertical_row_radius_os17", "dimen", "com.android.systemui")
        val sliderRadius = if (radiusId != 0) row.resources.getDimension(radiusId) else sliderWidth / 2f
        val radius = (inset + sliderRadius).coerceAtMost(panelWidth / 2f)
        material(native)?.let { proxy ->
            NativeMaterial.apply(proxy, root, radius)
            val config = Reflect.call(proxy, "getBlurConfig")!!
            radii.putIfAbsent(config, corners.map { Reflect.call(config, "get$it") as Float })
            corners.forEach { Reflect.call(config, "set$it", radius) }
            Reflect.call(proxy, "applyBlurConfig")
        }
        root.invalidate()
        android.util.Log.i(
            "GlassSoundbar",
            "Geometry before first frame: radius=$radius inset=$inset internalSliderGap=$targetSliderGap padding=$padding rowGap=$rowGap rows=${rows.size} panel=${panelWidth}x$panelHeight rowsHeight=$rowsHeight anchor=${collapsedCapsules[root]}"
        )
    }

    private fun captureCollapsedCapsule(native: Any, root: View) {
        runCatching {
            val interactor = Reflect.field(native, "volumeInteractor") ?: return@runCatching
            val activeRow = Reflect.call(interactor, "getActiveRow") ?: return@runCatching
            val slider = Reflect.call(activeRow, "getSlider") as? View ?: return@runCatching
            val rect = visualCapsuleOnScreen(slider) ?: return@runCatching
            collapsedCapsules[root] = Rect(rect)
            android.util.Log.i("GlassSoundbar", "Collapsed capsule anchor=$rect centerY=${rect.exactCenterY()}")
        }.onFailure {
            android.util.Log.w("GlassSoundbar", "Unable to capture collapsed capsule", it)
        }
    }

    private fun visualCapsuleOnScreen(slider: View): Rect? = runCatching {
        Reflect.call(slider, "preCalcMaterialClipPath")
        val local = Rect()
        val host = Reflect.field(slider, "mMaterialHost")
        if (host != null) Reflect.call(host, "resolveVisualCapsuleInto", local)
        if (local.isEmpty) Reflect.call(slider, "copyBackgroundRect", local)
        if (local.isEmpty && slider.width > 0 && slider.height > 0) local.set(0, 0, slider.width, slider.height)
        if (local.isEmpty) return@runCatching null
        val location = IntArray(2)
        slider.getLocationOnScreen(location)
        local.offset(location[0], location[1])
        local
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
        val padding = activePaddingPx.takeIf { it > 0f } ?: PANEL_PADDING_DP * root.resources.displayMetrics.density
        val currentRootTop = rootLocation[1]
        val desiredRootTop = (anchor.top - padding).toInt()
        val rootDelta = desiredRootTop - currentRootTop
        val capsuleLocalTop = capsule.top - currentRootTop
        val recyclerDelta = padding - capsuleLocalTop
        val changed = kotlin.math.abs(rootDelta) > 1 || kotlin.math.abs(recyclerDelta) > 1f
        if (!changed) return false

        recycler.translationY += recyclerDelta
        val lp = root.layoutParams as? FrameLayout.LayoutParams ?: return false
        lp.topMargin = (lp.topMargin + rootDelta).coerceAtLeast(0)
        val desiredHeight = lp.height
        root.layoutParams = lp
        root.requestLayout()
        root.invalidate()
        android.util.Log.i(
            "GlassSoundbar",
            "Pre-draw capsule sync: collapsed=$anchor expanded=$capsule padding=$padding panelHeight=$desiredHeight top=${lp.topMargin} rootDelta=$rootDelta recyclerDelta=$recyclerDelta recyclerY=${recycler.translationY}"
        )
        return true
    }

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
        alignCorners(native, root)
        normalizeRows()
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
                return true
            }
        })
    }

    fun normalizeRows() {
        val native = activeNative ?: return
        val adapter = Reflect.field(native, "volumeListAdapter")!!
        val rows = (Reflect.call(adapter, "getPanelShowList") as List<*>).filterNotNull()
        val reference = rows.firstOrNull()?.let { Reflect.call(it, "getSliderFrame") as View } ?: return
        val top = reference.paddingTop
        val bottom = reference.paddingBottom
        rows.forEach { row ->
            val frame = Reflect.call(row, "getSliderFrame") as View
            val lp = frame.layoutParams as ViewGroup.MarginLayoutParams
            rowLayouts.putIfAbsent(frame, intArrayOf(lp.height, lp.topMargin, frame.paddingLeft, frame.paddingTop, frame.paddingRight, frame.paddingBottom))
            if (lp.height != -1 || lp.topMargin != 0 || frame.paddingTop != top || frame.paddingBottom != bottom) {
                lp.height = -1; lp.topMargin = 0
                frame.setPadding(frame.paddingLeft, top, frame.paddingRight, bottom)
                frame.layoutParams = lp
            }
        }
    }
}
