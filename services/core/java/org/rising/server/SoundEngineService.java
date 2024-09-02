/*
 * Copyright (C) 2023 The RisingOS Android Project
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

package org.rising.server;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.server.SystemService;

public final class SoundEngineService extends SystemService {

    private final AudioManager mAudioManager;
    private static final String TAG = "SoundEngineService";

    private static final String SOUND_ENGINE_LOUDNESS_GAIN = "sound_engine_loudness_gain";
    private static final String SOUND_ENGINE_BASS_BOOST = "sound_engine_bass_boost";
    private static final String SOUND_ENGINE_SURROUND = "sound_engine_surround";
    private static final String SOUND_ENGINE_ENABLED = "sound_engine_enabled";
    private static final int USER_ALL = UserHandle.USER_ALL;

    private final AudioEffectsUtils mAudioEffectsUtils;
    private final Context mContext;
    private final SettingsObserver mSettingsObserver;

    private Handler mAudioHandler;
    private boolean mIsEqRegistered = false;

    public SoundEngineService(Context context) {
        super(context);
        mContext = context;
        mAudioEffectsUtils = new AudioEffectsUtils(mContext);
        mSettingsObserver = new SettingsObserver(new Handler());
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mAudioHandler = new Handler(mContext.getMainLooper());
    }

    @Override
    public void onStart() {
        mSettingsObserver.observe();
        updateEffects();
    }

    private int getSetting(String settingName) {
        try {
            return Settings.System.getIntForUser(
                    getContentResolver(),
                    settingName, 0, ActivityManager.getCurrentUser());
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving setting: " + settingName, e);
            return 0;
        }
    }

    private ContentResolver getContentResolver() {
        return mContext.getContentResolver();
    }

    private void updateEffects() {
        int gain = getSetting(SOUND_ENGINE_LOUDNESS_GAIN) * 100;
        int bassBoost = getSetting(SOUND_ENGINE_BASS_BOOST) * 10;
        int surround = getSetting(SOUND_ENGINE_SURROUND) * 10;
        if (getSetting(SOUND_ENGINE_ENABLED) != 1) {
            disableAllEffects();
            return;
        }
        if (!mIsEqRegistered) {
            mAudioEffectsUtils.initializeEffects();
            mIsEqRegistered = true;
        }
        mAudioEffectsUtils.updateEffects(gain, bassBoost, surround);
    }

    private void disableAllEffects() {
        if (!mIsEqRegistered) return;
        mAudioEffectsUtils.releaseEffects();
        mIsEqRegistered = false;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SOUND_ENGINE_LOUDNESS_GAIN), false, this, USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SOUND_ENGINE_BASS_BOOST), false, this, USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SOUND_ENGINE_SURROUND), false, this, USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(SOUND_ENGINE_ENABLED), false, this, USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateEffects();
        }
    }
}
