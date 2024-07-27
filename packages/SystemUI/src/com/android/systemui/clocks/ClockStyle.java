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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;

import com.android.internal.util.crdroid.ThemeUtils;

import com.android.settingslib.drawable.CircleFramedDrawable;

import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.tuner.TunerService;

import java.io.FileNotFoundException;
import java.io.InputStream;

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
    private static final String CUSTOM_AOD_IMAGE_URI_KEY = "custom_aod_image_uri";

    private ThemeUtils mThemeUtils;
    private Context mContext;
    private View currentClockView;
    private int mClockStyle;

    private static final long UPDATE_INTERVAL_MILLIS = 15 * 1000;
    private final Handler mHandler;
    private long lastUpdateTimeMillis = 0;

    private final StatusBarStateController mStatusBarStateController;

    private boolean mDozing;

    private ImageView customImageView;
    
    // Burn-in protection
    private static final int BURN_IN_PROTECTION_INTERVAL = 60000; // 60 seconds
    private static final int BURN_IN_PROTECTION_MAX_SHIFT = 10; // 10 pixels
    private final Handler mBurnInProtectionHandler = new Handler();
    private int mCurrentShiftX = 0;
    private int mCurrentShiftY = 0;
    
    private final Runnable mBurnInProtectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (customImageView != null && mDozing) {
                mCurrentShiftX = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                mCurrentShiftY = (int) (Math.random() * BURN_IN_PROTECTION_MAX_SHIFT * 2) - BURN_IN_PROTECTION_MAX_SHIFT;
                customImageView.setTranslationX(mCurrentShiftX);
                customImageView.setTranslationY(mCurrentShiftY);
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
            updateCustomImageView();
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
        mHandler = Dependency.get(Dependency.MAIN_HANDLER);
        mThemeUtils = new ThemeUtils(context);
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE);
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
        customImageView = findViewById(R.id.custom_aod_image_view);
        updateClockView();
        updateCustomImageView();
    }

    private void startBurnInProtection() {
        mBurnInProtectionHandler.post(mBurnInProtectionRunnable);
    }

    private void stopBurnInProtection() {
        mBurnInProtectionHandler.removeCallbacks(mBurnInProtectionRunnable);
        if (customImageView != null) {
            customImageView.setTranslationX(0);
            customImageView.setTranslationY(0);
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
    }

    private void updateCustomImageView() {
        if (customImageView == null) return;
        boolean customAodImageEnabled = Settings.System.getIntForUser(mContext.getContentResolver(), 
            "custom_aod_image_enabled", 0, UserHandle.USER_CURRENT) != 0;
        if (!customAodImageEnabled) {
            if (customImageView.getVisibility() != View.GONE) {
                customImageView.setVisibility(View.GONE);
            }
            return;
        }
        String imagePath = Settings.System.getString(mContext.getContentResolver(), CUSTOM_AOD_IMAGE_URI_KEY);
        if (imagePath != null && mDozing) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                int maxSize = (int) mContext.getResources().getDimension(R.dimen.custom_aod_image_size);
                Drawable roundedImg = new CircleFramedDrawable(bitmap, maxSize);
                customImageView.setImageDrawable(roundedImg);
                bitmap.recycle();
                customImageView.setVisibility(View.VISIBLE);
                customImageView.setAlpha(0f);
                customImageView.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .withEndAction(this::startBurnInProtection)
                    .start();
            } catch (Exception e) {
                Log.e("Custom AOD image", "Error loading image", e);
            }
        } else {
            customImageView.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> {
                    customImageView.setVisibility(View.GONE);
                    stopBurnInProtection();
                })
                .start();
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

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (CLOCK_STYLE.equals(key)) {
            mClockStyle = TunerService.parseInteger(newValue, 0);
            updateClockView();
        }
    }
}
