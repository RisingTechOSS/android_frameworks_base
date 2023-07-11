/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings.Global
import android.service.quicksettings.Tile
import android.view.View
import androidx.annotation.Nullable
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.Dependency
import com.android.systemui.qs.QSHost
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.R
import com.android.systemui.statusbar.policy.ZenModeController
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.logging.QSLogger
import javax.inject.Inject

class SoundTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
) : QSTileImpl<BooleanState>(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger) {

    companion object {
        const val TILE_SPEC = "sound"
    }

    private val mZenController: ZenModeController = Dependency.get(ZenModeController::class.java)
    private val mAudioManager: AudioManager by lazy {
        mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var mListening = false
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshState()
        }
    }

    override fun newTileState(): BooleanState {
        return BooleanState()
    }

    override fun handleSetListening(listening: Boolean) {
        if (mAudioManager == null) {
            return
        }
        if (mListening == listening) return
        mListening = listening
        if (listening) {
            val filter = IntentFilter().apply {
                addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)
            }
            mContext.registerReceiver(mReceiver, filter)
        } else {
            mContext.unregisterReceiver(mReceiver)
        }
    }

    override protected fun handleClick(@Nullable view: View?) {
        updateState()
    }

    private fun updateState() {
        val oldState = mAudioManager.ringerModeInternal
        var newState = oldState
        when (oldState) {
            AudioManager.RINGER_MODE_NORMAL -> {
                newState = AudioManager.RINGER_MODE_VIBRATE
                mAudioManager.ringerModeInternal = newState
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                newState = AudioManager.RINGER_MODE_SILENT
                mZenController.setZen(Global.ZEN_MODE_ALARMS, null, TAG)
                mAudioManager.ringerModeInternal = newState
            }
            AudioManager.RINGER_MODE_SILENT -> {
                newState = AudioManager.RINGER_MODE_NORMAL
                mZenController.setZen(Global.ZEN_MODE_OFF, null, TAG)
                mAudioManager.ringerModeInternal = newState
            }
        }
    }

    override fun handleLongClick(@Nullable view: View?) {
        mAudioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_sound_label)
    }

    override protected fun handleUpdateState(state: BooleanState, arg: Any?) {
        when (mAudioManager.ringerModeInternal) {
            AudioManager.RINGER_MODE_NORMAL -> {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_ringer_audible)
                state.label = mContext.getString(R.string.quick_settings_sound_ring)
                state.contentDescription = mContext.getString(R.string.quick_settings_sound_ring)
                state.state = Tile.STATE_ACTIVE
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_ringer_vibrate)
                state.label = mContext.getString(R.string.quick_settings_sound_vibrate)
                state.contentDescription = mContext.getString(R.string.quick_settings_sound_vibrate)
                state.state = Tile.STATE_ACTIVE
            }
            AudioManager.RINGER_MODE_SILENT -> {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_ringer_silent)
                state.label = mContext.getString(R.string.quick_settings_sound_dnd)
                state.contentDescription = mContext.getString(R.string.quick_settings_sound_dnd)
                state.state = Tile.STATE_ACTIVE
            }
            else -> return
        }
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.CUSTOM_SETTINGS
    }
}
