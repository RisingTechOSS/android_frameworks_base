/*
 * Copyright (C) 2023-2024 the RisingOS android Project
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
 * limitations under the License
 */

package com.android.systemui.qs.tiles

import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.util.Log
import android.view.View
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.internal.logging.MetricsLogger
import com.android.systemui.res.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.qs.QSTile.Icon
import com.android.systemui.plugins.qs.QSTile.State
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.QSHost
import com.android.systemui.util.settings.SystemSettings
import javax.inject.Inject

class SoundEngineTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main private val mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val systemSettings: SystemSettings,
): QSTileImpl<State>(
    host,
    uiEventLogger,
    backgroundLooper,
    mainHandler,
    falsingManager,
    metricsLogger,
    statusBarStateController,
    activityStarter,
    qsLogger,
) {
    private val settingsObserver: SettingsObserver
    private val tileLabel: String = mContext.resources.getString(R.string.sound_engine_tile_label)
    private var ignoreSettingsChange: Boolean = false
    private var soundEngineMode = Mode.DISABLED

    init {
        settingsObserver = SettingsObserver()
    }

    override fun newTileState() = State().apply {
        icon = ResourceIcon.get(soundEngineMode?.iconRes ?: R.drawable.ic_sound_engine_disabled)
        state = if (soundEngineMode == Mode.DISABLED) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
    }

    override fun getLongClickIntent() = null

    override fun isAvailable() = true

    override fun getTileLabel(): CharSequence = tileLabel

    override protected fun handleInitialize() {
        settingsObserver.observe()
        updateMode()
    }

    override fun handleClick(view: View?) {
        soundEngineMode = soundEngineMode.next()
        updateSoundEngineProfile()
        refreshState()
    }

    override fun handleUpdateState(state: State?, arg: Any?) {
        state?.apply {
            label = tileLabel
            contentDescription = tileLabel
            secondaryLabel = mContext.getString(soundEngineMode.labelRes)
            icon = ResourceIcon.get(soundEngineMode?.iconRes ?: R.drawable.ic_sound_engine_disabled)
            this.state = if (soundEngineMode == Mode.DISABLED) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        }
    }

    override fun getMetricsCategory(): Int = MetricsEvent.CRDROID_SETTINGS

    override fun destroy() {
        settingsObserver.unobserve()
        super.destroy()
    }

    private fun updateMode() {
        val audioMode = systemSettings.getInt(Companion.AUDIO_EFFECT_MODE, 0)
        soundEngineMode = Mode.values().find { it.modeValue == audioMode } ?: Mode.DISABLED
    }

    private fun updateSoundEngineProfile() {
        ignoreSettingsChange = true
        systemSettings.putInt(Companion.AUDIO_EFFECT_MODE, soundEngineMode.modeValue)
        ignoreSettingsChange = false
    }

    private inner class SettingsObserver : ContentObserver(mainHandler) {
        private var isObserving = false

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!ignoreSettingsChange) {
                updateMode()
                refreshState()
            }
        }

        fun observe() {
            if (isObserving) return
            isObserving = true
            systemSettings.registerContentObserver(Companion.AUDIO_EFFECT_MODE, this)
        }

        fun unobserve() {
            if (!isObserving) return
            isObserving = false
            systemSettings.unregisterContentObserver(this)
        }
    }

    companion object {
        const val TILE_SPEC = "sound_engine"
        private const val TAG = "SoundEngineTile"
        private const val AUDIO_EFFECT_MODE = "audio_effect_mode"
        private const val AUDIO_EFFECT_MODE_ENABLED = "audio_effect_mode_enabled"
        private const val DEBUG = false
    }
    
    private enum class Mode(val modeValue: Int, val iconRes: Int, val labelRes: Int) {
        DISABLED(0, R.drawable.ic_sound_engine_disabled, R.string.sound_engine_mode_disabled_label),
        SMART(4, R.drawable.ic_sound_engine_smart, R.string.sound_engine_mode_smart_label),
        MUSIC(1, R.drawable.ic_sound_engine_music, R.string.sound_engine_mode_music_label),
        THEATER(3, R.drawable.ic_sound_engine_theater, R.string.sound_engine_mode_theater_label),
        GAME(2, R.drawable.ic_sound_engine_game, R.string.sound_engine_mode_game_label);

        fun next(): Mode = values()[(ordinal + 1) % values().size]
    }
}
