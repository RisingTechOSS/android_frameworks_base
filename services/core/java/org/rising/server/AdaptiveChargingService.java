/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
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
package org.rising.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.android.server.SystemService;

public class AdaptiveChargingService extends SystemService {

    private static final String TAG = "AdaptiveChargingService";

    private PowerManager mPowerManager;
    private boolean mAdaptiveChargingEnabled = false;
    private boolean mLastChargingMode = false;
    private Context mContext;
    private Intent mLastBatteryStatusIntent;

    public AdaptiveChargingService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor("adaptive_charging_enabled"),
                false,
                mAdaptiveChargingObserver
        );
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mLastBatteryStatusIntent = mContext.registerReceiver(mBatteryReceiver, filter);
    }

    private final ContentObserver mAdaptiveChargingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (mLastBatteryStatusIntent != null) {
                updateAdaptiveChargingStatus(mLastBatteryStatusIntent);
            }
        }
    };

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLastBatteryStatusIntent = intent;
            updateAdaptiveChargingStatus(intent);
        }
    };

    private void updateAdaptiveChargingStatus(Intent intent) {
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC || 
                            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
        mAdaptiveChargingEnabled = Settings.System.getInt(mContext.getContentResolver(),
                "adaptive_charging_enabled", 0) == 1;
        boolean shouldEnableAdaptiveCharging = (isCharging || isPlugged) && mAdaptiveChargingEnabled;
        if (mPowerManager != null && mLastChargingMode != shouldEnableAdaptiveCharging) {
            mPowerManager.setAdaptivePowerSaveEnabled(shouldEnableAdaptiveCharging);
            mLastChargingMode = shouldEnableAdaptiveCharging;
            Log.d(TAG, "Adaptive Charging status updated: " + shouldEnableAdaptiveCharging);
        }
    }
}
