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
package com.android.systemui.charging;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class BatteryProgressBar extends ProgressBar {

    private static final int UPDATE_INTERVAL_MS = 2000; // Update every 2 seconds

    private int batteryLevel;
    private Handler mBackgroundHandler;
    private Handler mMainHandler;
    private Runnable mBatteryLevelUpdater;

    public BatteryProgressBar(Context context) {
        super(context);
        init(context);
    }

    public BatteryProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BatteryProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startBatteryLevelUpdates();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopBatteryLevelUpdates();
    }

    private void init(Context context) {
        batteryLevel = getBatteryLevel(context);
        setProgress(batteryLevel);

        HandlerThread handlerThread = new HandlerThread("BatteryProgressBarThread");
        handlerThread.start();
        mBackgroundHandler = new Handler(handlerThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());

        mBatteryLevelUpdater = new Runnable() {
            @Override
            public void run() {
                updateBatteryLevel(context);
                mBackgroundHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
    }

    private void startBatteryLevelUpdates() {
        mBatteryLevelUpdater.run();
    }

    private void stopBatteryLevelUpdates() {
        mBackgroundHandler.removeCallbacks(mBatteryLevelUpdater);
        mBackgroundHandler.getLooper().quit();
    }

    private void updateBatteryLevel(Context context) {
        final int currentLevel = getBatteryLevel(context);
        if (batteryLevel != currentLevel) {
            batteryLevel = currentLevel;
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    setProgress(currentLevel);
                }
            });
        }
    }

    private int getBatteryLevel(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            return 0;
        }
    }
}
