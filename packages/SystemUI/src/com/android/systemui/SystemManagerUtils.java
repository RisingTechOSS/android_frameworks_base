/*
 * Copyright (C) 2019 Descendant
 * Copyright (C) 2023 the RisingOS Android Project
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

package com.android.systemui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.power.Boost;
import android.hardware.power.Mode;
import android.location.LocationManager;
import android.os.Handler;
import android.os.UserHandle;
import android.os.PowerManagerInternal;
import android.provider.Settings;

import com.android.server.LocalServices;
import com.android.systemui.Dependency;

import java.util.ArrayList;
import java.util.List;

public class SystemManagerUtils {
    static String TAG = "SystemManagerUtils";

    static Handler h = new Handler();
    static Runnable mStartManagerInstance;
    static Runnable mStopManagerInstance;
    static List<ActivityManager.RunningAppProcessInfo> RunningServices;
    static ActivityManager localActivityManager;
    static Context imContext;
    static ContentResolver mContentResolver;
    static List<String> killablePackages;
    static final long IDLE_TIME_NEEDED = 10000;
    static int ultraSaverStatus;

    public static void initSystemManager(Context mContext) {
        imContext = mContext;
        killablePackages = new ArrayList<>();
        localActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mContentResolver = mContext.getContentResolver();

        mStartManagerInstance = new Runnable() {
            public void run() {
                    powerSaverHandler(true);
                    killBackgroundProcesses();
            }
        };
        mStopManagerInstance = new Runnable() {
            public void run() {
                cancelIdleService(mContext);
            }
        };
    }

    public static void startIdleService() {
        RunningServices = localActivityManager.getRunningAppProcesses();

        if (IDLE_TIME_NEEDED > timeBeforeAlarm(imContext) && timeBeforeAlarm(imContext) != 0) {
            h.postDelayed(mStartManagerInstance,100);
        } else {
            h.postDelayed(mStartManagerInstance,IDLE_TIME_NEEDED /*10ms*/);
        }
        if (timeBeforeAlarm(imContext) != 0) {
            h.postDelayed(mStopManagerInstance,(timeBeforeAlarm(imContext) - 900000));
        }
    }

    public static void cacheCleaner(PackageManager pm) {
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo packageInfo : apps) {
            pm.deleteApplicationCacheFiles(packageInfo.packageName,null);
        }
    }

    public static void powerSaverHandler(boolean enable) {
        PowerManagerInternal mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        if (mLocalPowerManager != null) {
          mLocalPowerManager.setPowerMode(Mode.DEVICE_IDLE, enable);
        }
    }

    public static void cancelIdleService(Context mContext) {
        h.removeCallbacks(mStartManagerInstance);
        onScreenWake(mContext);
    }

     public static void boostingServiceHandler(boolean enable) {
        PowerManagerInternal mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        if (mLocalPowerManager != null) {
          mLocalPowerManager.setPowerMode(Mode.SUSTAINED_PERFORMANCE, enable);
        }
     }

    public static void onScreenWake(Context mContext) {
        h.removeCallbacks(mStopManagerInstance);
        powerSaverHandler(false);
        PowerManagerInternal mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        if (mLocalPowerManager != null) {
          mLocalPowerManager.setPowerBoost(Boost.DISPLAY_UPDATE_IMMINENT, 200);
        }
    }

    public static long timeBeforeAlarm(Context imContext) {
        AlarmManager.AlarmClockInfo info =
                ((AlarmManager)imContext.getSystemService(Context.ALARM_SERVICE)).getNextAlarmClock();
        if (info != null) {
            long alarmTime = info.getTriggerTime();
            long realTime = alarmTime - System.currentTimeMillis();
            return realTime;
        } else {
            return 0;
        }
    }

    public static void killBackgroundProcesses() {
        localActivityManager = (ActivityManager) imContext.getSystemService(Context.ACTIVITY_SERVICE);
        RunningServices = localActivityManager.getRunningAppProcesses();
        for (int i=0; i < RunningServices.size(); i++) {
            if (!RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("com.android.") &&
                !RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("org.rising") &&
                !RunningServices.get(i).pkgList[0].toString().toLowerCase().equals("android") &&
                !RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("launcher") &&
                !RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("ims") &&
                !RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("messaging") &&
                RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("camera") &&
                RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("settings") &&
                RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("gms") &&
                RunningServices.get(i).pkgList[0].toString().toLowerCase().contains("google")) {
                    localActivityManager.killBackgroundProcesses(RunningServices.get(i).pkgList[0].toString());
            }
        }
    }

}
