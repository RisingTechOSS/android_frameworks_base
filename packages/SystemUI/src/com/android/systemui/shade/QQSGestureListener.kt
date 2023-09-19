/*
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.systemui.shade

import android.content.Context
import android.content.res.Resources
import android.os.PowerManager
import android.os.AsyncTask
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.GestureDetector
import android.view.MotionEvent
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.phone.dagger.CentralSurfacesComponent
import com.android.systemui.tuner.TunerService
import com.android.systemui.tuner.TunerService.Tunable

import lineageos.providers.LineageSettings

import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@CentralSurfacesComponent.CentralSurfacesScope
class QQSGestureListener @Inject constructor(
    private val context: Context,
    private val falsingManager: FalsingManager,
    private val powerManager: PowerManager,
    private val statusBarStateController: StatusBarStateController,
    tunerService: TunerService,
    @Main resources: Resources
) : GestureDetector.SimpleOnGestureListener(), CoroutineScope by CoroutineScope(Dispatchers.Main) {

    companion object {
        val DOUBLE_TAP_SLEEP_GESTURE =
            "lineagesystem:${LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE}"
    }

    private var doubleTapToSleepEnabled = false
    private val quickQsOffsetHeight = resources.getDimensionPixelSize(
        com.android.internal.R.dimen.quick_qs_offset_height
    )
    private val vibratorHelper = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val clickVibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)

    init {
        val tunable = Tunable { key: String?, value: String? ->
            if (key == DOUBLE_TAP_SLEEP_GESTURE) {
                doubleTapToSleepEnabled = TunerService.parseIntegerSwitch(value, true)
            }
        }
        tunerService.addTunable(tunable, DOUBLE_TAP_SLEEP_GESTURE)
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        if (shouldGoToSleep(e)) {
            triggerVibration {
                powerManager.goToSleep(e.eventTime)
            }
            return true
        }
        return false
    }

    private fun shouldGoToSleep(e: MotionEvent) = with(e) {
        actionMasked == MotionEvent.ACTION_UP &&
        !statusBarStateController.isDozing &&
        doubleTapToSleepEnabled &&
        y < quickQsOffsetHeight &&
        !falsingManager.isFalseDoubleTap
    }

    private fun triggerVibration(callback: () -> Unit) {
        vibratorHelper?.let {
            AsyncTask.execute {
                it.vibrate(clickVibrationEffect)
                callback()
            }
        }
    }
}
