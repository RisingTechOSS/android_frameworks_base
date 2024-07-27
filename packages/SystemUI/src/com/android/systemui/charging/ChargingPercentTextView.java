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
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.widget.TextView;

public class ChargingPercentTextView extends TextView {

    private static final int UPDATE_INTERVAL_MS = 2000; // Update every 2 seconds

    private Context mContext;
    private int lastDisplayedLevel = -1;
    private Handler mBackgroundHandler;
    private Handler mMainHandler;
    private Runnable mBatteryUpdater;

    public ChargingPercentTextView(Context context) {
        super(context);
        init(context);
    }

    public ChargingPercentTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChargingPercentTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startBatteryUpdates();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopBatteryUpdates();
    }

    private void init(Context context) {
        mContext = context;

        HandlerThread handlerThread = new HandlerThread("ChargingPercentTextViewThread");
        handlerThread.start();
        mBackgroundHandler = new Handler(handlerThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());

        mBatteryUpdater = new Runnable() {
            @Override
            public void run() {
                updateBatteryLevel();
                mBackgroundHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
    }

    private void startBatteryUpdates() {
        mBatteryUpdater.run();
    }

    private void stopBatteryUpdates() {
        mBackgroundHandler.removeCallbacks(mBatteryUpdater);
        mBackgroundHandler.getLooper().quit();
    }

    private void updateBatteryLevel() {
        int currLvl = getBatteryLevel(mContext);
        if (currLvl != lastDisplayedLevel) {
            lastDisplayedLevel = currLvl;
            final String batteryText = currLvl + "%";
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    setText(batteryText);
                }
            });
        }
    }

    private int getBatteryLevel(Context context) {
        final BatteryManager batteryManager = (BatteryManager) context.getSystemService(BatteryManager.class);
        if (batteryManager != null) {
            return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            return 0;
        }
    }
}
