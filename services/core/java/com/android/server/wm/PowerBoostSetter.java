/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wm;

import android.os.PowerManagerInternal;
import android.util.Log;

import com.android.server.LocalServices;

import java.time.Instant;

/**
 * Sends power boost events to the power manager.
 */
public class PowerBoostSetter {
    private static final String TAG = "PowerBoostSetter";
    // Set power boost timeout to 0.2 seconds
    private static final int POWER_BOOST_TIMEOUT_MS = 200;

    PowerManagerInternal mPowerManagerInternal = null;
    Instant mPreviousTimeout = null;

    PowerBoostSetter() {
        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
    }

    /**
     * Boosts the CPU clock frequency as if the screen is touched
     */
    public void boostPower() {
        if (mPowerManagerInternal == null) {
            mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
        }

        if (mPowerManagerInternal == null) {
            Log.w(TAG, "PowerManagerInternal null");
        } else if ((mPreviousTimeout == null) || Instant.now().isAfter(
                mPreviousTimeout.plusMillis(POWER_BOOST_TIMEOUT_MS / 2))) {
            // Only boost if the previous timeout is at least halfway done
            mPreviousTimeout = Instant.now();
            mPowerManagerInternal.setPowerBoost(PowerManagerInternal.BOOST_DISPLAY_UPDATE_IMMINENT,
                    POWER_BOOST_TIMEOUT_MS);
        }
    }
}
