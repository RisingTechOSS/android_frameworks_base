/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs

import android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.provider.Settings
import android.os.UserHandle
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView

import com.android.internal.graphics.ColorUtils
import com.android.systemui.Dependency
import com.android.systemui.R
import com.android.systemui.util.LargeScreenUtils
import com.android.systemui.tuner.TunerService

class QuickStatusBarHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), TunerService.Tunable {

    private var mExpanded = false
    private var mQsDisabled = false

    private var mHeaderImageEnabled = false
    private var mHeaderImageValue = 0
    private var mHeaderImageFadeLevel = 40
    private var mHeaderImageOpacityLevel = 30
    private var mHeaderImageHeight = 80
    private var mHeaderImageFilterColor = -1
    private var mCurrentOrientation = 0
    private var mHeaderCustomImageEnabled = false

    private val tunerService = Dependency.get(TunerService::class.java)
    private val mHeaderImageResources = HashMap<Int, Int>()

    private lateinit var mHeaderQsPanel: QuickQSPanel
    private lateinit var mQsHeaderImageView: ImageView
    private lateinit var mQsHeaderLayout: View
    private lateinit var mQsHeaderGradientView: View

    init {
        mHeaderImageResources.clear()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        mHeaderQsPanel = findViewById(R.id.quick_qs_panel)
        mQsHeaderLayout = findViewById(R.id.layout_header)
        mQsHeaderImageView = findViewById(R.id.qs_header_image_view)
        mQsHeaderGradientView = findViewById(R.id.qs_header_gradient_view)
        mQsHeaderImageView?.clipToOutline = true

        tunerService.addTunable(this, QS_HEADER_IMAGE)
        tunerService.addTunable(this, QS_HEADER_IMAGE_FADE_LEVEL)
        tunerService.addTunable(this, QS_HEADER_IMAGE_OPACITY_LEVEL)
        tunerService.addTunable(this, QS_HEADER_IMAGE_HEIGHT)
        tunerService.addTunable(this, QS_HEADER_IMAGE_FILTER_COLOR)
        tunerService.addTunable(this, QS_HEADER_CUSTOM_IMAGE_URI_ENABLED)
        tunerService.addTunable(this, QS_HEADER_CUSTOM_IMAGE_URI)

        mCurrentOrientation = resources.configuration.orientation
        updateResources()
    }

    override fun onTuningChanged(key: String?, newValue: String?) {
        when (key) {
            QS_HEADER_IMAGE -> {
                mHeaderImageValue = TunerService.parseInteger(newValue, 0)
                mHeaderImageEnabled = mHeaderImageValue != 0
                updateQSHeaderImage()
            }
            QS_HEADER_IMAGE_FADE_LEVEL -> {
                mHeaderImageFadeLevel = TunerService.parseInteger(newValue, 40)
                updateQSHeaderImage()
            }
            QS_HEADER_IMAGE_OPACITY_LEVEL -> {
                mHeaderImageOpacityLevel = TunerService.parseInteger(newValue, 30)
                updateQSHeaderImage()
            }
            QS_HEADER_IMAGE_HEIGHT -> {
                mHeaderImageHeight = TunerService.parseInteger(newValue, 200)
                updateQSHeaderImage()
            }
            QS_HEADER_IMAGE_FILTER_COLOR -> {
                mHeaderImageFilterColor = TunerService.parseInteger(newValue, -1)
                updateQSHeaderImage()
            }
            QS_HEADER_CUSTOM_IMAGE_URI_ENABLED -> {
                mHeaderCustomImageEnabled = TunerService.parseIntegerSwitch(newValue, false)
                updateQSHeaderImage()
            }
            QS_HEADER_CUSTOM_IMAGE_URI -> {
                updateQSHeaderImage()
            }
        }
    }

    private fun updateQSHeaderImage() {
        if (mQsHeaderLayout == null || mQsHeaderImageView == null) {
            return
        }
        if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE || !mHeaderImageEnabled && !mHeaderCustomImageEnabled) {
            mQsHeaderImageView.visibility = View.GONE
            mQsHeaderGradientView.visibility = View.GONE
            return
        }

        mQsHeaderImageView.visibility = View.VISIBLE
        mQsHeaderGradientView.visibility = View.VISIBLE

