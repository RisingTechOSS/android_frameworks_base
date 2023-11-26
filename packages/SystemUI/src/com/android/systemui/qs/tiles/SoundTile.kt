/*
 * Copyright (C) 2023 The risingOS Android Project
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
import android.media.MediaPlayer
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.service.quicksettings.Tile
import android.view.View
import androidx.annotation.Nullable
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import javax.inject.Inject

class SoundTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
) : QSTileImpl<BooleanState>(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger, statusBarStateController, activityStarter, qsLogger) {

    companion object {
        const val TILE_SPEC = "sound"
    }

    private val effectClick: VibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)

    private var mListening = false
    private val mAudioManager: AudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val intentFilter = IntentFilter(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)

    private val mVibrator: Vibrator? = mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val mediaPlayer: MediaPlayer? = MediaPlayer.create(mContext, R.raw.volume_control_sound)

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshState()
        }
    }

    override fun newTileState() = BooleanState()

    override fun handleSetListening(listening: Boolean) {
        if (mListening == listening) return
        mListening = listening
        if (listening) {
            mContext.registerReceiver(mReceiver, intentFilter)
        } else {
            mContext.unregisterReceiver(mReceiver)
        }
    }

    override fun handleClick(@Nullable view: View?) {
        val currentRingerMode = mAudioManager.ringerModeInternal
        val newState = when (currentRingerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> return
        }
        if (newState == AudioManager.RINGER_MODE_NORMAL) {
            playSound()
        } else if (newState == AudioManager.RINGER_MODE_VIBRATE) {
            AsyncTask.execute { mVibrator?.vibrate(effectClick) }
        }
        mAudioManager.ringerModeInternal = newState
    }

    private fun playSound() {
        if (mAudioManager.isMusicActive) {
            return
        }
        try {
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
        } catch (e: Exception) {}
    }

    override fun getLongClickIntent() = Intent(Settings.Panel.ACTION_VOLUME)

    override fun getTileLabel() = mContext.getString(R.string.quick_settings_sound_label)

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        state.label = getTileLabel()
        state.icon = when (mAudioManager.ringerModeInternal) {
            AudioManager.RINGER_MODE_NORMAL -> ResourceIcon.get(R.drawable.ic_notifications_on).also {
                state.secondaryLabel = mContext.getString(R.string.quick_settings_sound_ring)
                state.state = Tile.STATE_ACTIVE
            }
            AudioManager.RINGER_MODE_VIBRATE -> ResourceIcon.get(R.drawable.ic_notifications_vibrate).also {
                state.secondaryLabel = mContext.getString(R.string.quick_settings_sound_vibrate)
                state.state = Tile.STATE_INACTIVE
            }
            AudioManager.RINGER_MODE_SILENT -> ResourceIcon.get(R.drawable.ic_notifications_off).also {
                state.secondaryLabel = mContext.getString(R.string.quick_settings_sound_silent)
                state.state = Tile.STATE_INACTIVE
            }
            else -> return
        }
    }

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.VIEW_UNKNOWN
}
