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
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Handler;
import android.provider.Settings;
import com.android.server.SystemService;

public final class NetworkOptimizerService extends SystemService {

    private final Handler handler = new Handler();
    private static final long TOGGLE_DELAY_MS = 500;
    private Context mContext;

    public NetworkOptimizerService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(new UnlockReceiver(), filter);
    }

    private class UnlockReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                handler.postDelayed(() -> {
                    if (!isAirplaneModeOn()) {
                        toggleAirplaneModeOnAndOff();
                    }
                    context.unregisterReceiver(this);
                }, 5000);
            }
        }
    }
    
    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void toggleAirplaneModeOnAndOff() {
        setAirplaneMode(true);
        handler.postDelayed(() -> setAirplaneMode(false), TOGGLE_DELAY_MS);
    }

    private void setAirplaneMode(boolean enable) {
        Settings.Global.putInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, enable ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enable);
        mContext.sendBroadcast(intent);
    }
}