        val fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, mHeaderImageFilterColor, mHeaderImageFadeLevel / 100f)
        mQsHeaderImageView.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP)

        if (mHeaderCustomImageEnabled) {
            val customImagePath = Settings.System.getStringForUser(
                context.contentResolver,
                Settings.System.QS_HEADER_CUSTOM_IMAGE_URI,
                UserHandle.USER_CURRENT
            )
            val bitmap = BitmapFactory.decodeFile(customImagePath)
            mQsHeaderImageView.setImageBitmap(bitmap ?: return)
        } else {
            val resourceId = mHeaderImageResources.getOrPut(mHeaderImageValue) {
                resources.getIdentifier(
                    "qs_header_image_$mHeaderImageValue", "drawable", "com.android.systemui"
                )
            }
            mQsHeaderImageView.setImageResource(resourceId)
        }
        mQsHeaderImageView.imageAlpha = (255 * mHeaderImageOpacityLevel / 100)

        val qsGradientViewlayout = mQsHeaderGradientView.layoutParams as ViewGroup.MarginLayoutParams
        qsGradientViewlayout.height = dpToPx(mHeaderImageHeight).toInt()
        mQsHeaderGradientView.layoutParams = qsGradientViewlayout

        val qsHeaderLayout = mQsHeaderLayout.layoutParams as ViewGroup.MarginLayoutParams
        qsHeaderLayout.height = dpToPx(mHeaderImageHeight).toInt()
        mQsHeaderLayout.layoutParams = qsHeaderLayout
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mCurrentOrientation = newConfig.orientation
        updateResources()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return event.y > mHeaderQsPanel.top && super.onTouchEvent(event)
    }

    fun updateResources() {
        if (mHeaderQsPanel == null) return
        val resources = context.resources
        val largeScreenHeaderActive = LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources)

        updateQSHeaderImage()

        val lp = layoutParams
        lp.height = if (mQsDisabled) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams = lp

        val qqsLP = mHeaderQsPanel.layoutParams as ViewGroup.MarginLayoutParams
        qqsLP.topMargin = if (largeScreenHeaderActive)
            context.resources.getDimensionPixelSize(R.dimen.qqs_layout_margin_top)
        else
            context.resources.getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height)
        mHeaderQsPanel.layoutParams = qqsLP
    }

    fun setExpanded(expanded: Boolean, quickQSPanelController: QuickQSPanelController) {
        if (quickQSPanelController == null || mExpanded == expanded) return
        mExpanded = expanded
        quickQSPanelController.setExpanded(expanded)
    }

    fun disable(state1: Int, state2: Int, animate: Boolean) {
        val disabled = state2 and DISABLE2_QUICK_SETTINGS != 0
        if (mHeaderQsPanel == null || disabled == mQsDisabled) return
        mQsDisabled = disabled
        mHeaderQsPanel.setDisabledByPolicy(disabled)
        updateResources()
    }

    private fun setContentMargins(view: View, marginStart: Int, marginEnd: Int) {
        if (view != null) {
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart = marginStart
            lp.marginEnd = marginEnd
            view.layoutParams = lp
        }
    }

    companion object {
        private const val QS_HEADER_IMAGE = "system:" + Settings.System.QS_HEADER_IMAGE
        private const val QS_HEADER_IMAGE_FADE_LEVEL = "system:" + Settings.System.QS_HEADER_IMAGE_FADE_LEVEL
        private const val QS_HEADER_IMAGE_OPACITY_LEVEL = "system:" + Settings.System.QS_HEADER_IMAGE_OPACITY_LEVEL
        private const val QS_HEADER_IMAGE_HEIGHT = "system:" + Settings.System.QS_HEADER_IMAGE_HEIGHT
        private const val QS_HEADER_IMAGE_FILTER_COLOR = "system:" + Settings.System.QS_HEADER_IMAGE_FILTER_COLOR
        private const val QS_HEADER_CUSTOM_IMAGE_URI_ENABLED = "system:" + Settings.System.QS_HEADER_CUSTOM_IMAGE_URI_ENABLED
        private const val QS_HEADER_CUSTOM_IMAGE_URI = "system:" + Settings.System.QS_HEADER_CUSTOM_IMAGE_URI
    }
}
