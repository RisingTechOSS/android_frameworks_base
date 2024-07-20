/*
 * Copyright (C) 2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.qs

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import com.android.systemui.res.R
import kotlin.math.abs
import com.android.internal.util.android.VibrationUtils

interface UserInteractionListener {
    fun onUserInteractionEnd()
    fun onLongPress()
    fun onUserSwipe()
}

open class VerticalSlider(context: Context, attrs: AttributeSet? = null) : CardView(context, attrs) {

    private val listeners: MutableList<UserInteractionListener> = mutableListOf()

    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private val longPressHandler = Handler()
    private val longPressRunnable: Runnable = Runnable {
        doLongPressAction()
    }
    private var isLongPressDetected = false
    private val longPressThreshold = 10f

    protected var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
        }
    private val cornerRadius = context.resources.getDimensionPixelSize(R.dimen.qs_controls_slider_corner_radius).toFloat()
    private val layoutRect: RectF = RectF()
    protected val progressRect: RectF = RectF()
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        isDither = true
        isFilterBitmap = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val layoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        isDither = true
        isFilterBitmap = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val path = Path()
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastProgress: Int = 0
    private val threshold = 0.05f
    private var actionPerformed = false

    private var lastVibrateTime: Long = 0
    private val SLIDER_HAPTICS_TIMEOUT: Long = 100

    private val isNightMode: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    init {
        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressDetected = false
                    actionPerformed = false
                    startLongPressDetection(event)
                    lastX = event.x
                    lastY = event.y
                    lastProgress = progress
                    requestDisallowInterceptTouchEventFromParentsAndRoot(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(lastX - event.x)
                    val deltaY = abs(lastY - event.y)
                    if (isLongPress(event, deltaX, deltaY)) {
                        isLongPressDetected = true
                        doLongPressAction()
                    } else {
                        cancelLongPressDetection()
                        val deltaY = lastY - event.y
                        val progressDelta = (deltaY * 100 / measuredHeight.toFloat()).toInt()
                        progress = (lastProgress + progressDelta).coerceIn(0, 100)
                        notifyListenersUserSwipe()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    notifyListenersUserInteractionEnd()
                    cancelLongPressDetection()
                    true
                }
                else -> {
                    false
                }
            }
        }
        backgroundTintList = ColorStateList.valueOf(
            context.getResources().getColor(if (isNightMode) R.color.qs_controls_container_bg_color_dark 
            else R.color.qs_controls_container_bg_color_light)
        )
        radius = cornerRadius
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        updateSliderPaint()
    }

    fun performSliderHaptics(intensity: Int) {
        if (intensity == 0) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVibrateTime >= SLIDER_HAPTICS_TIMEOUT) {
            VibrationUtils.triggerVibration(context, intensity)
            lastVibrateTime = currentTime
        }
    }

    private fun startLongPressDetection(event: MotionEvent) {
        longPressHandler.postDelayed(longPressRunnable, longPressTimeout.toLong())
    }

    private fun doLongPressAction() {
        if (isLongPressDetected && !actionPerformed) {
            listeners.forEach { it.onLongPress() }
            actionPerformed = true
            isLongPressDetected = false
        }
    }

    private fun cancelLongPressDetection() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        isLongPressDetected = false
    }

    private fun isLongPress(event: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
        val pressDuration = event.eventTime - event.downTime
        val distanceMoved = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
        return pressDuration >= longPressTimeout && distanceMoved < longPressThreshold
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE && (event.y <= 0 || event.y >= measuredHeight)) {
            return false
        }
        return true
    }

    fun addUserInteractionListener(listener: UserInteractionListener) {
        listeners.add(listener)
    }

    fun removeUserInteractionListener(listener: UserInteractionListener) {
        listeners.remove(listener)
    }

    private fun notifyListenersUserInteractionEnd() {
        listeners.forEach { it.onUserInteractionEnd() }
    }

    private fun notifyListenersUserSwipe() {
        listeners.forEach { it.onUserSwipe() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRect.set(0f, 0f, w.toFloat(), h.toFloat())
        progressRect.set(0f, (1 - progress / 100f) * h, w.toFloat(), h.toFloat())
        path.reset()
        path.addRoundRect(layoutRect, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredHeight > 0 && measuredWidth > 0) {
            layoutRect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
            progressRect.set(
                0f,
                (1 - progress / 100f) * measuredHeight,
                measuredWidth.toFloat(),
                measuredHeight.toFloat()
            )
            path.reset()
            path.addRoundRect(layoutRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val roundedRectPath = Path().apply {
            addRoundRect(layoutRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(roundedRectPath)
        canvas.drawRoundRect(layoutRect, cornerRadius, cornerRadius, layoutPaint)
        val progressRadii = floatArrayOf(
            0f, 0f,   // Top-left radius
            0f, 0f,   // Top-right radius
            cornerRadius, cornerRadius, // Bottom-right radius
            cornerRadius, cornerRadius  // Bottom-left radius
        )
        val progressRectPath = Path().apply {
            addRoundRect(progressRect, progressRadii, Path.Direction.CW)
        }
        canvas.drawPath(progressRectPath, progressPaint)
    }

    protected open fun updateSliderPaint() {
        backgroundTintList = ColorStateList.valueOf(
            context.getResources().getColor(if (isNightMode) R.color.qs_controls_container_bg_color_dark 
            else R.color.qs_controls_container_bg_color_light)
        )
        layoutPaint.color = context.getResources().getColor(if (isNightMode) R.color.qs_controls_container_bg_color_dark else R.color.qs_controls_container_bg_color_light)
        progressPaint.color = context.getResources().getColor(if (isNightMode) R.color.qs_controls_active_color_dark else R.color.qs_controls_active_color_light)
        invalidate()
    }

    fun updateIconTint(view: ImageView?) {
        val emptyThreshold = 20 // 20% of 100
        val isEmpty = progress <= emptyThreshold
        val iconColor = if (isEmpty) {
            if (isNightMode)
                R.color.qs_controls_bg_color_light
            else
                R.color.qs_controls_bg_color_dark
        } else {
            if (isNightMode)
                R.color.qs_controls_active_color_light
            else
                R.color.qs_controls_active_color_dark
        }
        val color = context.getResources().getColor(iconColor)
        view?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
    
    fun setSliderProgress(sliderProgress: Int) {
        progress = sliderProgress
    }

    protected open fun updateProgressRect() {
        val calculatedProgress = progress / 100f
        val newTop = (1 - calculatedProgress) * measuredHeight
        if (abs(newTop - progressRect.top) > measuredHeight * threshold) {
            progressRect.top = newTop
        } else {
            progressRect.top += (newTop - progressRect.top) * 0.1f
        }
        invalidate()
    }

    private fun requestDisallowInterceptTouchEventFromParentsAndRoot(disallowIntercept: Boolean) {
        var parentView = this.parent
        while (parentView != null && parentView !is ViewGroup) {
            parentView = parentView.parent
        }
        if (parentView != null) {
            parentView.requestDisallowInterceptTouchEvent(disallowIntercept)
        }

        var rootView = this.rootView
        while (rootView != null && rootView.parent != null && rootView.parent is View) {
            rootView = rootView.parent as View
        }
        rootView?.parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}
