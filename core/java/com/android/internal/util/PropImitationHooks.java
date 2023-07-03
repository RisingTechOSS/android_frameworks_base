/*
 * Copyright (C) 2022 Paranoid Android
 * Copyright (C) 2022 StatiXOS
 * Copyright (C) 2023 the RisingOS Android Project
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

import android.app.Application;
import android.content.res.Resources;
import android.os.Build;
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

public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = false;

    private static final String sCertifiedFp =
            Resources.getSystem().getString(R.string.config_certifiedFingerprint);

    private static final String sStockFp =
            Resources.getSystem().getString(R.string.config_stockFingerprint);

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";

    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_SUBSCRIPTION_RED = "com.google.android.apps.subscriptions.red";
    private static final String PACKAGE_TURBO = "com.google.android.apps.turbo";
    private static final String PACKAGE_VELVET = "com.google.android.googlequicksearchbox";
    private static final String PACKAGE_GBOARD = "com.google.android.inputmethod.latin";
    private static final String PACKAGE_SETUPWIZARD = "com.google.android.setupwizard";

    private static final Map<String, Object> sP7Props = createGoogleSpoofProps("cheetah", "Pixel 7 Pro", "google/cheetah/cheetah:13/TQ3A.230605.012/10204971:user/release-keys");
    private static final Map<String, Object> gPhotosProps = createGoogleSpoofProps("marlin", "Pixel XL", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    private static final Map<String, Object> redfinProps = createGoogleSpoofProps("redfin", "Pixel 5", "google/redfin/redfin:13/TQ3A.230605.011/10161073:user/release-keys");
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

    private static Map<String, Object> createGoogleSpoofProps(String device, String model, String fingerprint) {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "google");
        props.put("MANUFACTURER", "Google");
        props.put("DEVICE", device);
        props.put("PRODUCT", device);
        props.put("MODEL", model);
        props.put("FINGERPRINT", fingerprint);
        return props;
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

    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    public static void setProps(Application app) {
        final String packageName = app.getPackageName();
        final String processName = app.getProcessName();

        if (packageName == null || processName == null) {
            return;
        }

        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);

        if (sIsGms) {
            dlog("Setting Pixel XL fingerprint for: " + packageName);
            spoofBuildGms();
        } else if (!sCertifiedFp.isEmpty() && sIsFinsky) {
            dlog("Setting certified fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sCertifiedFp);
        } else if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
            dlog("Setting stock fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sStockFp);
        } else {
            switch (packageName) {
                case PACKAGE_SUBSCRIPTION_RED:
                case PACKAGE_TURBO:
                case PACKAGE_VELVET:
                case PACKAGE_GBOARD:
                case PACKAGE_SETUPWIZARD:
                case PACKAGE_GMS:
                    dlog("Spoofing Pixel 7 Pro for: " + packageName);
                    sP7Props.forEach((k, v) -> setPropValue(k, v));
                    break;
                case PACKAGE_GPHOTOS:
                    if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", false)) {
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

    private static void setPropValue(String key, Object value){
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
        // Alter model name and fingerprint to avoid hardware attestation enforcement
        setPropValue("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
        setPropValue("PRODUCT", "marlin");
        setPropValue("DEVICE", "marlin");
        setPropValue("MODEL", "Pixel XL");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
      if (DEBUG) Log.d(TAG, msg);
    }
}
