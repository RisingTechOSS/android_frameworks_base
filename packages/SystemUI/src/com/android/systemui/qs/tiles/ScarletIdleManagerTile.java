/*
 * Copyright (C) 2023 riceDroid Android Project
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.SettingObserver;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.settings.SecureSettings;

import java.time.format.DateTimeFormatter;
import java.time.LocalTime;

import javax.inject.Inject;

public class ScarletIdleManagerTile extends SecureQSTile<QSTile.BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_scarlet);
    
    private static final ComponentName MISC_SETTINGS_COMPONENT = new ComponentName(
            "com.android.settings", "com.android.settings.Settings$ScarletSettingsActivity");

    private static final Intent MISC_SETTINGS =
            new Intent().setComponent(MISC_SETTINGS_COMPONENT);

    private final SettingObserver mSetting;

    @Inject
    public ScarletIdleManagerTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SecureSettings secureSettings,
            KeyguardStateController keyguardStateController
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger, keyguardStateController);

        mSetting = new SettingObserver(secureSettings, mHandler, Settings.Secure.SCARLET_AGGRESSIVE_IDLE_MODE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
        SettingsObserver settingsObserver = new SettingsObserver(mainHandler);
        settingsObserver.observe();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view, boolean keyguardShowing) {
        if (checkKeyguard(view, keyguardShowing)) {
            return;
        }
        setEnabled(!mState.value);
        refreshState();
    }

    private void setEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.SCARLET_AGGRESSIVE_IDLE_MODE, enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean sysManagerState = value != 0;
        final boolean sysManagerEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCARLET_SYSTEM_MANAGER, 0, UserHandle.USER_CURRENT) == 1;
        final boolean sysIdleManagerEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SCARLET_AGGRESSIVE_IDLE_MODE, 0, UserHandle.USER_CURRENT) == 1;

        state.value = sysManagerState;
        state.label = mContext.getString(R.string.quick_settings_scarlet_idle_mode_label);
        state.icon = mIcon;
        state.contentDescription = TextUtils.isEmpty(state.secondaryLabel)
                ? state.label
                : TextUtils.concat(state.label, ", ", state.secondaryLabel);
	state.secondaryLabel = sysManagerEnabled ? mContext.getResources().getString(sysIdleManagerEnabled ? R.string.quick_settings_scarlet_idle_mode_enabled : R.string.quick_settings_scarlet_idle_mode_disabled) : mContext.getResources().getString(R.string.quick_settings_scarlet_services_unavailable);
        state.state = sysManagerEnabled ? (state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE) : Tile.STATE_UNAVAILABLE;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public Intent getLongClickIntent() {
        return MISC_SETTINGS;
    }

    @Override
    protected void handleSetListening(boolean listening) {
    }

    @Override
    public CharSequence getTileLabel() {
        return getState().label;
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SCARLET_AGGRESSIVE_IDLE_MODE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCARLET_SYSTEM_MANAGER), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    }
}
