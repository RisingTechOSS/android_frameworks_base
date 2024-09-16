/*
 * Copyright (C) 2023-2024 the risingOS Android Project
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

package com.android.server;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

import com.android.internal.util.rising.SystemRestartUtils;

import java.util.Calendar;

public class SmartPowerOffService implements PointerEventListener {

    private static final String TAG = "SmartPowerOffService";
    private static final boolean DEBUG = false;
    private static final String POWER_OFF_TIME_KEY = "smart_power_off_time";
    private static final String SMART_POWER_OFF_ENABLED_KEY = "smart_power_off_enabled";
    private static final long DELAY_MILLIS = 1 * 60 * 1000; // 1 minute
    private static final long CHECK_INTERVAL_MS = 2000; // 2 seconds
    private static final long ONE_MINUTE_IN_MILLIS = 60 * 1000; // 1 minute
    private static final int POWER_OFF_SERVICE_NOTIFICATION_ID = 7383856;

    private Context mContext;
    private Handler mHandler;
    private ContentObserver mSettingsObserver;
    private long mPowerOffTimeMillis;
    private long mDelayEndTimeMillis;
    private boolean mPointerEventReceived = false;
    private boolean mIsServiceEnabled = false;
    private boolean mIsScheduleDelayed = false;

    private AlarmManager mAlarmManager;

    private final Alarm mAlarm = new Alarm();

    private class Alarm implements AlarmManager.OnAlarmListener {
        @Override
        public void onAlarm() {
            checkShutDownTime();
        }
        public void set(Calendar time) {
            mAlarmManager.cancel(this);
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), TAG, this, mHandler);
        }
        public void set(long timeInMillis) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timeInMillis);
            set(calendar);
        }
    }

    public SmartPowerOffService(Context context) {
        mContext = context;
        mHandler = new Handler();
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private void wakeupScreen() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        try {
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        TAG + ":WakeupScreen");
                wakeLock.acquire(3000);
                if (DEBUG) Log.d(TAG, "Screen wakeup triggered");
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                if (DEBUG) Log.d(TAG, "Wake lock released");
            }
        }
    }

    private void setupSettingsObserver() {
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                updateServiceState();
                updateShutDownTime();
                if (DEBUG) Log.d(TAG, "Settings changed, removing delay");
                removeDelay();
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(POWER_OFF_TIME_KEY), false, mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(SMART_POWER_OFF_ENABLED_KEY), false, mSettingsObserver);
        updateServiceState();
        updateShutDownTime();
    }

    private void updateServiceState() {
        mIsServiceEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                SMART_POWER_OFF_ENABLED_KEY, 0, UserHandle.USER_CURRENT) != 0;
    }

    private void removeDelay() {
        mPointerEventReceived = false;
        mDelayEndTimeMillis = mPowerOffTimeMillis;
        mAlarm.set(mDelayEndTimeMillis);
        if (DEBUG) Log.d(TAG, "Delay removed, resetting to user-set power off time");
    }

    private void updateShutDownTime() {
        if (!mIsServiceEnabled) return;
        String timeValue = Settings.System.getString(mContext.getContentResolver(), POWER_OFF_TIME_KEY);
        if (timeValue != null) {
            String[] parts = timeValue.split(":");
            if (parts.length == 2) {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                }
                mPowerOffTimeMillis = calendar.getTimeInMillis();
                mDelayEndTimeMillis = mPowerOffTimeMillis;
                mAlarm.set(calendar.getTimeInMillis());
            }
        }
    }

    private void startPeriodicCheck() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkShutDownTime();
                mHandler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        });
    }

    private void checkShutDownTime() {
        if (!mIsServiceEnabled) return;
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis >= mDelayEndTimeMillis) {
            if (!mPointerEventReceived) {
                shutDownDevice();
            }
            updateShutDownTime();
        }
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mPointerEventReceived = true;
            if (DEBUG) Log.d(TAG, "Pointer event detected, delaying power off by 1 minute");
            if (mDelayEndTimeMillis - System.currentTimeMillis() <= ONE_MINUTE_IN_MILLIS) {
                mDelayEndTimeMillis = System.currentTimeMillis() + DELAY_MILLIS;
                mIsScheduleDelayed = true;
                mAlarm.set(mDelayEndTimeMillis);
                if (DEBUG) Log.d(TAG, "New delay end time: " + mDelayEndTimeMillis);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            mPointerEventReceived = false;
            if (DEBUG) Log.d(TAG, "Pointer event released");
        }
    }

    private void shutDownDevice() {
        if (!mIsServiceEnabled || mPointerEventReceived) return;
        wakeupScreen();
        mHandler.postDelayed(() -> {
            SystemRestartUtils.powerOffSystem(mContext);
            if (DEBUG) Log.d(TAG, "Shutting down the device");
        }, 500);
    }

    public void start() {
        setupSettingsObserver();
        startPeriodicCheck();
        if (DEBUG) Log.d(TAG, "SmartPowerOffService started");
    }

    public void stop() {
        mHandler.removeCallbacksAndMessages(null);
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        if (DEBUG) Log.d(TAG, "SmartPowerOffService stopped");
    }
}
