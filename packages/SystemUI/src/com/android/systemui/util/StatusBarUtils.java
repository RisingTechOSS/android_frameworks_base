/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.util;

import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.TypedValue;

import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;

public class StatusBarUtils {

    public static final String LEFT_PADDING = "system:" + "statusbar_left_padding";
    public static final String RIGHT_PADDING = "system:" + "statusbar_right_padding";
    public static final String TOP_PADDING = "system:" + "statusbar_top_padding";
    public static final String DEFAULT = "_default";

    private int mLeftPad;
    private int mRightPad;
    private int mTopPad;
    
    private Context mContext;
    private Resources mRes;

    private LayoutChangeListener mListener;
    private TunerService mTunerService;

    public interface LayoutChangeListener {
        void onLayoutChanged(int leftPadding, int rightPadding, int topPadding);
    }

    private static StatusBarUtils sInstance;
    
    private StatusBarUtils(Context context) {
        mContext = context;
        mRes = mContext.getResources();

        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(mTunable, LEFT_PADDING, RIGHT_PADDING, TOP_PADDING);

        String leftPaddingKey = LEFT_PADDING.substring(7);
        String rightPaddingKey = RIGHT_PADDING.substring(7);
        String topPaddingKey = TOP_PADDING.substring(7);
    }

    public static synchronized StatusBarUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new StatusBarUtils(context.getApplicationContext());
        }
        return sInstance;
    }

    public void setLayoutChangeListener(LayoutChangeListener listener) {
        mListener = listener;
        if (mListener != null) {
            mListener.onLayoutChanged(mLeftPad, mRightPad, mTopPad);
        }
    }

    public int getLeftPadding() {
        return mLeftPad;
    }

    public int getRightPadding() {
        return mRightPad;
    }

    public int getTopPadding() {
        return mTopPad;
    }

    public void updatePadding(String key, float newValue) {
        int padding = Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        newValue,
                        mRes.getDisplayMetrics()));
        switch (key) {
            case LEFT_PADDING:
                mLeftPad = padding;
                break;
            case RIGHT_PADDING:
                mRightPad = padding;
                break;
            case TOP_PADDING:
                mTopPad = padding;
                break;
        }
    }

    public int getDefaultLeftPadding() {
        return mRes.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_padding_start);
    }

    public int getDefaultRightPadding() {
        return mRes.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_padding_end);
    }

    public int getDefaultTopPadding() {
        return mRes.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_padding_top);
    }
    
    private void notifyChange() {
        if (mListener != null) {
            mListener.onLayoutChanged(mLeftPad, mRightPad, mTopPad);
        }
    }

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case LEFT_PADDING:
                    float leftPadding = (float) TunerService.parseInteger(newValue, getDefaultLeftPadding());
                    updatePadding(key, leftPadding);
                    notifyChange();
                    break;
                case RIGHT_PADDING:
                    float rightPadding = (float) TunerService.parseInteger(newValue, getDefaultRightPadding());
                    updatePadding(key, rightPadding);
                    notifyChange();
                    break;
                case TOP_PADDING:
                    float topPadding = (float) TunerService.parseInteger(newValue, getDefaultTopPadding());
                    updatePadding(key, topPadding);
                    notifyChange();
                    break;
                default:
                    break;
            }
        }
    };
}

