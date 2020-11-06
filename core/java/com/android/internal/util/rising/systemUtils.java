/*
 * Copyright (C) 2017-2023 crDroid Android Project
 * Copyright (C) 2020 - Havoc OS
 * Copyright (C) 2023 Rising OS Android Project
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

package com.android.internal.util.rising;

import static android.provider.Settings.Global.ZEN_MODE_OFF;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;

import android.app.AlertDialog;
import android.app.IActivityManager;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.os.AsyncTask;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorPrivacyManager;
import android.location.LocationManager;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.statusbar.IStatusBarService;
import android.os.RemoteException;
import com.android.internal.util.ArrayUtils;
import com.android.internal.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class systemUtils {

    public static boolean isPackageInstalled(Context context, String packageName, boolean ignoreState) {
        if (packageName != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        return isPackageInstalled(context, packageName, true);
    }

    public static boolean isPackageEnabled(Context context, String packageName) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
            return pi.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException notFound) {
            return false;
        }
    }

    public static List<String> launchablePackages(Context context) {
        List<String> list = new ArrayList<>();

        Intent filter = new Intent(Intent.ACTION_MAIN, null);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(filter,
                PackageManager.GET_META_DATA);

        int numPackages = apps.size();
        for (int i = 0; i < numPackages; i++) {
            ResolveInfo app = apps.get(i);
            list.add(app.activityInfo.packageName);
        }

        return list;
    }

    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        FireActions.toggleCameraFlash();
    }

    public static boolean hasNavbarByDefault(Context context) {
        boolean needsNav = context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        if ("1".equals(navBarOverride)) {
            needsNav = false;
        } else if ("0".equals(navBarOverride)) {
            needsNav = true;
        }
        return needsNav;
    }
    
   public static void showSystemRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.system_restart_title)
                .setMessage(R.string.system_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartAndroid(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restartAndroid(Context context) {
        new restartAndroidTask(context).execute();
    }

    private static class restartAndroidTask extends AsyncTask<Void, Void, Void> {

        public restartAndroidTask(Context context) {
            super();
        }

        @Override
        protected Void doInBackground(Void... params) {
          IStatusBarService mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
            try {
                 try {
                   Thread.sleep(1000);
               } catch (InterruptedException e) {}
                  try {
                     mBarService.reboot(false, null);
                   } catch (RemoteException e) {}
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

   public static void showSettingsRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_restart_title)
                .setMessage(R.string.settings_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSettings(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restartSettings(Context context) {
        new restartSettingsTask(context).execute();
    }

    private static class restartSettingsTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<Context> mContext;

        public restartSettingsTask(Context context) {
            super();
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.get().getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.settings".equals(app.processName)) {
                    	ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Keep FireAction methods below this point.
     * Place calls to methods above this point.
     */
    private static final class FireActions {
        private static IStatusBarService mStatusBarService = null;
        private static IStatusBarService getStatusBarService() {
            synchronized (FireActions.class) {
                if (mStatusBarService == null) {
                    mStatusBarService = IStatusBarService.Stub.asInterface(
                            ServiceManager.getService("statusbar"));
                }
                return mStatusBarService;
            }
        }

        public static void toggleCameraFlash() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }
    }

    public static class SystemManagerController {
        private final Resources mResources;

        private Context mContext;
        private AudioManager mAudioManager;
        private NotificationManager mNotificationManager;
        private WifiManager mWifiManager;
        private BluetoothAdapter mBluetoothAdapter;
        private int mSubscriptionId;
        private Toast mToast;

        private boolean mSystemManagerAggresiveMode;

        private static boolean mWifiState;
        private static boolean mCellularState;
        private static boolean mBluetoothState;
        private static int mLocationState;
        private static int mRingerState;
        private static int mZenState;

        private static final String TAG = "SystemManagerController";

        public SystemManagerController(Context context) {
            mContext = context;
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            mResources = mContext.getResources();
         
            mSystemManagerAggresiveMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE, 0, UserHandle.USER_CURRENT) == 1;

            SettingsObserver observer = new SettingsObserver(new Handler(Looper.getMainLooper()));
            observer.observe();
            observer.update();
        }

        private TelephonyManager getTelephonyManager() {
            int subscriptionId = mSubscriptionId;

            // If mSubscriptionId is invalid, get default data sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
            }

            // If data sub is also invalid, get any active sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                int[] activeSubIds = SubscriptionManager.from(mContext).getActiveSubscriptionIdList();
                if (!ArrayUtils.isEmpty(activeSubIds)) {
                    subscriptionId = activeSubIds[0];
                }
            }

            return mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subscriptionId);
        }

        private boolean isWifiEnabled() {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                return mWifiManager.isWifiEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setWifiEnabled(boolean enable) {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                mWifiManager.setWifiEnabled(enable);
            } catch (Exception e) {
            }
        }

        private int getLocationMode() {
            return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF, UserHandle.USER_CURRENT);
        }

        private void setLocationMode(int mode) {
            Settings.Secure.putIntForUser(mContext.getContentResolver(),
                        Settings.Secure.LOCATION_MODE, mode, UserHandle.USER_CURRENT);
        }

        private boolean isBluetoothEnabled() {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                return mBluetoothAdapter.isEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setBluetoothEnabled(boolean enable) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                if (enable) mBluetoothAdapter.enable();
                else mBluetoothAdapter.disable();
            } catch (Exception e) {
            }
        }

        private int getZenMode() {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                return mNotificationManager.getZenMode();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setZenMode(int mode) {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                mNotificationManager.setZenMode(mode, null, TAG);
            } catch (Exception e) {
            }
        }

        private int getRingerModeInternal() {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                return mAudioManager.getRingerModeInternal();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setRingerModeInternal(int mode) {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                mAudioManager.setRingerModeInternal(mode);
            } catch (Exception e) {
            }
        }

        private void enableAggressiveMode() {
            if (!ActivityManager.isSystemReady()) return;

            // Disable Wi-Fi
            final boolean systemManagerDisableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_WIFI_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableWifi) {
                mWifiState = isWifiEnabled();
                setWifiEnabled(false);
            }

            // Disable Bluetooth
            final boolean systemManagerDisableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_BLUETOOTH_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableBluetooth) {
                mBluetoothState = isBluetoothEnabled();
                setBluetoothEnabled(false);
            }

            // Disable Mobile Data
            final boolean systemManagerDisableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_CELLULAR_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableData) {
                mCellularState = getTelephonyManager().isDataEnabled();
                getTelephonyManager().setDataEnabled(false);
            }

            // Disable Location
            final boolean systemManagerDisableLocation = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_LOCATION_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableLocation) {
                mLocationState = getLocationMode();
                setLocationMode(Settings.Secure.LOCATION_MODE_OFF);
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0) {
                mRingerState = getRingerModeInternal();
                mZenState = getZenMode();
                switch (ringerMode) {
                    case 1:
                        setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                        setZenMode(ZEN_MODE_OFF);
                        break;
                    case 2:
                        setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                        setZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                        break;
                    case 3:
                        setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                        setZenMode(ZEN_MODE_OFF);
                        break;
                    case 4:
                        setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                        setZenMode(ZEN_MODE_OFF);
                        break;
                    default:
                        break;
                }
            }
        }


        private void disableAggressiveMode() {
            if (!ActivityManager.isSystemReady()) return;

            // Enable Wi-Fi
            final boolean systemManagerDisableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_WIFI_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableWifi && mWifiState != isWifiEnabled()) {
                setWifiEnabled(mWifiState);
            }

            // Enable Bluetooth
            final boolean systemManagerDisableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_BLUETOOTH_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableBluetooth && mBluetoothState != isBluetoothEnabled()) {
                setBluetoothEnabled(mBluetoothState);
            }

            // Enable Mobile Data
            final boolean systemManagerDisableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_CELLULAR_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableData && mCellularState != getTelephonyManager().isDataEnabled()) {
                getTelephonyManager().setDataEnabled(mCellularState);
            }

            // Enable Location
            final boolean systemManagerDisableLocation = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_LOCATION_TOGGLE, 0, UserHandle.USER_CURRENT) == 1;
            if (systemManagerDisableLocation && mLocationState != getLocationMode()) {
                setLocationMode(mLocationState);
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0 && (mRingerState != getRingerModeInternal() ||
                    mZenState != getZenMode())) {
                setRingerModeInternal(mRingerState);
                setZenMode(mZenState);
            }
        }

        private void setAMTriggerState(boolean aggressiveTriggerState) {
            if (mSystemManagerAggresiveMode == aggressiveTriggerState) {
                return;
            }

            mSystemManagerAggresiveMode = aggressiveTriggerState;

            if (mSystemManagerAggresiveMode) {
                enableAggressiveMode();
                return;
            }
            disableAggressiveMode();
            
        }

        class SettingsObserver extends ContentObserver {
            SettingsObserver(Handler handler) {
                super(handler);
            }

            void observe() {
                ContentResolver resolver = mContext.getContentResolver();
                resolver.registerContentObserver(Settings.Secure.getUriFor(
                        Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE), false, this,
                        UserHandle.USER_ALL);
                resolver.registerContentObserver(Settings.Secure.getUriFor(
                        Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_TRIGGER), false, this,
                        UserHandle.USER_ALL);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                update();
            }

            void update() {
                final boolean agressiveModeEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE, 0, UserHandle.USER_CURRENT) == 1;
                final boolean amTriggerState = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.SYSTEM_MANAGER_AGGRESSIVE_IDLE_MODE_TRIGGER, 0, UserHandle.USER_CURRENT) == 1;

                setAMTriggerState(agressiveModeEnabled ? amTriggerState : false);
            }
        }
    }
}

