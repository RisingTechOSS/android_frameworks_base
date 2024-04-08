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
import android.provider.Settings;

import com.android.server.LocalServices;
import android.os.PowerManager;

import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;

public class SystemUtils {

    private final String ADAPTIVE_CHARGING_ENABLED = "system:adaptive_charging_enabled";
    
    private final PowerManager mPowerManager;
    private final TunerService mTunerService;

    private boolean mAdaptiveChargingEnabled = false;
    private boolean mLastChargingMode = false;
    
    public SystemUtils (Context context) {
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(mTunable, ADAPTIVE_CHARGING_ENABLED);
    }

    public void setAdaptiveChargingStatus(boolean enabled) {
        boolean chargingMode = enabled && mAdaptiveChargingEnabled;
        if (mPowerManager != null && mLastChargingMode != chargingMode) {
            mPowerManager.setAdaptivePowerSaveEnabled(chargingMode);
            mLastChargingMode = chargingMode;
        }
    }
    
    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case ADAPTIVE_CHARGING_ENABLED:
                    mAdaptiveChargingEnabled = TunerService.parseIntegerSwitch(newValue, false);
                    break;
                default:
                    break;
            }
        }
    };
}
