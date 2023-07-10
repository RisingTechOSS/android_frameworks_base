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

package com.android.systemui

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.power.Mode
import android.os.Handler
import android.os.PowerManager
import android.os.PowerManagerInternal
import com.android.internal.util.rising.systemUtils.SystemManagerController
import com.android.server.LocalServices
import java.util.*

class SystemManagerUtils {
    private val IDLE_TIME_NEEDED: Long = 20000
    private val appIdleBlacklist: Set<String> = setOf(
        "google",
        ".gms"
    )

    private val handler = Handler()
    private lateinit var startManagerInstance: Runnable
    private lateinit var stopManagerInstance: Runnable
    private var localPowerManager: PowerManagerInternal? = null
    private lateinit var sysManagerController: SystemManagerController
    private lateinit var usageStatsManager: UsageStatsManager
    private val unusedAppPackages = HashSet<String>()

    fun initSystemManager(context: Context) {
        sysManagerController = SystemManagerController(context)
        localPowerManager = LocalServices.getService(PowerManagerInternal::class.java)
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        startManagerInstance = Runnable { idleModeHandler(true) }
        stopManagerInstance = Runnable { cancelIdleService() }
    }

    fun startIdleService(context: Context) {
        val nextAlarmTime = timeBeforeAlarm(context)
        val delay = nextAlarmTime.coerceAtMost(IDLE_TIME_NEEDED)
        handler.postDelayed(startManagerInstance, delay)
    }

    fun idleModeHandler(idle: Boolean) {
        localPowerManager?.setPowerMode(Mode.DEVICE_IDLE, idle)
    }

    fun cancelIdleService() {
        handler.removeCallbacks(startManagerInstance)
        onScreenWake()
    }

    fun boostingServiceHandler(enable: Boolean, boostingLevel: Int) {
        localPowerManager?.let { powerManager ->
            powerManager.setPowerMode(Mode.SUSTAINED_PERFORMANCE, false)
            powerManager.setPowerMode(Mode.INTERACTIVE, false)
            powerManager.setPowerMode(Mode.FIXED_PERFORMANCE, false)
            when (boostingLevel) {
                1 -> powerManager.setPowerMode(Mode.SUSTAINED_PERFORMANCE, enable)
                2 -> powerManager.setPowerMode(Mode.INTERACTIVE, enable)
                3 -> powerManager.setPowerMode(Mode.FIXED_PERFORMANCE, enable)
            }
        }
    }

    fun onScreenWake() {
        handler.removeCallbacks(stopManagerInstance)
        idleModeHandler(false)
    }

    fun enterPowerSaveMode(context: Context, enable: Boolean) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        powerManager?.setAdaptivePowerSaveEnabled(enable)
    }

    fun timeBeforeAlarm(context: Context): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        return alarmManager?.nextAlarmClock?.triggerTime?.minus(System.currentTimeMillis()) ?: 0L
    }

    private fun isIdleBlacklisted(packageName: String): Boolean {
        return appIdleBlacklist.any { packageName.contains(it) }
    }

    private fun deleteUnusedAppsCacheFiles(context: Context, pm: PackageManager, packageNames: Set<String>) {
        packageNames.forEach { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                if (!isSystemApp(appInfo)) {
                    context.packageManager.deleteApplicationCacheFiles(packageName, null)
                }
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
    }

    fun deepClean(context: Context, pm: PackageManager, idle: Boolean) {
        unusedAppPackages.clear()

        val currentTime = System.currentTimeMillis()
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - (24 * 60 * 60 * 1000),
            currentTime
        )

        val blacklistedPackages = usageStatsList
            .filter { isIdleBlacklisted(it.packageName.toLowerCase()) }
            .filter { it.totalTimeInForeground == 0L }
            .map { it.packageName.toLowerCase() }
            .toSet()

        unusedAppPackages.addAll(blacklistedPackages)

        deleteUnusedAppsCacheFiles(context, pm, unusedAppPackages)
    }

    fun killBackgroundProcesses(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        val runningProcesses = activityManager?.runningAppProcesses ?: return

        val processesToKill = runningProcesses
            .filter { processInfo ->
                processInfo.pkgList.any { isIdleBlacklisted(it.toLowerCase()) }
            }
            .map { it.processName }
            .toSet()

        processesToKill.forEach { process ->
            activityManager.killBackgroundProcesses(process)
        }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }
}
