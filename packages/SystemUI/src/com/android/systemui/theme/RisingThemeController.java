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

package com.android.systemui.theme;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

public class RisingThemeController {

    private static final String TAG = "RisingThemeController";
    private final ContentResolver mContentResolver;
    private final Handler mBackgroundHandler;

    public RisingThemeController(ContentResolver contentResolver, Handler backgroundHandler) {
        this.mContentResolver = contentResolver;
        this.mBackgroundHandler = backgroundHandler;
    }

    public void observe(String key, boolean isSystemSetting, Runnable reevaluateSystemThemeCallback) {
        Uri uri = isSystemSetting ? Settings.System.getUriFor(key) : Settings.Secure.getUriFor(key);
        if (uri != null) {
            ContentObserver contentObserver = new ContentObserver(mBackgroundHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    reevaluateSystemThemeCallback.run();
                }
            };
            mContentResolver.registerContentObserver(uri, false, contentObserver);
        } else {
            Log.e(TAG, "Failed to get URI for key: " + key);
        }
    }
}

