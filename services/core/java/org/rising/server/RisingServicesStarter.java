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

import com.android.server.SystemServiceManager;

public class RisingServicesStarter {

    private final SystemServiceManager mSystemServiceManager;

    private static final String QUICKSWITCH_SERVICE_CLASS =
            "org.rising.server.QuickSwitchService";

    private static final String SOUND_ENGINE_SERVICE_CLASS =
            "org.rising.server.SoundEngineService";

    private static final String NETWORK_OPT_SERVICE_CLASS =
            "org.rising.server.NetworkOptimizerService";

    private static final String CHARGING_OPT_SERVICE_CLASS =
            "org.rising.server.AdaptiveChargingService";
            
    private static final String STORAGE_CLEANER_SERVICE_CLASS =
            "org.rising.server.StorageCleanerService";

    public RisingServicesStarter(SystemServiceManager systemServiceManager) {
        this.mSystemServiceManager = systemServiceManager;
    }

    public void startAllServices() {
        startService(QUICKSWITCH_SERVICE_CLASS);
        startService(SOUND_ENGINE_SERVICE_CLASS);
        startService(CHARGING_OPT_SERVICE_CLASS);
        startService(STORAGE_CLEANER_SERVICE_CLASS);
    }

    private void startService(String serviceClassName) {
        try {
            mSystemServiceManager.startService(serviceClassName);
        } catch (Exception e) {}
    }
}

