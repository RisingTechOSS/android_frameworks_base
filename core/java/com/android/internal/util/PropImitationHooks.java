/*
 * Copyright (C) 2022 Paranoid Android
 * Copyright (C) 2022 StatiXOS
 * Copyright (C) 2023 the RisingOS Android Project
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.Context;
import android.content.ComponentName;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Process;
import android.os.Build.VERSION;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = false;
    
    private static final String PRODUCT_DEVICE = "ro.product.device";

    private static final String sMainFP = "google/felix/felix:14/UP1A.231105.003/11010452:user/release-keys";
    private static final String sMainModel = "Pixel Fold";
    private static final String sStockFp = SystemProperties.get("ro.vendor.build.fingerprint");

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_GCAM = "com.google.android.GoogleCamera";
    private static final String PACKAGE_SNAPCHAT = "com.snapchat.android";

    private static final String PACKAGE_SUBSCRIPTION_RED = "com.google.android.apps.subscriptions.red";
    private static final String PACKAGE_TURBO = "com.google.android.apps.turbo";
    private static final String PACKAGE_VELVET = "com.google.android.googlequicksearchbox";
    private static final String PACKAGE_GBOARD = "com.google.android.inputmethod.latin";
    private static final String PACKAGE_SETUPWIZARD = "com.google.android.setupwizard";
    private static final String PACKAGE_EMOJI_WALLPAPER = "com.google.android.apps.emojiwallpaper";
    private static final String PACKAGE_CINEMATIC_PHOTOS = "com.google.android.wallpaper.effects";
    private static final String PACKAGE_GOOGLE_WALLPAPERS = "com.google.android.wallpaper";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final Map<String, Object> sMainSpoofProps = createGoogleSpoofProps(sMainModel, sMainFP);
    private static final Map<String, Object> gPhotosProps = createGoogleSpoofProps("Pixel XL", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    private static final Map<String, Object> asusROG1Props = createGameProps("ASUS_Z01QD", "Asus");
    private static final Map<String, Object> asusROG3Props = createGameProps("ASUS_I003D", "Asus");
    private static final Map<String, Object> xperia5Props = createGameProps("SO-52A", "Sony");
    private static final Map<String, Object> op8ProProps = createGameProps("IN2020", "OnePlus");
    private static final Map<String, Object> op9RProps = createGameProps("LE2101", "OnePlus");
    private static final Map<String, Object> xmMi11TProps = createGameProps("21081111RG", "Xiaomi");
    private static final Map<String, Object> xmF4Props = createGameProps("22021211RG", "Xiaomi");

    private static Map<String, Object> createGameProps(String model, String manufacturer) {
        Map<String, Object> props = new HashMap<>();
        props.put("MODEL", model);
        props.put("MANUFACTURER", manufacturer);
        return props;
    }

    private static Map<String, Object> createGoogleSpoofProps(String model, String fingerprint) {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "google");
        props.put("MANUFACTURER", "Google");
        props.put("ID", getBuildID(fingerprint));
        props.put("DEVICE", getDeviceName(fingerprint));
        props.put("PRODUCT", getDeviceName(fingerprint));
        props.put("MODEL", model);
        props.put("FINGERPRINT", fingerprint);
        props.put("TYPE", "user");
        props.put("TAGS", "release-keys");
        return props;
    }

    private static String getDeviceName(String fingerprint) {
        String[] parts = fingerprint.split("/");
        if (parts.length >= 2) {
            return parts[1];
        } else {
            return "";
        }
    }

    private static final Set<String> packagesToChangeROG1 = new HashSet<>(Arrays.asList(
            "com.madfingergames.legends"
    ));

    private static final Set<String> packagesToChangeROG3 = new HashSet<>(Arrays.asList(
            "com.pearlabyss.blackdesertm",
            "com.pearlabyss.blackdesertm.gl"
    ));

    private static final Set<String> packagesToChangeXP5 = new HashSet<>(Arrays.asList(
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn"
    ));

    private static final Set<String> packagesToChangeOP8P = new HashSet<>(Arrays.asList(
            "com.netease.lztgglobal",
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.rekoo.pubgm",
            "com.riotgames.league.wildrift",
            "com.riotgames.league.wildrifttw",
            "com.riotgames.league.wildriftvn",
            "com.tencent.ig",
            "com.tencent.tmgp.pubgmhd",
            "com.vng.pubgmobile"
    ));

    private static final Set<String> packagesToChangeOP9R = new HashSet<>(Arrays.asList(
            "com.epicgames.fortnite",
            "com.epicgames.portal"
    ));

    private static final Set<String> packagesToChange11T = new HashSet<>(Arrays.asList(
            "com.ea.gp.apexlegendsmobilefps",
            "com.levelinfinite.hotta.gp",
            "com.mobile.legends",
            "com.supercell.clashofclans",
            "com.tencent.tmgp.sgame",
            "com.vng.mlbbvn"
    ));

    private static final Set<String> packagesToChangeF4 = new HashSet<>(Arrays.asList(
            "com.dts.freefiremax",
            "com.dts.freefireth"
    ));

    private static String getBuildID(String fingerprint) {
        Pattern pattern = Pattern.compile("([A-Za-z0-9]+\\.\\d+\\.\\d+\\.\\w+)");
        Matcher matcher = pattern.matcher(fingerprint);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static volatile boolean sIsGms, sIsFinsky, sIsSetupWizard;
    private static volatile String sProcessName;

    public static void setProps(Application app) {
        final String packageName = app.getPackageName();
        final String processName = app.getProcessName();
        if (app == null || packageName == null || processName == null) {
            return;
        }
        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsSetupWizard = packageName.equals(PACKAGE_SETUPWIZARD);

        if (sIsGms) {
            if (shouldTryToCertifyDevice()) {
                dlog("Spoofing build for GMS");
                spoofBuildGms();
            }
        } else {
            switch (packageName) {
                case PACKAGE_SUBSCRIPTION_RED:
                case PACKAGE_TURBO:
                case PACKAGE_VELVET:
                case PACKAGE_GBOARD:
                case PACKAGE_SETUPWIZARD:
                case PACKAGE_GMS:
                case PACKAGE_GCAM:
                case PACKAGE_CINEMATIC_PHOTOS:
                case PACKAGE_GOOGLE_WALLPAPERS:
                case PACKAGE_EMOJI_WALLPAPER:
                    if (packageName.equals(PACKAGE_GCAM) && !SystemProperties.getBoolean("persist.sys.pixelprops.gcam", false)) {
                        dlog("Not spoofing as " + sMainModel + " for: " + packageName);
                        break;
                    }
                    dlog("Spoofing as " + sMainModel + " for: " + packageName);
                    sMainSpoofProps.forEach((k, v) -> setPropValue(k, v));
                    setPropValue("TIME", System.currentTimeMillis());
                    break;
                case PACKAGE_ARCORE:
                    dlog("Setting stock fingerprint for: " + packageName);
                    setPropValue("FINGERPRINT", sStockFp);
                    break;
                case PACKAGE_SNAPCHAT:
                    dlog("Spoofing build for: " + packageName);
                    spoofBuildGms();
                    break;
                case PACKAGE_GPHOTOS:
                    if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", true)) {
                        dlog("Spoofing as Pixel XL for: " + packageName);
                        gPhotosProps.forEach((k, v) -> setPropValue(k, v));
                    }
                    break;
                default:
                    if (SystemProperties.getBoolean("persist.sys.pixelprops.games", false)) {
                        Map<String, Object> gamePropsToSpoof = null;
                        if (packagesToChangeROG1.contains(packageName)) {
                            dlog("Spoofing as Asus ROG 1 for: " + packageName);
                            gamePropsToSpoof = asusROG1Props;
                        } else if (packagesToChangeROG3.contains(packageName)) {
                            dlog("Spoofing as Asus ROG 3 for: " + packageName);
                            gamePropsToSpoof = asusROG3Props;
                        } else if (packagesToChangeXP5.contains(packageName)) {
                            dlog("Spoofing as Sony Xperia 5 for: " + packageName);
                            gamePropsToSpoof = xperia5Props;
                        } else if (packagesToChangeOP8P.contains(packageName)) {
                            dlog("Spoofing as Oneplus 8 Pro for: " + packageName);
                            gamePropsToSpoof = op8ProProps;
                        } else if (packagesToChangeOP9R.contains(packageName)) {
                            dlog("Spoofing as Oneplus 9R for: " + packageName);
                            gamePropsToSpoof = op9RProps;
                        } else if (packagesToChange11T.contains(packageName)) {
                            dlog("Spoofing as Xiaomi Mi 11T for: " + packageName);
                            gamePropsToSpoof = xmMi11TProps;
                        } else if (packagesToChangeF4.contains(packageName)) {
                            dlog("Spoofing as Xiaomi F4 for: " + packageName);
                            gamePropsToSpoof = xmF4Props;
                        }
                        if (gamePropsToSpoof != null) {
                            gamePropsToSpoof.forEach((k, v) -> setPropValue(k, v));
                        }
                    }
                    break;
            }
        }
    }

    private static boolean shouldTryToCertifyDevice() {
        final boolean[] shouldCertify = {true};
        final boolean was = isGmsAddAccountActivityOnTop();
        final String reason = "GmsAddAccountActivityOnTop";
        dlog("Skip spoofing build for GMS, because " + reason + "!");
        TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean isNow = isGmsAddAccountActivityOnTop();
                if (isNow ^ was) {
                    dlog(String.format("%s changed: isNow=%b, was=%b, killing myself!", reason, isNow, was));
                    shouldCertify[0] = false;
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
        return shouldCertify[0];
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setVersionField(String key, Integer value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            // Edit
            field.set(null, value);
            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void spoofBuildGms() {
        // Alter build parameters to Nexus 5X for avoiding hardware attestation enforcement
        setPropValue("DEVICE", "bullhead");
        setPropValue("ID", "OPR6.170623.013");
        setPropValue("FINGERPRINT", "google/bullhead/bullhead:8.0.0/OPR6.170623.013/4283548:user/release-keys");
        setPropValue("MODEL", "Nexus 5X");
        setPropValue("PRODUCT", "bullhead");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N);
        setVersionFieldString("SECURITY_PATCH", "2017-08-05");
    }

    private static void setVersionFieldString(String key, String value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if ((isCallerSafetyNet() || sIsFinsky) && !sIsSetupWizard && shouldTryToCertifyDevice()) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
