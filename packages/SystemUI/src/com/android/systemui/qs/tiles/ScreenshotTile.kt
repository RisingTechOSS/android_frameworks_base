/*
 * Copyright (C) 2020 The OmniROM Project
 *               2023 the RisingOS android Project
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.ScreenshotSource.SCREENSHOT_GLOBAL_ACTIONS
import android.view.WindowManager.TAKE_SCREENSHOT_FULLSCREEN
import android.view.WindowManager.TAKE_SCREENSHOT_SELECTED_REGION
import androidx.annotation.Nullable
import com.android.internal.util.ScreenshotHelper
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.qs.QSHost
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.internal.R
import javax.inject.Inject

/** Quick settings tile: Screenshot **/
class ScreenshotTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
) : QSTileImpl<BooleanState>(host, backgroundLooper, mainHandler, falsingManager, metricsLogger, statusBarStateController, activityStarter, qsLogger) {

    companion object {
        const val TILE_SPEC = "screenshot"
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.CUSTOM_SETTINGS
    }

    override fun newTileState(): BooleanState {
        return BooleanState()
    }

    override fun handleSetListening(listening: Boolean) {}

    override fun isAvailable(): Boolean {
        return true
    }

    override fun handleClick(@Nullable view: View?) {
        host.collapsePanels()
        val screenshotHelper = ScreenshotHelper(mContext)
        mHandler.postDelayed({
            screenshotHelper.takeScreenshot(
                TAKE_SCREENSHOT_FULLSCREEN,
                SCREENSHOT_GLOBAL_ACTIONS,
                mHandler,
                null
            )
        }, 1000)
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    override fun handleLongClick(@Nullable view: View?) {
        host.collapsePanels()
        val screenshotHelper = ScreenshotHelper(mContext)
        mHandler.postDelayed({
            screenshotHelper.takeScreenshot(
                TAKE_SCREENSHOT_SELECTED_REGION,
                SCREENSHOT_GLOBAL_ACTIONS,
                mHandler,
                null
            )
        }, 1000)
        refreshState()
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.global_action_screenshot)
    }

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        state.label = mContext.getString(R.string.global_action_screenshot)
        state.contentDescription = mContext.getString(R.string.global_action_screenshot)
        state.secondaryLabel = mContext.getString(com.android.systemui.R.string.screenshot_long_press)
        state.icon = ResourceIcon.get(R.drawable.ic_screenshot)
        state.value = true
        state.state = Tile.STATE_INACTIVE
    }
}
