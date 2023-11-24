/*
 * Copyright (C) 2018 The OmniROM Project
 *               2020-2021 The LineageOS Project
 *               2023 The risingOS Android Project
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

package com.android.systemui.qs.tiles

import android.content.Intent
import android.database.ContentObserver
import android.hardware.display.AmbientDisplayConfiguration
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile
import android.view.View
import androidx.annotation.Nullable
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject

class AODTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main private val mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val secureSettings: SecureSettings,
    private val batteryController: BatteryController
) :QSTileImpl<QSTile.State?>(
        host,
        uiEventLogger,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger
    ), BatteryController.BatteryStateChangeCallback {

    companion object {
        const val TILE_SPEC = "aod"
        private val LS_DISPLAY_SETTINGS = Intent("android.settings.LOCK_SCREEN_SETTINGS")
    }
    
    private val sIcon = ResourceIcon.get(R.drawable.ic_qs_aod)

    private val observer = object : ContentObserver(Handler(mainHandler.looper)) {
        override fun onChange(selfChange: Boolean) {
            refreshState()
        }
    }

    private val config = AmbientDisplayConfiguration(mContext)

    private var isListening = false

    private enum class DozeState(val dozeAlwaysOnValue: Int, val dozeOnChargeValue: Int) {
        OFF(0, 0),
        ALWAYS_ON_CHARGE(0, 1),
        ALWAYS_ON(1, 0)
    }

    init {
        batteryController.observe(lifecycle, this)
    }

    override fun onPowerSaveChanged(isPowerSave: Boolean) {
        refreshState()
    }

    private fun getDozeState(): DozeState {
        val alwaysOn = secureSettings.getIntForUser(Settings.Secure.DOZE_ALWAYS_ON, 0, UserHandle.USER_CURRENT) == 1
        return if (alwaysOn) DozeState.ALWAYS_ON
        else {
            val alwaysOnCharge = secureSettings.getIntForUser(Settings.Secure.DOZE_ON_CHARGE, 0, UserHandle.USER_CURRENT) == 1
            if (alwaysOnCharge) DozeState.ALWAYS_ON_CHARGE
            else DozeState.OFF
        }
    }

    override fun isAvailable(): Boolean {
        return config.alwaysOnAvailable()
    }

    override fun newTileState(): QSTile.State {
        return QSTile.State().apply {
            icon = sIcon
        }
    }

    override fun handleClick(@Nullable view: View?) {
        val newState = when (getDozeState()) {
            DozeState.OFF -> DozeState.ALWAYS_ON_CHARGE
            DozeState.ALWAYS_ON_CHARGE -> DozeState.ALWAYS_ON
            DozeState.ALWAYS_ON -> DozeState.OFF
        }
        secureSettings.putIntForUser(Settings.Secure.DOZE_ALWAYS_ON, newState.dozeAlwaysOnValue, UserHandle.USER_CURRENT)
        secureSettings.putIntForUser(Settings.Secure.DOZE_ON_CHARGE, newState.dozeOnChargeValue, UserHandle.USER_CURRENT)
    }

    override fun getLongClickIntent(): Intent {
        return LS_DISPLAY_SETTINGS
    }

    override fun getTileLabel(): CharSequence {
        return when {
            batteryController.isAodPowerSave -> mContext.getString(R.string.quick_settings_aod_off_powersave_label)
            getDozeState() == DozeState.ALWAYS_ON_CHARGE -> mContext.getString(R.string.quick_settings_aod_on_charge_label)
            getDozeState() == DozeState.ALWAYS_ON -> mContext.getString(R.string.quick_settings_aod_label)
            else -> mContext.getString(R.string.quick_settings_aod_off_label)
        }
    }

    override fun handleUpdateState(state: QSTile.State?, arg: Any?) {
        state?.label = tileLabel
        state?.icon = sIcon
        state?.state = when {
            batteryController.isAodPowerSave -> Tile.STATE_UNAVAILABLE
            getDozeState() == DozeState.OFF -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.VIEW_UNKNOWN
    }

    override fun handleSetListening(listening: Boolean) {
        if (isListening == listening) return
        isListening = listening
        if (isListening) {
            secureSettings.registerContentObserverForUser(Settings.Secure.DOZE_ALWAYS_ON, observer, UserHandle.USER_ALL)
            secureSettings.registerContentObserverForUser(Settings.Secure.DOZE_ON_CHARGE, observer, UserHandle.USER_ALL)
        } else {
            secureSettings.unregisterContentObserver(observer)
        }
    }
}
