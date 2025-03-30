package com.kepsake.mizu2.ui.webtoon

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator

class CustomScroller(
    context: Context,
    interpolator: Interpolator? = null,
    flywheel: Boolean = context.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.HONEYCOMB
) {
    private val mInterpolator: Interpolator

    private var mMode: Int = 0

    private var mStartX: Int = 0
    private var mStartY: Int = 0
    private var mFinalX: Int = 0
    private var mFinalY: Int = 0

    private var mMinX: Int = 0
    private var mMaxX: Int = 0
    private var mMinY: Int = 0
    private var mMaxY: Int = 0

    private var mCurrX: Int = 0
    private var mCurrY: Int = 0
    private var mStartTime: Long = 0
    private var mDuration: Int = 0
    private var mDurationReciprocal: Float = 0f
    private var mDeltaX: Float = 0f
    private var mDeltaY: Float = 0f
    private var mFinished: Boolean = true
    private var mFlywheel: Boolean = false

    private var mVelocity: Float = 0f
    private var mCurrVelocity: Float = 0f
    private var mDistance: Int = 0

    private var mFlingFriction: Float = ViewConfiguration.getScrollFriction()

    private var mDeceleration: Float = 0f
    private val mPpi: Float

    private var mPhysicalCoeff: Float = 0f

    companion object {
        private const val DEFAULT_DURATION = 250
        private const val SCROLL_MODE = 0
        private const val FLING_MODE = 1

        private val DECELERATION_RATE = (Math.log(0.78) / Math.log(0.9)).toFloat()
        private const val INFLEXION = 0.35f // Tension lines cross at (INFLEXION, 1)
        private const val START_TENSION = 0.5f
        private const val END_TENSION = 1.0f
        private const val P1 = START_TENSION * INFLEXION
        private const val P2 = 1.0f - END_TENSION * (1.0f - INFLEXION)

        private const val NB_SAMPLES = 100
        private val SPLINE_POSITION = FloatArray(NB_SAMPLES + 1)
        private val SPLINE_TIME = FloatArray(NB_SAMPLES + 1)

        init {
            var x_min = 0.0f
            var y_min = 0.0f
            for (i in 0 until NB_SAMPLES) {
                val alpha = i.toFloat() / NB_SAMPLES

                var x_max = 1.0f
                var x: Float
                var tx: Float
                var coef: Float
                while (true) {
                    x = x_min + (x_max - x_min) / 2.0f
                    coef = 3.0f * x * (1.0f - x)
                    tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x
                    if (Math.abs(tx - alpha) < 1E-5) break
                    if (tx > alpha) x_max = x
                    else x_min = x
                }
                SPLINE_POSITION[i] = coef * ((1.0f - x) * START_TENSION + x) + x * x * x

                var y_max = 1.0f
                var y: Float
                var dy: Float
                while (true) {
                    y = y_min + (y_max - y_min) / 2.0f
                    coef = 3.0f * y * (1.0f - y)
                    dy = coef * ((1.0f - y) * START_TENSION + y) + y * y * y
                    if (Math.abs(dy - alpha) < 1E-5) break
                    if (dy > alpha) y_max = y
                    else y_min = y
                }
                SPLINE_TIME[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y
            }
            SPLINE_POSITION[NB_SAMPLES] = 1.0f
            SPLINE_TIME[NB_SAMPLES] = 1.0f
        }
    }

    init {
        mInterpolator = interpolator ?: ViscousFluidInterpolator()
        mPpi = context.resources.displayMetrics.density * 160.0f
        mDeceleration = computeDeceleration(ViewConfiguration.getScrollFriction())
        mFlywheel = flywheel

        mPhysicalCoeff = computeDeceleration(0.31f) // look and feel tuning
    }

    fun setFriction(friction: Float) {
        mDeceleration = computeDeceleration(friction)
        mFlingFriction = friction
    }

    private fun computeDeceleration(friction: Float): Float {
        return SensorManager.GRAVITY_EARTH * 39.37f * mPpi * friction
    }

    fun isFinished(): Boolean {
        return mFinished
    }

    fun forceFinished(finished: Boolean) {
        mFinished = finished
    }

    fun getDuration(): Int {
        return mDuration
    }

    fun getCurrX(): Int {
        return mCurrX
    }

    fun getCurrY(): Int {
        return mCurrY
    }

    fun getCurrVelocity(): Float {
        return if (mMode == FLING_MODE)
            mCurrVelocity
        else
            mVelocity - mDeceleration * timePassed() / 2000.0f
    }

    fun getStartX(): Int {
        return mStartX
    }

    fun getStartY(): Int {
        return mStartY
    }

    fun getFinalX(): Int {
        return mFinalX
    }

    fun getFinalY(): Int {
        return mFinalY
    }

    fun computeScrollOffset(): Boolean {
        if (mFinished) {
            return false
        }

        val timePassed = (AnimationUtils.currentAnimationTimeMillis() - mStartTime).toInt()

        if (timePassed < mDuration) {
            when (mMode) {
                SCROLL_MODE -> {
                    val x = mInterpolator.getInterpolation(timePassed * mDurationReciprocal)
                    mCurrX = mStartX + Math.round(x * mDeltaX)
                    mCurrY = mStartY + Math.round(x * mDeltaY)
                }

                FLING_MODE -> {
                    val t = timePassed.toFloat() / mDuration
                    val index = (NB_SAMPLES * t).toInt()
                    var distanceCoef = 1.0f
                    var velocityCoef = 0.0f
                    if (index < NB_SAMPLES) {
                        val t_inf = index.toFloat() / NB_SAMPLES
                        val t_sup = (index + 1).toFloat() / NB_SAMPLES
                        val d_inf = SPLINE_POSITION[index]
                        val d_sup = SPLINE_POSITION[index + 1]
                        velocityCoef = (d_sup - d_inf) / (t_sup - t_inf)
                        distanceCoef = d_inf + (t - t_inf) * velocityCoef
                    }

                    mCurrVelocity = velocityCoef * mDistance / mDuration * 1000.0f

                    mCurrX = mStartX + Math.round(distanceCoef * (mFinalX - mStartX))
                    // Pin to mMinX <= mCurrX <= mMaxX
                    mCurrX = Math.min(mCurrX, mMaxX)
                    mCurrX = Math.max(mCurrX, mMinX)

                    mCurrY = mStartY + Math.round(distanceCoef * (mFinalY - mStartY))
                    // Pin to mMinY <= mCurrY <= mMaxY
                    mCurrY = Math.min(mCurrY, mMaxY)
                    mCurrY = Math.max(mCurrY, mMinY)

                    if (mCurrX == mFinalX && mCurrY == mFinalY) {
                        mFinished = true
                    }
                }
            }
        } else {
            mCurrX = mFinalX
            mCurrY = mFinalY
            mFinished = true
        }
        return true
    }

    fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        startScroll(startX, startY, dx, dy, DEFAULT_DURATION)
    }

    fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        mMode = SCROLL_MODE
        mFinished = false
        mDuration = duration
        mStartTime = AnimationUtils.currentAnimationTimeMillis()
        mStartX = startX
        mStartY = startY
        mFinalX = startX + dx
        mFinalY = startY + dy
        mDeltaX = dx.toFloat()
        mDeltaY = dy.toFloat()
        mDurationReciprocal = 1.0f / mDuration
    }

    fun fling(
        startX: Int, startY: Int, velocityX: Int, velocityY: Int,
        minX: Int, maxX: Int, minY: Int, maxY: Int
    ) {
        // Continue a scroll or fling in progress
        var velocityX = velocityX
        var velocityY = velocityY
        if (mFlywheel && !mFinished) {
            val oldVel = getCurrVelocity()

            val dx = (mFinalX - mStartX).toFloat()
            val dy = (mFinalY - mStartY).toFloat()
            val hyp = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

            val ndx = dx / hyp
            val ndy = dy / hyp

            val oldVelocityX = ndx * oldVel
            val oldVelocityY = ndy * oldVel
            if (Math.signum(velocityX.toFloat()) == Math.signum(oldVelocityX) &&
                Math.signum(velocityY.toFloat()) == Math.signum(oldVelocityY)
            ) {
                velocityX += oldVelocityX.toInt()
                velocityY += oldVelocityY.toInt()
            }
        }

        mMode = FLING_MODE
        mFinished = false

        val velocity = Math.hypot(velocityX.toDouble(), velocityY.toDouble()).toFloat()

        mVelocity = velocity
        mDuration = getSplineFlingDuration(velocity)
        mStartTime = AnimationUtils.currentAnimationTimeMillis()
        mStartX = startX
        mStartY = startY

        val coeffX = if (velocity == 0f) 1.0f else velocityX / velocity
        val coeffY = if (velocity == 0f) 1.0f else velocityY / velocity

        val totalDistance = getSplineFlingDistance(velocity)
        mDistance = (totalDistance * Math.signum(velocity.toDouble())).toInt()

        mMinX = minX
        mMaxX = maxX
        mMinY = minY
        mMaxY = maxY

        mFinalX = (startX + Math.round(totalDistance * coeffX)).toInt()
        // Pin to mMinX <= mFinalX <= mMaxX
        mFinalX = Math.min(mFinalX, mMaxX)
        mFinalX = Math.max(mFinalX, mMinX)

        mFinalY = (startY + Math.round(totalDistance * coeffY)).toInt()
        // Pin to mMinY <= mFinalY <= mMaxY
        mFinalY = Math.min(mFinalY, mMaxY)
        mFinalY = Math.max(mFinalY, mMinY)
    }

    private fun getSplineDeceleration(velocity: Float): Double {
        return Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff).toDouble())
    }

    private fun getSplineFlingDuration(velocity: Float): Int {
        val l = getSplineDeceleration(velocity)
        val decelMinusOne = DECELERATION_RATE - 1.0
        return (1000.0 * Math.exp(l / decelMinusOne)).toInt()
    }

    private fun getSplineFlingDistance(velocity: Float): Double {
        val l = getSplineDeceleration(velocity)
        val decelMinusOne = DECELERATION_RATE - 1.0
        return mFlingFriction * mPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l)
    }

    fun abortAnimation() {
        mCurrX = mFinalX
        mCurrY = mFinalY
        mFinished = true
    }

    fun extendDuration(extend: Int) {
        val passed = timePassed()
        mDuration = passed + extend
        mDurationReciprocal = 1.0f / mDuration
        mFinished = false
    }

    fun timePassed(): Int {
        return (AnimationUtils.currentAnimationTimeMillis() - mStartTime).toInt()
    }

    fun setFinalX(newX: Int) {
        mFinalX = newX
        mDeltaX = (mFinalX - mStartX).toFloat()
        mFinished = false
    }

    fun setFinalY(newY: Int) {
        mFinalY = newY
        mDeltaY = (mFinalY - mStartY).toFloat()
        mFinished = false
    }

    fun isScrollingInDirection(xvel: Float, yvel: Float): Boolean {
        return !mFinished && Math.signum(xvel.toDouble()) == Math.signum((mFinalX - mStartX).toDouble()) &&
                Math.signum(yvel.toDouble()) == Math.signum((mFinalY - mStartY).toDouble())
    }

    private class ViscousFluidInterpolator : Interpolator {
        companion object {
            /** Controls the viscous fluid effect (how much of it).  */
            private const val VISCOUS_FLUID_SCALE = 1.0f

            private val VISCOUS_FLUID_NORMALIZE: Float
            private val VISCOUS_FLUID_OFFSET: Float

            init {
                // must be set to 1.0 (used in viscousFluid())
                VISCOUS_FLUID_NORMALIZE = 1.0f / viscousFluid(1.0f)
                // account for very small floating-point error
                VISCOUS_FLUID_OFFSET = 1.0f - VISCOUS_FLUID_NORMALIZE * viscousFluid(1.0f)
            }

            private fun viscousFluid(x: Float): Float {
                var x = x
                x *= VISCOUS_FLUID_SCALE
                if (x < 1.0f) {
                    x -= (1.0f - Math.exp(-x.toDouble())).toFloat()
                } else {
                    val start = 0.36787944117f   // 1/e == exp(-1)
                    x = 1.0f - Math.exp(1.0 - x.toDouble()).toFloat()
                    x = start + x * (1.0f - start)
                }
                return x
            }
        }

        override fun getInterpolation(input: Float): Float {
            val interpolated = VISCOUS_FLUID_NORMALIZE * viscousFluid(input)
            return if (interpolated > 0) {
                interpolated + VISCOUS_FLUID_OFFSET
            } else {
                interpolated
            }
        }
    }
}