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
package com.android.systemui.power

import android.os.PowerManagerInternal
import android.util.Log
import com.android.server.LocalServices
import java.time.Instant

/**
 * Sends power boost events to the power manager.
 */
class SysUIPowerBoostSetter {

    companion object {
        private const val TAG = "SysUIPowerBoostSetter"
        // Set power boost timeout to 2 seconds
        private const val POWER_BOOST_TIMEOUT_MS = 2000L
    }

    private var powerManagerInternal: PowerManagerInternal? = LocalServices.getService(PowerManagerInternal::class.java)
    private var previousTimeout: Instant? = null

    /**
     * Boosts the CPU clock frequency as if the screen is touched
     */
    fun boostPower() {
        powerManagerInternal = powerManagerInternal ?: LocalServices.getService(PowerManagerInternal::class.java)
        if (powerManagerInternal == null) {
            Log.w(TAG, "PowerManagerInternal null")
        } else if (previousTimeout == null || Instant.now().isAfter(previousTimeout!!.plusMillis(POWER_BOOST_TIMEOUT_MS / 2))) {
            // Only boost if the previous timeout is at least halfway done
            previousTimeout = Instant.now()
            powerManagerInternal!!.setPowerBoost(PowerManagerInternal.BOOST_DISPLAY_UPDATE_IMMINENT, POWER_BOOST_TIMEOUT_MS.toInt())
        }
    }
}
