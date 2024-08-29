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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import com.android.server.SystemService;

public class StorageCleanerService extends SystemService {

    private static final String TAG = "StorageCleanerService";
    private Context mContext;
    private final Handler mHandler;

    public StorageCleanerService(Context context) {
        super(context);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onStart() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mScreenOnReceiver, filter);
    }

    @Override
    public void onBootPhase(int phase) {
        super.onBootPhase(phase);
    }

    private final BroadcastReceiver mScreenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                mHandler.post(() -> {
                    if (isCacheCleanerEnabled()) {
                        clearAppCache();
                    } else {
                        Log.i(TAG, "Cache cleaner is disabled.");
                    }
                });
            }
        }
    };

    private boolean isCacheCleanerEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(), 
            "cache_cleaner_enabled", 0, UserHandle.USER_CURRENT) != 0;
    }

    private void clearAppCache() {
        PackageManager pm = mContext.getPackageManager();
        for (ApplicationInfo appInfo : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            try {
                mContext.getPackageManager().deleteApplicationCacheFiles(appInfo.packageName, null);
                //Log.i(TAG, "Cache cleared for package: " + appInfo.packageName);
            } catch (Exception e) {
                //Log.e(TAG, "Error clearing cache for package: " + appInfo.packageName, e);
            }
        }
    }
}
