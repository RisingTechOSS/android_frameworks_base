/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.internal.graphics.ColorUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.util.LargeScreenUtils;
import com.android.systemui.tuner.TunerService;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout implements TunerService.Tunable {

    private boolean mExpanded;
    private boolean mQsDisabled;

    private static final String QS_HEADER_IMAGE =
            "system:" + Settings.System.QS_HEADER_IMAGE;

    protected QuickQSPanel mHeaderQsPanel;

    // QS Header
    private ImageView mQsHeaderImageView;
    private View mQsHeaderLayout;
    private boolean mHeaderImageEnabled;
    private int mHeaderImageValue;

    private int mCurrentOrientation;
    private SparseArray<Integer> mHeaderImageResources;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHeaderImageResources = new SparseArray<>();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);

        mQsHeaderLayout = findViewById(R.id.layout_header);
        mQsHeaderImageView = findViewById(R.id.qs_header_image_view);
        mQsHeaderImageView.setClipToOutline(true);

        Dependency.get(TunerService.class).addTunable(this, QS_HEADER_IMAGE);
        
        mCurrentOrientation = getResources().getConfiguration().orientation;

        updateResources();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case QS_HEADER_IMAGE:
                mHeaderImageValue = TunerService.parseInteger(newValue, 0);
                mHeaderImageEnabled = mHeaderImageValue != 0;
                updateQSHeaderImage();
                break;
            default:
                break;
        }
    }

    private void updateQSHeaderImage() {
        if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE || !mHeaderImageEnabled) {
            mQsHeaderImageView.setVisibility(View.GONE);
            return;
        }
        int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, Color.BLACK, 30 / 100f);
        mQsHeaderImageView.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
        mQsHeaderImageView.setVisibility(View.VISIBLE);
        Integer resourceId = mHeaderImageResources.get(mHeaderImageValue);
        if (resourceId == null) {
            resourceId = getResources().getIdentifier(
                    "qs_header_image_" + mHeaderImageValue, "drawable", "com.android.systemui");
            mHeaderImageResources.put(mHeaderImageValue, resourceId);
        }
        mQsHeaderImageView.setImageResource(resourceId);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCurrentOrientation = newConfig.orientation;
        updateResources();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only react to touches inside QuickQSPanel
        return event.getY() > mHeaderQsPanel.getTop() && super.onTouchEvent(event);
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);
        
        updateQSHeaderImage();

        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = mQsDisabled ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        setLayoutParams(lp);

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        qqsLP.topMargin = largeScreenHeaderActive
                ? mContext.getResources().getDimensionPixelSize(R.dimen.qqs_layout_margin_top)
                : mContext.getResources().getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height);
        mHeaderQsPanel.setLayoutParams(qqsLP);
    }

    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        updateResources();
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }
}
