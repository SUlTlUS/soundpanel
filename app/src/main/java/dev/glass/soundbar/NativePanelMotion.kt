package dev.glass.soundbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import java.util.IdentityHashMap

/** Animate the bounds of the existing ROM material, never scale its contents. */
internal object NativePanelMotion {
    private data class RowTrack(val view: View, val settledTranslation: Float)
    private class Motion(val root: View, val originalClip: Rect?) {
        val backgrounds = IdentityHashMap<Drawable, Rect>()
        val bounds = Rect()
        var animator: ValueAnimator? = null
        var observer: ViewTreeObserver? = null
        var listener: ViewTreeObserver.OnPreDrawListener? = null
        var detachListener: View.OnAttachStateChangeListener? = null
    }

    private val motions = IdentityHashMap<View, Motion>()
    private val backgrounds = IdentityHashMap<Drawable, Motion>()
    private val rowTracks = IdentityHashMap<View, RowTrack>()
    private val trackingRoots = IdentityHashMap<View, Boolean>()
    private val pendingShows = IdentityHashMap<View, Pair<ViewTreeObserver, ViewTreeObserver.OnPreDrawListener>>()
    private val enterCurve = PathInterpolator(0.2f, 0f, 0.2f, 1f)
    private val exitCurve = PathInterpolator(0.4f, 0f, 1f, 1f)

    fun prepareExpand(root: View) {
        rowTracks.remove(root)
        trackingRoots[root] = true
    }

    fun trackLeadingRow(root: View, row: View, settledTranslation: Float) {
        if (trackingRoots[root] == true && !rowTracks.containsKey(root)) {
            rowTracks[root] = RowTrack(row, settledTranslation)
        }
    }

    fun expand(root: View, capsuleWidth: Float) =
        start(root, capsuleWidth, root.width.toFloat(), 180L, true, capsuleWidth)

    fun show(root: View) {
        cancel(root)
        val observer = root.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                cancelPending(root)
                start(root, root.width * 0.65f, root.width.toFloat(), 140L, true)
                return true
            }
        }
        pendingShows[root] = observer to listener
        observer.addOnPreDrawListener(listener)
    }

    fun hide(root: View, capsuleWidth: Float?) {
        start(root, root.width.toFloat(), capsuleWidth ?: (root.width * 0.65f), 160L, false, capsuleWidth)
    }

    private fun start(root: View, fromWidth: Float, toWidth: Float, duration: Long, entering: Boolean, capsuleWidth: Float? = null) {
        if (!root.isAttachedToWindow || root.width <= 0 || root.height <= 0) return
        val previous = motions[root]
        val startWidth = previous?.bounds?.width()?.toFloat() ?: fromWidth
        val originalClip = if (previous != null) previous.originalClip else root.clipBounds?.let(::Rect)
        cancel(root, keepTrack = true)
        val motion = Motion(root, originalClip)
        motions[root] = motion
        var progress = 0f
        var clockEnded = false
        var boundsApplied = false
        var opacityFailed = false
        motion.bounds.set(PanelMorphGeometry.left(root.width, startWidth, toWidth, 0f), 0, root.width, root.height)
        fun applyFrame() {
            val track = if (capsuleWidth != null) rowTracks[root] else null
            val desiredLeft = if (track != null) {
                if (!entering && (!track.view.isAttachedToWindow || track.view.alpha <= 0.01f)) {
                    PanelMorphGeometry.left(root.width, startWidth, toWidth, 1f)
                } else {
                    PanelMorphGeometry.followedLeft(root.width, capsuleWidth!!, track.view.translationX, track.settledTranslation)
                }
            } else PanelMorphGeometry.left(root.width, startWidth, toWidth, progress)
            val left = PanelMorphGeometry.advanceLeft(motion.bounds.left, desiredLeft, entering)
            val changed = !boundsApplied || motion.bounds.left != left ||
                motion.bounds.right != root.width || motion.bounds.bottom != root.height
            if (changed) {
                motion.bounds.set(left, 0, root.width, root.height)
                root.clipBounds = motion.bounds
                NativeMaterial.setMotionBounds(root, motion.bounds)
                boundsApplied = true
            }
            root.background?.let { drawable ->
                if (!motion.backgrounds.containsKey(drawable)) {
                    motion.backgrounds[drawable] = Rect(drawable.bounds)
                    backgrounds[drawable] = motion
                }
                if (drawable.bounds != motion.bounds) drawable.bounds = motion.bounds
            }
            if (!entering && !opacityFailed) {
                runCatching { NativeMaterial.syncMotionOpacity(root) }.onFailure {
                    opacityFailed = true
                    ModuleDebugLog.e("GlassSoundbar", "Native stroke fade failed", it)
                }
            }
        }
        motion.listener = ViewTreeObserver.OnPreDrawListener {
            applyFrame()
            if (entering && (motion.bounds.left == 0 ||
                    (clockEnded && (capsuleWidth == null || rowTracks[root] == null)))) {
                cancel(root, keepTrack = true)
            }
            true
        }
        motion.observer = root.viewTreeObserver.also { it.addOnPreDrawListener(motion.listener) }
        motion.detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = cancel(view)
        }.also { root.addOnAttachStateChangeListener(it) }
        applyFrame()
        // A tracked native row supplies both progress and invalidation. Keep a
        // fallback clock only for untracked transitions instead of running two clocks.
        motion.animator = if (capsuleWidth != null && rowTracks[root] != null) null else ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = if (entering) enterCurve else exitCurve
            addUpdateListener {
                progress = it.animatedValue as Float
                // Native row translation already schedules a frame. Commit the
                // material once at pre-draw, after all native animators update.
                if (capsuleWidth == null || rowTracks[root] == null) root.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (motions[root] === motion) {
                        clockEnded = true
                        // Draw the last clock frame before restoring full bounds.
                        root.invalidate()
                    }
                }
            })
            start()
        }
        ModuleDebugLog.i("GlassSoundbar", "Panel morph: entering=$entering width=$startWidth->$toWidth source=${if (capsuleWidth != null) "native-row" else "clock"} fallbackDuration=$duration right=${root.right}")
    }

    /** View.draw may reset a resized background's bounds after pre-draw. */
    fun applyBackgroundBounds(drawable: Drawable) {
        backgrounds[drawable]?.let { if (drawable.bounds != it.bounds) drawable.bounds = it.bounds }
    }

    fun cancel(root: View) = cancel(root, keepTrack = false)

    private fun cancel(root: View, keepTrack: Boolean) {
        cancelPending(root)
        if (!keepTrack) {
            rowTracks.remove(root)
            trackingRoots.remove(root)
        }
        val motion = motions.remove(root) ?: return
        motion.animator?.cancel()
        motion.detachListener?.let(root::removeOnAttachStateChangeListener)
        motion.listener?.let { listener ->
            motion.observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
        }
        root.clipBounds = motion.originalClip
        motion.backgrounds.forEach { (drawable, bounds) ->
            backgrounds.remove(drawable)
            drawable.bounds = if (drawable === root.background) Rect(0, 0, root.width, root.height) else bounds
        }
        NativeMaterial.setMotionBounds(root, null)
        NativeMaterial.resetMotionOpacity(root)
        root.invalidate()
    }

    private fun cancelPending(root: View) {
        pendingShows.remove(root)?.let { (observer, listener) ->
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }
}
