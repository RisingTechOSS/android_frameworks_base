/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.clocks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.Toast;

import com.android.internal.util.crdroid.ThemeUtils;
import com.android.settingslib.drawable.CircleFramedDrawable;

import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.tuner.TunerService;

public class ClockStyle extends RelativeLayout implements TunerService.Tunable {

    private static final int[] CLOCK_LAYOUTS = {
            0,
            R.layout.keyguard_clock_oos,
            R.layout.keyguard_clock_center,
            R.layout.keyguard_clock_cos,
            R.layout.keyguard_clock_custom,
            R.layout.keyguard_clock_miui,
            R.layout.keyguard_clock_ide,
            R.layout.keyguard_clock_hyper,
            R.layout.keyguard_clock_stylish,
            R.layout.keyguard_clock_sidebar,
            R.layout.keyguard_clock_minimal,
            R.layout.keyguard_clock_minimal2
    };

    int[] centerClocks = {2, 4, 8, 9, 10, 11};

    private static final int DEFAULT_STYLE = 0; // Disabled
    private static final String CLOCK_STYLE_KEY = "clock_style";
    private static final String CLOCK_STYLE = "system:" + CLOCK_STYLE_KEY;
    private static final String CUSTOM_AOD_IMAGE_URI_KEY = "system:custom_aod_image_uri";
    private static final String CUSTOM_AOD_IMAGE_ENABLED_KEY = "system:custom_aod_image_enabled";

    private final ThemeUtils mThemeUtils;
    private final Context mContext;
    private final TunerService mTunerService;

    private View currentClockView;
    private int mClockStyle;    

    private static final long UPDATE_INTERVAL_MILLIS = 15 * 1000;
    private long lastUpdateTimeMillis = 0;

    private final StatusBarStateController mStatusBarStateController;

    private boolean mDozing;

    private ImageView mAodImageView;
    private String mImagePath;
    private String mCurrImagePath;
    private boolean mAodImageEnabled;
    private boolean mImageLoaded = false;

    // Burn-in protection
    private static final int BURN_IN_PROTECTION_INTERVAL = 10000; // 10 seconds
    private static final int BURN_IN_PROTECTION_MAX_SHIFT = 4; // 4 pixels
    private final Handler mBurnInProtectionHandler = new Handler();
    private int mCurrentShiftX = 0;
    private int mCurrentShiftY = 0;
    
