package com.kepsake.mizu2.ui.webtoon

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var isZooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0
    var originalHeight = 0
        private set
    private var heightSet = false
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE

    // Replace AnimatorSet with OverScroller
    private var overScroller: CustomScroller
    private var flingRunnable: Runnable? = null

    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && currentScale < DEFAULT_RATE) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
            }
        }

    private val minRate
        get() = if (zoomOutDisabled) DEFAULT_RATE else MIN_RATE

    private val listener = GestureListener()
    private val detector = Detector()

    var doubleTapZoom = true

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    init {
        overScroller = CustomScroller(context)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        if (!heightSet) {
            originalHeight = MeasureSpec.getSize(heightSpec)
            heightSet = true
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager
        lastVisibleItemPosition =
            (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val layoutManager = layoutManager
        val visibleItemCount = layoutManager?.childCount ?: 0
        val totalItemCount = layoutManager?.itemCount ?: 0
        atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0
    }

    private fun getPositionX(positionX: Float): Float {
        if (currentScale < 1) {
            return 0f
        }
        val maxPositionX = halfWidth * (currentScale - 1)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (currentScale < 1) {
            return (originalHeight / 2 - halfHeight).toFloat()
        }
        val maxPositionY = halfHeight * (currentScale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(
        fromRate: Float,
        toRate: Float,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float,
    ) {
        isZooming = true
        val animatorSet = AnimatorSet()
        val translationXAnimator = ValueAnimator.ofFloat(fromX, toX)
        translationXAnimator.addUpdateListener { animation -> x = animation.animatedValue as Float }

        val translationYAnimator = ValueAnimator.ofFloat(fromY, toY)
        translationYAnimator.addUpdateListener { animation -> y = animation.animatedValue as Float }

        val scaleAnimator = ValueAnimator.ofFloat(fromRate, toRate)
        scaleAnimator.addUpdateListener { animation ->
            currentScale = animation.animatedValue as Float
            setScaleRate(currentScale)
        }
        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator)
        animatorSet.duration = ANIMATOR_DURATION_TIME.toLong()
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.doOnEnd {
            isZooming = false
            currentScale = toRate
        }
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        // Cancel any existing flings
        cancelFling()

        // Calculate start and max positions
        val startX = x.toInt()
        val startY = y.toInt()

        overScroller.fling(
            startX,
            startY,
            velocityX,
            if (atFirstPosition || atLastPosition) velocityY else 0,  // velocities
            Int.MIN_VALUE,
            Int.MAX_VALUE,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
        )

        // Create and post a runnable to update the view
        flingRunnable = object : Runnable {
            override fun run() {
                if (overScroller.computeScrollOffset()) {
                    x = getPositionX(overScroller.getCurrX().toFloat())
                    y = getPositionY(overScroller.getCurrY().toFloat())

                    // Continue updating
                    postOnAnimation(this)
                }
            }
        }

        postOnAnimation(flingRunnable)
        return true
    }

    fun cancelFling() {
        flingRunnable?.let {
            removeCallbacks(it)
            flingRunnable = null
        }
        overScroller.abortAnimation()
        stopScroll() // Stop RecyclerView's built-in fling
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) {
            x = getPositionX(x + dx)
        }
        if (dy != 0) {
            y = getPositionY(y + dy)
        }
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
    }

    fun onScale(scaleFactor: Float) {
        currentScale *= scaleFactor
        currentScale = currentScale.coerceIn(
            minRate,
            MAX_SCALE_RATE,
        )

        setScaleRate(currentScale)

        layoutParams.height = if (currentScale < 1) {
            (originalHeight / currentScale).toInt()
        } else {
            originalHeight
        }
        halfHeight = layoutParams.height / 2

        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }

        requestLayout()
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true
        }
    }

    fun onScaleEnd() {
        if (scaleX < minRate) {
            zoom(currentScale, minRate, x, 0f, y, 0f)
        }
    }

    inner class GestureListener : SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            longTapListener?.invoke(e)
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (!isZooming && doubleTapZoom) {
                if (scaleX != DEFAULT_RATE) {
                    zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                    layoutParams.height = originalHeight
                    halfHeight = layoutParams.height / 2
                    requestLayout()
                } else {
                    val toScale = 2f
                    val toX = (halfWidth - ev.x) * (toScale - 1)
                    val toY = (halfHeight - ev.y) * (toScale - 1)
                    zoom(DEFAULT_RATE, toScale, 0f, toX, 0f, toY)
                }
            }
        }
    }

    inner class Detector : GestureDetector(context, listener) {

        private var scrollPointerId = 0
        private var downX = 0f
        private var downY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var isZoomDragging = false
        var isDoubleTapping = false
        var isQuickScaling = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    // Cancel any active fling when user touches the screen
                    cancelFling()
                    scrollPointerId = ev.getPointerId(0)
                    downX = ev.x
                    downY = ev.y
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = ev.getX(actionIndex)
                    downY = ev.getY(actionIndex)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapping && isQuickScaling) {
                        return true
                    }

                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) {
                        return false
                    }

                    val x = ev.getX(index)
                    val y = ev.getY(index)
                    var dx = x - downX
                    var dy = if (atFirstPosition || atLastPosition) y - downY else 0f

                    if (!isZoomDragging && currentScale > 1f) {
                        var startScroll = false
                        val effectiveTouchSlop = touchSlop / currentScale

                        if (abs(dx) > effectiveTouchSlop) {
                            if (dx < 0) {
                                dx += effectiveTouchSlop
                            } else {
                                dx -= effectiveTouchSlop
                            }
                            startScroll = true
                        }
                        if (abs(dy) > effectiveTouchSlop) {
                            if (dy < 0) {
                                dy += effectiveTouchSlop
                            } else {
                                dy -= effectiveTouchSlop
                            }
                            startScroll = true
                        }

                        if (startScroll) {
                            isZoomDragging = true
                        }
                    }

                    if (isZoomDragging) {
                        val scaledDx = (dx * currentScale).toInt()
                        val scaledDy = (dy * currentScale).toInt()
                        zoomScrollBy(scaledDx, scaledDy)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isDoubleTapping && !isQuickScaling) {
                        listener.onDoubleTapConfirmed(ev)
                    }
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }

                MotionEvent.ACTION_CANCEL -> {
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    // Add QuinticInterpolator class for matching RecyclerView fling behavior
    private class QuinticInterpolator : Interpolator {
        override fun getInterpolation(t: Float): Float {

            Log.e("Interpolator", "getInterpolation: $t")
            if (t < 0.4f) return t * t * t * 0.2f
            return t * t * t * t * t
        }
    }
}

private const val ANIMATOR_DURATION_TIME = 200
private const val MIN_RATE = 1f
private const val DEFAULT_RATE = 1f
private const val MAX_SCALE_RATE = 8f
