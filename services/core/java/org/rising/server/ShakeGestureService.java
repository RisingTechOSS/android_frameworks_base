/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
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

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.server.SystemService;
import com.android.internal.util.android.VibrationUtils;

public final class ShakeGestureService extends SystemService {

    private static final String TAG = "ShakeGestureService";

    private static final String SHAKE_GESTURES_ENABLED = "shake_gestures_enabled";
    private static final String SHAKE_GESTURES_ACTION = "shake_gestures_action";
    private static final int USER_ALL = UserHandle.USER_ALL;

    private final Context mContext;
    private final ShakeGestureUtils mShakeGestureUtils;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private static ShakeGestureService instance;
    private ShakeGesturesCallbacks mShakeCallbacks;

    private final SettingsObserver mSettingsObserver;
    private boolean mShakeServiceEnabled = false;
    private int mShakeGestureAction = 0;

    private ShakeGestureUtils.OnShakeListener mShakeListener;
    private Thread mCpuAwakeThread;

    private ShakeGestureService(Context context, ShakeGesturesCallbacks callback) {
        super(context);
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeGestureService::WakeLock");
        mShakeGestureUtils = new ShakeGestureUtils(mContext);
        mSettingsObserver = new SettingsObserver(new Handler());
        mShakeCallbacks = callback;
        mShakeListener = new ShakeGestureUtils.OnShakeListener() {
            @Override
            public void onShake() {
                if (mShakeServiceEnabled) {
                    mShakeCallbacks.onShake();
                }
            }
        };
        updateSettings();
        mSettingsObserver.observe();
        if (mShakeServiceEnabled) {
            mShakeGestureUtils.registerListener(mShakeListener);
        }
    }

    public static synchronized ShakeGestureService getInstance(Context context, ShakeGesturesCallbacks callback) {
        if (instance == null) {
            instance = new ShakeGestureService(context, callback);
        }
        return instance;
    }

    public interface ShakeGesturesCallbacks {
        void onShake();
    }

    @Override
    public void onStart() {}

    private void updateSettings() {
        mShakeGestureAction = Settings.System.getInt(mContext.getContentResolver(),
                SHAKE_GESTURES_ACTION, 0);
        boolean wasShakeServiceEnabled = mShakeServiceEnabled;
        mShakeServiceEnabled = Settings.System.getInt(mContext.getContentResolver(),
                SHAKE_GESTURES_ENABLED, 0) == 1 && mShakeGestureAction != 0;

        if (mShakeServiceEnabled && !wasShakeServiceEnabled) {
            mShakeGestureUtils.registerListener(mShakeListener);
        } else if (!mShakeServiceEnabled && wasShakeServiceEnabled) {
            mShakeGestureUtils.unregisterListener(mShakeListener);
        }
    }

    private void startCpuAwakeThread() {
        if (mCpuAwakeThread == null) {
            acquireWakeLock();
            mCpuAwakeThread = new Thread();
            mCpuAwakeThread.start();
        }
    }

    private void acquireWakeLock() {
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    public void onInteractiveChanged(boolean awake) {
        if (awake) {
            if (mCpuAwakeThread != null) {
                mCpuAwakeThread = null;
            }
            releaseWakeLock();
            if (mShakeServiceEnabled) {
                mShakeGestureUtils.registerListener(mShakeListener);
            }
        } else {
            if (mShakeServiceEnabled) {
                if (mShakeGestureAction != 4 && mShakeGestureAction != 1) {
                    mShakeGestureUtils.unregisterListener(mShakeListener);
                } else {
                    startCpuAwakeThread();
                }
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SHAKE_GESTURES_ENABLED), false, this, USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SHAKE_GESTURES_ACTION), false, this, USER_ALL);
        }
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
}