    private final Runnable mBurnInProtectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDozing) {
                mCurrentShiftX = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                mCurrentShiftY = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                if (mAodImageView != null) {
                    mAodImageView.setTranslationX(mCurrentShiftX);
                    mAodImageView.setTranslationY(mCurrentShiftY);
                }
                if (currentClockView != null) {
                    currentClockView.setTranslationX(mCurrentShiftX);
                    currentClockView.setTranslationY(mCurrentShiftY);
                }
                invalidate();
                mBurnInProtectionHandler.postDelayed(this, BURN_IN_PROTECTION_INTERVAL);
            }
        }
    };

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
        @Override
        public void onStateChanged(int newState) {}

        @Override
        public void onDozingChanged(boolean dozing) {
            if (mDozing == dozing) {
                return;
            }
            mDozing = dozing;
            updateAodImageView();
            if (mDozing) {
                startBurnInProtection();
            } else {
                stopBurnInProtection();
            }
        }
    };

    public ClockStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mThemeUtils = new ThemeUtils(context);
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(this, CLOCK_STYLE, CUSTOM_AOD_IMAGE_URI_KEY, CUSTOM_AOD_IMAGE_ENABLED_KEY);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
    }

    private void updateClockOverlays() {
        mThemeUtils.setOverlayEnabled(
                "android.theme.customization.smartspace",
                mClockStyle != 0 ? "com.android.systemui.hide.smartspace" : "com.android.systemui",
                "com.android.systemui");
        mThemeUtils.setOverlayEnabled(
                "android.theme.customization.smartspace_offset",
                mClockStyle != 0 && isCenterClock(mClockStyle)
                        ? "com.android.systemui.smartspace_offset.smartspace"
                        : "com.android.systemui",
                "com.android.systemui");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAodImageView = findViewById(R.id.custom_aod_image_view);
        updateClockView();
        loadAodImage();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mStatusBarStateController.removeCallback(mStatusBarStateListener);
        mTunerService.removeTunable(this);
        mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
        if (mAodImageView != null) {
            mAodImageView.animate().cancel();
            mAodImageView.setImageDrawable(null);
        }
    }

    private void startBurnInProtection() {
        if (mClockStyle == 0) return;
        mBurnInProtectionHandler.post(mBurnInProtectionRunnable);
    }

    private void stopBurnInProtection() {
        if (mClockStyle == 0) return;
        mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
        if (mAodImageView != null) {
            mAodImageView.setTranslationX(0);
            mAodImageView.setTranslationY(0);
        }
    }

    private void updateTextClockViews(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View childView = viewGroup.getChildAt(i);
                updateTextClockViews(childView);
                if (childView instanceof TextClock) {
                    ((TextClock) childView).refreshTime();
                }
            }
        }
    }

    public void onTimeChanged() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastUpdateTimeMillis >= UPDATE_INTERVAL_MILLIS) {
            if (currentClockView != null) {
                updateTextClockViews(currentClockView);
                lastUpdateTimeMillis = currentTimeMillis;
            }
        }
    }

    private void updateClockView() {
        if (currentClockView != null) {
            ((ViewGroup) currentClockView.getParent()).removeView(currentClockView);
            currentClockView = null;
        }
        if (mClockStyle > 0 && mClockStyle < CLOCK_LAYOUTS.length) {
            ViewStub stub = findViewById(R.id.clock_view_stub);
            if (stub != null) {
                stub.setLayoutResource(CLOCK_LAYOUTS[mClockStyle]);
                currentClockView = stub.inflate();
                int gravity = isCenterClock(mClockStyle) ? Gravity.CENTER : Gravity.START;
                if (currentClockView instanceof LinearLayout) {
                    ((LinearLayout) currentClockView).setGravity(gravity);
                }
            }
        }
        updateClockOverlays();
        onTimeChanged();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case CLOCK_STYLE:
                mClockStyle = TunerService.parseInteger(newValue, DEFAULT_STYLE);
                updateClockView();
                break;
            case CUSTOM_AOD_IMAGE_URI_KEY:
                mImagePath = newValue;
                if (mImagePath != null && !mImagePath.isEmpty() 
                    && !mImagePath.equals(mCurrImagePath)) {
                    mCurrImagePath = mImagePath;
                    mImageLoaded = false;
                    loadAodImage();
                }
                break;
            case CUSTOM_AOD_IMAGE_ENABLED_KEY:
                mAodImageEnabled = TunerService.parseIntegerSwitch(newValue, false);
                break;
        }
    }

    private void updateAodImageView() {
        if (mAodImageView == null || !mAodImageEnabled) {
            if (mAodImageView != null) mAodImageView.setVisibility(View.GONE);
            return;
        }
        loadAodImage();
        if (mDozing) {
            mAodImageView.setVisibility(View.VISIBLE);
            mAodImageView.setScaleX(0f);
            mAodImageView.setScaleY(0f);
            mAodImageView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .withEndAction(this::startBurnInProtection)
                .start();
        } else {
            mAodImageView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(500)
                .withEndAction(() -> {
                    mAodImageView.setVisibility(View.GONE);
                    stopBurnInProtection();
                })
                .start();
        }
    }

    private void loadAodImage() {
        if (mAodImageView == null || mCurrImagePath == null || mCurrImagePath.isEmpty() || mImageLoaded) return;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(mCurrImagePath);
            if (bitmap != null) {
                Drawable roundedImg = new CircleFramedDrawable(bitmap, 
                    (int) mContext.getResources().getDimension(R.dimen.custom_aod_image_size));
                mAodImageView.setImageDrawable(roundedImg);
                bitmap.recycle();
                mImageLoaded = true;
            } else {
                mImageLoaded = false;
                mAodImageView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            mImageLoaded = false;
            mAodImageView.setVisibility(View.GONE);
        }
    }

    private boolean isCenterClock(int clockStyle) {
        for (int centerClock : centerClocks) {
            if (centerClock == clockStyle) {
                return true;
            }
        }
        return false;
    }
}
