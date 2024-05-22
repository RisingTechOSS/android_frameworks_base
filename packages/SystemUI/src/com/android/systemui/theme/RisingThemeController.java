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

import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.systemui.res.R;

public class RisingThemeController {

    private static final String TAG = "RisingThemeController";
    private final ContentResolver mContentResolver;
    private final Handler mBackgroundHandler;
    private Context mContext;

    public RisingThemeController(Context context, Handler backgroundHandler) {
        this.mContext = context;
        this.mContentResolver = mContext.getContentResolver();
        this.mBackgroundHandler = backgroundHandler;
    }

    public void observeSystemSettings(Runnable reevaluateSystemThemeCallback, String... keys) {
        for (String key : keys) {
            Uri uri = Settings.System.getUriFor(key);
            observe(uri, reevaluateSystemThemeCallback);
        }
    }

    public void observeSecureSettings(Runnable reevaluateSystemThemeCallback, String... keys) {
        for (String key : keys) {
            Uri uri = Settings.Secure.getUriFor(key);
            observe(uri, reevaluateSystemThemeCallback);
        }
    }

    private void observe(Uri uri, Runnable reevaluateSystemThemeCallback) {
        if (uri != null) {
            ContentObserver contentObserver = new ContentObserver(mBackgroundHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    Toast toast = Toast.makeText(mContext, R.string.reevaluating_system_theme, Toast.LENGTH_SHORT);
                    toast.show();
                    mBackgroundHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                             reevaluateSystemThemeCallback.run();
                        }
                    }, toast.getDuration() + 1250);
                }
            };
            mContentResolver.registerContentObserver(uri, false, contentObserver);
        } else {
            Log.e(TAG, "Failed to get URI for key");
        }
    }
}
