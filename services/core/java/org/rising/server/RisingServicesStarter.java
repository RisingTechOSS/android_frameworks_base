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

    public RisingServicesStarter(SystemServiceManager systemServiceManager) {
        this.mSystemServiceManager = systemServiceManager;
    }

    public void startAllServices() {
    }

    private void startService(String serviceClassName) {
        try {
            mSystemServiceManager.startService(serviceClassName);
        } catch (Exception e) {}
    }
}

