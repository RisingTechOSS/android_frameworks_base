/*
     Copyright (C) 2024 the risingOS Android Project
     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
*/
package com.android.systemui.lockscreen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;
import android.widget.Toast;

import androidx.annotation.StringRes;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.res.R;

import java.util.List;

public class ActivityLauncherUtils {

    private final Context mContext;
    private final ActivityStarter mActivityStarter;

    public ActivityLauncherUtils(Context context) {
        this.mContext = context;
        this.mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    private void launchAppIfAvailable(Intent launchIntent, @StringRes int appTypeResId) {
        final PackageManager packageManager = mContext.getPackageManager();
        final List<ResolveInfo> apps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!apps.isEmpty()) {
            mActivityStarter.startActivity(launchIntent, true);
        } else {
            showNoDefaultAppFoundToast(appTypeResId);
        }
    }

    public void launchTimer() {
        final Intent launchIntent = new Intent(AlarmClock.ACTION_SET_TIMER);
        launchAppIfAvailable(launchIntent, R.string.clock_timer);
    }

    public void launchCalculator() {
        final Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
        launchAppIfAvailable(launchIntent, R.string.calculator);
    }

    public void launchWeatherApp() {
        final Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.setClassName("org.omnirom.omnijaws", "org.omnirom.omnijaws.SettingsActivity");
        launchAppIfAvailable(launchIntent, R.string.omnijaws_weather);
    }

    private void showNoDefaultAppFoundToast(@StringRes int appTypeResId) {
        Toast.makeText(mContext, mContext.getString(appTypeResId) + " not found", Toast.LENGTH_SHORT).show();
    }
}
