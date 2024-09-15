/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.tuner.TunerService;

public class BatteryHealthNotification {

    private static final String CHANNEL_ID = "battery_health";
    private static final String HEALTHY_CHARGE_ENABLED_KEY = "system:health_charge_enabled";
    private static final String HIGH_BATTERY_THRESHOLD_KEY = "system:health_charge_high_threshold";
    private static final String LOW_BATTERY_THRESHOLD_KEY = "system:health_charge_low_threshold";
    private static final String CHARGING_THRESHOLD_PATH_KEY = "system:health_charge_threshold_path";

    private static final int LOW_BATTERY_NOTIFICATION_ID = 7383699;
    private static final int HIGH_BATTERY_NOTIFICATION_ID = 7383700;
    private static int LOW_BATTERY_THRESHOLD = 40;
    private static int HIGH_BATTERY_THRESHOLD = 80;

    private Context context;
    private NotificationManager notificationManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean mHealthyChargeEnabled = false;
    private boolean mServiceRegistered = false;
    private boolean lowBatteryNotified = false;
    private boolean highBatteryNotified = false;
    private String mChargingThresholdPath = "";

    private BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;
            if (!isCharging && batteryLevel <= LOW_BATTERY_THRESHOLD) {
                if (!lowBatteryNotified) {
                    showLowBatteryNotification();
                    dismissHighBatteryNotification();
                }
            } else {
                dismissLowBatteryNotification();
            }
            if (isCharging && batteryLevel >= HIGH_BATTERY_THRESHOLD) {
                if (!highBatteryNotified) {
                    showHighBatteryNotification();
                    dismissLowBatteryNotification();
                }
            } else {
                dismissHighBatteryNotification();
            }
            handleOnPowerStateChanged(isCharging);
        }
    };

    public BatteryHealthNotification(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        Dependency.get(TunerService.class).addTunable(
            mTunable, HEALTHY_CHARGE_ENABLED_KEY, LOW_BATTERY_THRESHOLD_KEY, HIGH_BATTERY_THRESHOLD_KEY, CHARGING_THRESHOLD_PATH_KEY);
        context.registerReceiver(batteryLevelReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public void start() {
        if (!mHealthyChargeEnabled || mServiceRegistered) return;
        context.registerReceiver(batteryLevelReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        mServiceRegistered = true;
    }

    public void stop() {
        if (!mServiceRegistered) return;
        context.unregisterReceiver(batteryLevelReceiver);
        mServiceRegistered = false;
    }

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case HEALTHY_CHARGE_ENABLED_KEY:
                    boolean enabled = TunerService.parseIntegerSwitch(newValue, false);
                    if (enabled != mHealthyChargeEnabled) {
                        mHealthyChargeEnabled = enabled;
                        if (mHealthyChargeEnabled) {
                            start();
                        } else {
                            stop();
                        }
                    }
                    break;
                case LOW_BATTERY_THRESHOLD_KEY:
                    LOW_BATTERY_THRESHOLD = TunerService.parseInteger(newValue, 40);
                    break;
                case HIGH_BATTERY_THRESHOLD_KEY:
                    HIGH_BATTERY_THRESHOLD = TunerService.parseInteger(newValue, 80);
                    break;
                case CHARGING_THRESHOLD_PATH_KEY:
                    mChargingThresholdPath = newValue;
                    break;
                default:
                    break;
            }
        }
    };

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_battery_health),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        notificationManager.createNotificationChannel(channel);
    }

    private void showLowBatteryNotification() {
        if (mHealthyChargeEnabled && !lowBatteryNotified) {
            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_healthy_charge)
                    .setContentTitle(context.getString(R.string.notification_title_low_battery))
                    .setContentText(context.getString(R.string.notification_content_low_battery))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .build();

            notificationManager.notify(LOW_BATTERY_NOTIFICATION_ID, notification);
            lowBatteryNotified = true;
        }
    }

    private void dismissLowBatteryNotification() {
        if (!lowBatteryNotified) return;
        notificationManager.cancel(LOW_BATTERY_NOTIFICATION_ID);
        lowBatteryNotified = false;
    }

    private void showHighBatteryNotification() {
        if (mHealthyChargeEnabled && !highBatteryNotified) {
            boolean canProtectCharge = mChargingThresholdPath != null 
                && !mChargingThresholdPath.isEmpty();
            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_healthy_charge)
                    .setContentTitle(context.getString(canProtectCharge ? 
                        R.string.notification_title_high_battery_protect 
                        : R.string.notification_title_high_battery))
                    .setContentText(context.getString(canProtectCharge ? 
                        R.string.notification_content_high_battery_protect 
                        : R.string.notification_content_high_battery))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .build();
            notificationManager.notify(HIGH_BATTERY_NOTIFICATION_ID, notification);
            highBatteryNotified = true;
        }
    }

    private void dismissHighBatteryNotification() {
        if (!highBatteryNotified) return;
        notificationManager.cancel(HIGH_BATTERY_NOTIFICATION_ID);
        highBatteryNotified = false;
    }

    private void handleOnPowerStateChanged(boolean charging) {
        if (charging) {
            if (lowBatteryNotified) {
                dismissLowBatteryNotification();
            }
        } else {
            if (highBatteryNotified) {
                dismissHighBatteryNotification();
            }
        }
    }
}
