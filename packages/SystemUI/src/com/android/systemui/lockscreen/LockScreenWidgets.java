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

import android.annotation.NonNull;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.hardware.camera2.CameraManager;
import android.media.AppVolume;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.AttributeSet;
import android.os.UserHandle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.Utils;

import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.animation.view.LaunchableImageView;
import com.android.systemui.animation.view.LaunchableFAB;
import com.android.systemui.media.dialog.MediaOutputDialogFactory;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.tiles.dialog.bluetooth.BluetoothTileDialogViewModel;
import com.android.systemui.qs.tiles.dialog.InternetDialogManager;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BluetoothController.Callback;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.MobileDataIndicators;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.settings.SystemSettings;
import com.android.internal.util.android.VibrationUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.internal.util.crdroid.OmniJawsClient;

public class LockScreenWidgets extends LinearLayout implements TunerService.Tunable, OmniJawsClient.OmniJawsObserver {

    private static final String LOCKSCREEN_DISPLAY_WIDGETS =
            "system:lockscreen_display_widgets";

    private static final String LOCKSCREEN_WIDGETS =
            "system:lockscreen_widgets";

    private static final String LOCKSCREEN_WIDGETS_EXTRAS =
            "system:lockscreen_widgets_extras";

    private static final int[] MAIN_WIDGETS_VIEW_IDS = {
            R.id.main_kg_item_placeholder1,
            R.id.main_kg_item_placeholder2
    };

    private static final int[] WIDGETS_VIEW_IDS = {
            R.id.kg_item_placeholder1,
            R.id.kg_item_placeholder2,
            R.id.kg_item_placeholder3,
            R.id.kg_item_placeholder4
    };

    public static final int BT_ACTIVE = R.drawable.qs_bluetooth_icon_on;
    public static final int BT_INACTIVE = R.drawable.qs_bluetooth_icon_off;
    public static final int DATA_ACTIVE = R.drawable.ic_signal_cellular_alt_24;
    public static final int DATA_INACTIVE = R.drawable.ic_mobiledata_off_24;
    public static final int RINGER_ACTIVE = R.drawable.ic_vibration_24;
    public static final int RINGER_INACTIVE = R.drawable.ic_ring_volume_24;
    public static final int TORCH_RES_ACTIVE = R.drawable.ic_flashlight_on;
    public static final int TORCH_RES_INACTIVE = R.drawable.ic_flashlight_off;
    public static final int WIFI_ACTIVE = R.drawable.ic_wifi_24;
    public static final int WIFI_INACTIVE = R.drawable.ic_wifi_off_24;

    public static final int BT_LABEL_INACTIVE = R.string.quick_settings_bluetooth_label;
    public static final int DATA_LABEL_INACTIVE = R.string.quick_settings_data_label;
    public static final int RINGER_LABEL_INACTIVE = R.string.quick_settings_ringer_label;
    public static final int TORCH_LABEL_ACTIVE = R.string.torch_active;
    public static final int TORCH_LABEL_INACTIVE = R.string.quick_settings_flashlight_label;
    public static final int WIFI_LABEL_INACTIVE = R.string.quick_settings_wifi_label;

    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;

    private final AccessPointController mAccessPointController;
    private final BluetoothController mBluetoothController;
    private final BluetoothTileDialogViewModel mBluetoothTileDialogViewModel;
    private final ConfigurationController mConfigurationController;
    private final DataUsageController mDataController;
    private final FlashlightController mFlashlightController;
    private final InternetDialogManager mInternetDialogManager;
    private final MediaOutputDialogFactory mMediaOutputDialogFactory;
    private final NetworkController mNetworkController;
    private final StatusBarStateController mStatusBarStateController;
    private final SystemSettings mSystemSettings;
    private final TunerService mTunerService;

    protected final CellSignalCallback mCellSignalCallback = new CellSignalCallback();
    protected final WifiSignalCallback mWifiSignalCallback = new WifiSignalCallback();

    private Context mContext;
    private LaunchableImageView mWidget1, mWidget2, mWidget3, mWidget4, mediaButton, torchButton, weatherButton;
    private LaunchableFAB mediaButtonFab, torchButtonFab, weatherButtonFab;
    private LaunchableFAB wifiButtonFab, dataButtonFab, ringerButtonFab, btButtonFab;
    private LaunchableImageView wifiButton, dataButton, ringerButton, btButton;
    private int mDarkColor, mDarkColorActive, mLightColor, mLightColorActive;

    private CameraManager mCameraManager;
    private String mCameraId;
    private boolean isFlashOn = false;

    private String mMainLockscreenWidgetsList;
    private String mSecondaryLockscreenWidgetsList;
    private LaunchableFAB[] mMainWidgetViews;
    private LaunchableImageView[] mSecondaryWidgetViews;
    private List<String> mMainWidgetsList = new ArrayList<>();
    private List<String> mSecondaryWidgetsList = new ArrayList<>();
    private String mWidgetImagePath;

    private AudioManager mAudioManager;
    private MediaController mController;
    private MediaMetadata mMediaMetadata;
    private String mLastTrackTitle = null;

    private boolean mDozing;
    
    private boolean mIsInflated = false;
    private GestureDetector mGestureDetector;
    private boolean mIsLongPress = false;

    private boolean mShowDisplayWidgets;
    private Runnable mMediaUpdater;
    private Handler mHandler;

    private ActivityLauncherUtils mActivityLauncherUtils;

    private final MediaController.Callback mMediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            updateMediaController();
        }
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            mMediaMetadata = metadata;
            updateMediaController();
        }
    };

    final ConfigurationListener mConfigurationListener = new ConfigurationListener() {
        @Override
        public void onUiModeChanged() {
            updateWidgetViews();
        }

        @Override
        public void onThemeChanged() {
            updateWidgetViews();
        }
    };

    public LockScreenWidgets(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mDarkColor = mContext.getResources().getColor(R.color.lockscreen_widget_background_color_dark);
        mLightColor = mContext.getResources().getColor(R.color.lockscreen_widget_background_color_light);
        mDarkColorActive = mContext.getResources().getColor(R.color.lockscreen_widget_active_color_dark);
        mLightColorActive = mContext.getResources().getColor(R.color.lockscreen_widget_active_color_light);

        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(this, LOCKSCREEN_DISPLAY_WIDGETS, 
            LOCKSCREEN_WIDGETS, LOCKSCREEN_WIDGETS_EXTRAS);
        mSystemSettings = Dependency.get(SystemSettings.class);
        mAccessPointController = Dependency.get(AccessPointController.class);
        mBluetoothTileDialogViewModel = Dependency.get(BluetoothTileDialogViewModel.class);
        mConfigurationController = Dependency.get(ConfigurationController.class);
        mFlashlightController = Dependency.get(FlashlightController.class);
        mInternetDialogManager = Dependency.get(InternetDialogManager.class);
        mMediaOutputDialogFactory = Dependency.get(MediaOutputDialogFactory.class);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mBluetoothController = Dependency.get(BluetoothController.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mDataController = mNetworkController.getMobileDataController();
        mActivityLauncherUtils = new ActivityLauncherUtils(mContext);
        
        mHandler = Dependency.get(Dependency.MAIN_HANDLER);
        
        if (mWeatherClient == null) {
            mWeatherClient = new OmniJawsClient(context);
        }
        try {
            mCameraId = mCameraManager.getCameraIdList()[0];
        } catch (Exception e) {}
        
        IntentFilter ringerFilter = new IntentFilter(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
        mContext.registerReceiver(mRingerModeReceiver, ringerFilter);
        mMediaUpdater = new Runnable() {
            @Override
            public void run() {
                updateMediaController();
                mHandler.postDelayed(this, 1000);
            }
        };
        updateMediaController();
    }

    public void enableWeatherUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
        }
    }

    public void disableWeatherUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
        }
    }

    @Override
    public void weatherError(int errorReason) {
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void updateSettings() {
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        try {
            if (mWeatherClient == null || !mWeatherClient.isOmniJawsEnabled()) {
                return;
            }
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            if (mWeatherInfo != null) {
            	// OpenWeatherMap
                String formattedCondition = mWeatherInfo.condition;
                if (formattedCondition.toLowerCase().contains("clouds")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_clouds);
                } else if (formattedCondition.toLowerCase().contains("rain")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_rain);
                } else if (formattedCondition.toLowerCase().contains("clear")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_clear);
                } else if (formattedCondition.toLowerCase().contains("storm")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_storm);
                } else if (formattedCondition.toLowerCase().contains("snow")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_snow);
                } else if (formattedCondition.toLowerCase().contains("wind")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_wind);
                } else if (formattedCondition.toLowerCase().contains("mist")) {
                    formattedCondition = mContext.getResources().getString(R.string.weather_condition_mist);
                }
                
				// MET Norway
				if (formattedCondition.toLowerCase().contains("_")) {
					final String[] words = formattedCondition.split("_");
					final StringBuilder formattedConditionBuilder = new StringBuilder();
					for (String word : words) {
						final String capitalizedWord = word.substring(0, 1).toUpperCase() + word.substring(1);
						formattedConditionBuilder.append(capitalizedWord).append(" ");
					}
					formattedCondition = formattedConditionBuilder.toString().trim();
				}
                
                final Drawable d = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
                if (weatherButtonFab != null) {
                	weatherButtonFab.setIcon(d);
                	weatherButtonFab.setText(mWeatherInfo.temp + mWeatherInfo.tempUnits + " \u2022 " + formattedCondition);
                	weatherButtonFab.setIconTint(null);
                }
                if (weatherButton != null) {
                	weatherButton.setImageDrawable(d);
                	weatherButton.setImageTintList(null);
                }
            }
        } catch(Exception e) {
            // Do nothing
        }
    }

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
        @Override
        public void onStateChanged(int newState) {}

        @Override
        public void onDozingChanged(boolean dozing) {
            if (mDozing == dozing) {
                return;
            }
            mDozing = dozing;
            updateContainerVisibility();
        }
    };

    private final FlashlightController.FlashlightListener mFlashlightCallback =
            new FlashlightController.FlashlightListener() {

        @Override
        public void onFlashlightChanged(boolean enabled) {
            isFlashOn = enabled;
            updateTorchButtonState();
        }

        @Override
        public void onFlashlightError() {
        }

        @Override
        public void onFlashlightAvailabilityChanged(boolean available) {
            isFlashOn = mFlashlightController.isEnabled() && available;
            updateTorchButtonState();
        }
    };

    private boolean isMediaControllerAvailable() {
        final MediaController mediaController = getActiveLocalMediaController();
        return mediaController != null && !TextUtils.isEmpty(mediaController.getPackageName());
    }
    
    private MediaController getActiveLocalMediaController() {
        MediaSessionManager mediaSessionManager =
                mContext.getSystemService(MediaSessionManager.class);
        MediaController localController = null;
        final List<String> remoteMediaSessionLists = new ArrayList<>();
        for (MediaController controller : mediaSessionManager.getActiveSessions(null)) {
            final MediaController.PlaybackInfo pi = controller.getPlaybackInfo();
            if (pi == null) {
                continue;
            }
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState == null) {
                continue;
            }
            if (playbackState.getState() != PlaybackState.STATE_PLAYING) {
                continue;
            }
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                if (localController != null
                        && TextUtils.equals(
                                localController.getPackageName(), controller.getPackageName())) {
                    localController = null;
                }
                if (!remoteMediaSessionLists.contains(controller.getPackageName())) {
                    remoteMediaSessionLists.add(controller.getPackageName());
                }
                continue;
            }
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                if (localController == null
                        && !remoteMediaSessionLists.contains(controller.getPackageName())) {
                    localController = controller;
                }
            }
        }
        return localController;
    }
    
    private boolean isWidgetEnabled(String widget) {
        return (mMainLockscreenWidgetsList != null 
            && !mMainLockscreenWidgetsList.contains(widget)) 
        	|| (mSecondaryLockscreenWidgetsList != null 
        	&& !mSecondaryLockscreenWidgetsList.contains(widget));
    }

    private void updateMediaController() {
        if (!isWidgetEnabled("media")) return;
        MediaController localController = getActiveLocalMediaController();
        if (localController != null && !sameSessions(mController, localController)) {
            if (mController != null) {
                mController.unregisterCallback(mMediaCallback);
                mController = null;
            }
            mController = localController;
            mController.registerCallback(mMediaCallback);
        }
        mMediaMetadata = isMediaControllerAvailable() ? mController.getMetadata() : null;
        updateMediaState();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE && isAttachedToWindow()) {
            updateMediaController();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isWidgetEnabled("wifi")) {
            mNetworkController.addCallback(mWifiSignalCallback);
        }
        if (isWidgetEnabled("data")) {
            mNetworkController.addCallback(mCellSignalCallback);
        }
        if (isWidgetEnabled("bt")) {
            mBluetoothController.addCallback(mBtCallback);
        }
        if (isWidgetEnabled("torch")) {
            mFlashlightController.addCallback(mFlashlightCallback);
        }
        mConfigurationController.addCallback(mConfigurationListener);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
        updateWidgetViews();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isWidgetEnabled("weather")) {
        	disableWeatherUpdates();
        }
        if (isWidgetEnabled("wifi")) {
            mNetworkController.removeCallback(mWifiSignalCallback);
        }
        if (isWidgetEnabled("data")) {
            mNetworkController.removeCallback(mCellSignalCallback);
        }
        if (isWidgetEnabled("bt")) {
            mBluetoothController.removeCallback(mBtCallback);
        }
        if (isWidgetEnabled("torch")) {
            mFlashlightController.removeCallback(mFlashlightCallback);
        }
        mConfigurationController.removeCallback(mConfigurationListener);
        mStatusBarStateController.removeCallback(mStatusBarStateListener);
        if (mController != null) {
            mController.unregisterCallback(mMediaCallback);
            mController = null;
        }
        mContext.unregisterReceiver(mRingerModeReceiver);
        mTunerService.removeTunable(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMainWidgetViews = new LaunchableFAB[MAIN_WIDGETS_VIEW_IDS.length];
        for (int i = 0; i < mMainWidgetViews.length; i++) {
            mMainWidgetViews[i] = findViewById(MAIN_WIDGETS_VIEW_IDS[i]);
        }
        mSecondaryWidgetViews = new LaunchableImageView[WIDGETS_VIEW_IDS.length];
        for (int i = 0; i < mSecondaryWidgetViews.length; i++) {
            mSecondaryWidgetViews[i] = findViewById(WIDGETS_VIEW_IDS[i]);
        }
        mIsInflated = true;
        updateWidgetViews();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_DISPLAY_WIDGETS:
                mShowDisplayWidgets = TunerService.parseIntegerSwitch(newValue, false);
                updateWidgetViews();
                break;
            case LOCKSCREEN_WIDGETS:
                mMainLockscreenWidgetsList = newValue;
                if (mMainLockscreenWidgetsList != null) {
                    mMainWidgetsList = Arrays.asList(mMainLockscreenWidgetsList.split(","));
                }
                updateWidgetViews();
                break;
            case LOCKSCREEN_WIDGETS_EXTRAS:
                mSecondaryLockscreenWidgetsList = newValue;
                if (mSecondaryLockscreenWidgetsList != null) {
                    mSecondaryWidgetsList = Arrays.asList(mSecondaryLockscreenWidgetsList.split(","));
                }
                updateWidgetViews();
                break;
            default:
                break;
        }
    }

    private void updateContainerVisibility() {
        final boolean isMainWidgetsEmpty = mMainLockscreenWidgetsList == null 
            || TextUtils.isEmpty(mMainLockscreenWidgetsList);
        final boolean isSecondaryWidgetsEmpty = mSecondaryLockscreenWidgetsList == null 
            || TextUtils.isEmpty(mSecondaryLockscreenWidgetsList);
        final boolean isEmpty = isMainWidgetsEmpty && isSecondaryWidgetsEmpty;
        final boolean lockscreenWidgetsEnabled = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                "lockscreen_widgets_enabled",
                0,
                UserHandle.USER_CURRENT) != 0;
        final View mainWidgetsContainer = findViewById(R.id.main_widgets_container);
        if (mainWidgetsContainer != null) {
            mainWidgetsContainer.setVisibility(isMainWidgetsEmpty ? View.GONE : View.VISIBLE);
        }
        final View secondaryWidgetsContainer = findViewById(R.id.secondary_widgets_container);
        if (secondaryWidgetsContainer != null) {
            secondaryWidgetsContainer.setVisibility(isSecondaryWidgetsEmpty ? View.GONE : View.VISIBLE);
        }
        final View displayWidgets = findViewById(R.id.display_widgets);
        if (displayWidgets != null) {
            displayWidgets.setVisibility(mShowDisplayWidgets ? View.VISIBLE : View.GONE);
        }
        final boolean shouldHideContainer = isEmpty || mDozing || !lockscreenWidgetsEnabled;
        setVisibility(shouldHideContainer ? View.GONE : View.VISIBLE);
    }

    public void updateWidgetViews() {
        if (!mIsInflated) return;
        if (mMainWidgetViews != null && mMainWidgetsList != null) {
            for (int i = 0; i < mMainWidgetViews.length; i++) {
                if (mMainWidgetViews[i] != null) {
                    mMainWidgetViews[i].setVisibility(i < mMainWidgetsList.size() ? View.VISIBLE : View.GONE);
                }
            }
            for (int i = 0; i < Math.min(mMainWidgetsList.size(), mMainWidgetViews.length); i++) {
                String widgetType = mMainWidgetsList.get(i);
                if (widgetType != null && i < mMainWidgetViews.length && mMainWidgetViews[i] != null) {
                    setUpWidgetWiews(null, mMainWidgetViews[i], widgetType);
                    updateMainWidgetResources(mMainWidgetViews[i], false);
                }
            }
        }
        if (mSecondaryWidgetViews != null && mSecondaryWidgetsList != null) {
            for (int i = 0; i < mSecondaryWidgetViews.length; i++) {
                if (mSecondaryWidgetViews[i] != null) {
                    mSecondaryWidgetViews[i].setVisibility(i < mSecondaryWidgetsList.size() ? View.VISIBLE : View.GONE);
                }
            }
            for (int i = 0; i < Math.min(mSecondaryWidgetsList.size(), mSecondaryWidgetViews.length); i++) {
                String widgetType = mSecondaryWidgetsList.get(i);
                if (widgetType != null && i < mSecondaryWidgetViews.length && mSecondaryWidgetViews[i] != null) {
                    setUpWidgetWiews(mSecondaryWidgetViews[i], null, widgetType);
                    updateWidgetsResources(mSecondaryWidgetViews[i]);
                }
            }
        }
        updateContainerVisibility();
        updateMediaController();
    }

    private void updateMainWidgetResources(LaunchableFAB efab, boolean active) {
        if (efab == null) return;
        efab.setElevation(0);
        setButtonActiveState(null, efab, false);
        long visibleWidgetCount = mMainWidgetsList.stream().filter(widget -> !"none".equals(widget)).count();
        ViewGroup.LayoutParams params = efab.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) params;
            if (efab.getVisibility() == View.VISIBLE && visibleWidgetCount == 1) {
                layoutParams.width = getResources().getDimensionPixelSize(R.dimen.kg_widget_main_width);
                layoutParams.height = getResources().getDimensionPixelSize(R.dimen.kg_widget_main_height);
            } else {
                layoutParams.width = 0;
                layoutParams.weight = 1;
            }
            efab.setLayoutParams(layoutParams);
        }
    }

    private void updateWidgetsResources(LaunchableImageView iv) {
        if (iv == null) return;
        iv.setBackgroundResource(R.drawable.lockscreen_widget_background_circle);
        setButtonActiveState(iv, null, false);
    }

    private boolean isNightMode() {
        final Configuration config = mContext.getResources().getConfiguration();
        return (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setUpWidgetWiews(LaunchableImageView iv, LaunchableFAB efab, String type) {
        switch (type) {
            case "none":
                if (iv != null) {
                    iv.setVisibility(View.GONE);
                }
                if (efab != null) {
                    efab.setVisibility(View.GONE);
                }
                break;
            case "wifi":
                if (iv != null) {
                    wifiButton = iv;
                    wifiButton.setOnLongClickListener(v -> { showInternetDialog(v); return true; });
                }
                if (efab != null) {
                    wifiButtonFab = efab;
                    wifiButtonFab.setOnLongClickListener(v -> { showInternetDialog(v); return true; });
                }
                setUpWidgetResources(iv, efab, v -> toggleWiFi(), WIFI_INACTIVE, R.string.quick_settings_wifi_label);
                break;
            case "data":
                if (iv != null) {
                    dataButton = iv;
                    dataButton.setOnLongClickListener(v -> { showInternetDialog(v); return true; });
                }
                if (efab != null) {
                    dataButtonFab = efab;
                    dataButtonFab.setOnLongClickListener(v -> { showInternetDialog(v); return true; });
                }
                setUpWidgetResources(iv, efab, v -> toggleMobileData(), DATA_INACTIVE, DATA_LABEL_INACTIVE);
                break;
            case "ringer":
                if (iv != null) {
                    ringerButton = iv;
                }
                if (efab != null) {
                    ringerButtonFab = efab;
                }
                setUpWidgetResources(iv, efab, v -> toggleRingerMode(), RINGER_INACTIVE, RINGER_LABEL_INACTIVE);
                break;
            case "bt":
                if (iv != null) {
                    btButton = iv;
                    btButton.setOnLongClickListener(v -> { showBluetoothDialog(v); return true; });
                }
                if (efab != null) {
                    btButtonFab = efab;
                    btButtonFab.setOnLongClickListener(v -> { showBluetoothDialog(v); return true; });
                }
                setUpWidgetResources(iv, efab, v -> toggleBluetoothState(), BT_INACTIVE, BT_LABEL_INACTIVE);
                break;
            case "torch":
                if (iv != null) {
                    torchButton = iv;
                }
                if (efab != null) {
                    torchButtonFab = efab;
                }
                setUpWidgetResources(iv, efab, v -> toggleFlashlight(), TORCH_RES_INACTIVE, TORCH_LABEL_INACTIVE);
                break;
            case "timer":
                setUpWidgetResources(iv, efab, v -> mActivityLauncherUtils.launchTimer(), R.drawable.ic_alarm, R.string.clock_timer);
                break;
            case "calculator":
                setUpWidgetResources(iv, efab, v -> mActivityLauncherUtils.launchCalculator(), R.drawable.ic_calculator, R.string.calculator);
                break;
            case "media":
                if (iv != null) {
                    mediaButton = iv;
                    mediaButton.setOnLongClickListener(v -> { showMediaDialog(v); return true; });
                }
                if (efab != null) {
                    mediaButtonFab = efab;
                }
                setUpWidgetResources(iv, efab, v -> toggleMediaPlaybackState(), R.drawable.ic_media_play, R.string.controls_media_button_play);
                break;
			case "weather":
                if (iv != null) {
                    weatherButton = iv;
                }
                if (efab != null) {
                    weatherButtonFab = efab;
                }
                setUpWidgetResources(iv, efab, v -> mActivityLauncherUtils.launchWeatherApp(), R.drawable.ic_weather, R.string.weather_data_unavailable);
                enableWeatherUpdates();
                break;
            default:
                break;
        }
    }

    private void setUpWidgetResources(LaunchableImageView iv, LaunchableFAB efab, 
        View.OnClickListener cl, int drawableRes, int stringRes){
        if (efab != null) {
            efab.setOnClickListener(cl);
            efab.setIcon(mContext.getDrawable(drawableRes));
            efab.setText(mContext.getResources().getString(stringRes));
            if (mediaButtonFab == efab) {
                attachSwipeGesture(efab);
            }
        }
        if (iv != null) {
            iv.setOnClickListener(cl);
            iv.setImageResource(drawableRes);
        }
    }

    private void attachSwipeGesture(LaunchableFAB efab) {
        final GestureDetector gestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                        VibrationUtils.triggerVibration(mContext, 2);
                        updateMediaController();
                    } else {
                        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT);
                        VibrationUtils.triggerVibration(mContext, 2);
                        updateMediaController();
                    }
                    return true;
                }
                return false;
            }
            
            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
                mIsLongPress = true;
                showMediaDialog(efab);
                mHandler.postDelayed(() -> {
                    mIsLongPress = false;
                }, 2500);
            }
        });
        efab.setOnTouchListener((v, event) -> {
            boolean isClick = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && !isClick && !mIsLongPress) {
                v.performClick();
            }
            return true;
        });
    }

    private void setButtonActiveState(LaunchableImageView iv, LaunchableFAB efab, boolean active) {
        int bgTint;
        int tintColor;
        if (active) {
            bgTint = isNightMode() ? mDarkColorActive : mLightColorActive;
            tintColor = isNightMode() ? mDarkColor : mLightColor;
        } else {
            bgTint = isNightMode() ? mDarkColor : mLightColor;
            tintColor = isNightMode() ? mLightColor : mDarkColor;
        }
        if (iv != null) {
            iv.setBackgroundTintList(ColorStateList.valueOf(bgTint));
            if (iv != weatherButton) {
            	iv.setImageTintList(ColorStateList.valueOf(tintColor));
            } else {
            	iv.setImageTintList(null);
            }
        }
        if (efab != null) {
            efab.setBackgroundTintList(ColorStateList.valueOf(bgTint));
            if (efab != weatherButtonFab) {
            	efab.setIconTint(ColorStateList.valueOf(tintColor));
            } else {
            	efab.setIconTint(null);
            }
            efab.setTextColor(tintColor);
        }
    }
    
    public void updateMediaState() {
        updateMediaPlaybackState();
        mHandler.postDelayed(() -> {
            updateMediaPlaybackState();
        }, 250);
    }

    private void toggleMediaPlaybackState() {
        if (isMediaPlaying()) {
            mHandler.removeCallbacks(mMediaUpdater);
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PAUSE);
            updateMediaController();
        } else {
            mMediaUpdater.run();
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }
    
    private void showMediaDialog(View view) {
        updateMediaController();
        post(() -> 
            mMediaOutputDialogFactory.create(getActiveVolumeApp(), true, view));
        VibrationUtils.triggerVibration(mContext, 2);
    }

    private String getActiveVolumeApp() {
        String mAppVolumeActivePackageName = "";
        for (AppVolume av : mAudioManager.listAppVolumes()) {
            if (av.isActive()) {
                mAppVolumeActivePackageName = av.getPackageName();
                break;
            }
        }
        return mAppVolumeActivePackageName;
    }

    private void dispatchMediaKeyWithWakeLockToMediaSession(final int keycode) {
        final MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        if (helper == null) {
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
        helper.sendMediaButtonEvent(event, true);
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        helper.sendMediaButtonEvent(event, true);
    }

    private void updateMediaPlaybackState() {
        boolean isPlaying = isMediaPlaying();
        int stateIcon = isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play;
        if (mediaButton != null) {
            mediaButton.setImageResource(stateIcon);
            setButtonActiveState(mediaButton, null, isPlaying);
        }
        if (mediaButtonFab != null) {
            String trackTitle = mMediaMetadata != null ? mMediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "";
            if (!TextUtils.isEmpty(trackTitle) && mLastTrackTitle != trackTitle) {
                mLastTrackTitle = trackTitle;
            }
            final boolean canShowTrackTitle = isPlaying || !TextUtils.isEmpty(mLastTrackTitle);
            mediaButtonFab.setIcon(mContext.getDrawable(isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play));
            mediaButtonFab.setText(canShowTrackTitle ? mLastTrackTitle : mContext.getResources().getString(R.string.controls_media_button_play));
            setButtonActiveState(null, mediaButtonFab, isPlaying);
        }
    }
    
    private boolean isMediaPlaying() {
        return isMediaControllerAvailable() 
            && PlaybackState.STATE_PLAYING == getMediaControllerPlaybackState(mController);
    }

    private void toggleFlashlight() {
        if (torchButton == null && torchButtonFab == null) return;
        try {
            mCameraManager.setTorchMode(mCameraId, !isFlashOn);
            isFlashOn = !isFlashOn;
            updateTorchButtonState();
        } catch (Exception e) {}
    }

    private void toggleWiFi() {
        final WifiCallbackInfo cbi = mWifiSignalCallback.mInfo;
        mNetworkController.setWifiEnabled(!cbi.enabled);
        updateWiFiButtonState(!cbi.enabled);
        mHandler.postDelayed(() -> {
            updateWiFiButtonState(cbi.enabled);
        }, 250);
    }

    private boolean isMobileDataEnabled() {
        return mDataController.isMobileDataEnabled();
    }

    private void toggleMobileData() {
        mDataController.setMobileDataEnabled(!isMobileDataEnabled());
        updateMobileDataState(!isMobileDataEnabled());
        mHandler.postDelayed(() -> {
            updateMobileDataState(isMobileDataEnabled());
        }, 250);
    }
    
    private void showInternetDialog(View view) {
        post(() -> mInternetDialogManager.create(true,
                mAccessPointController.canConfigMobileData(),
                mAccessPointController.canConfigWifi(), view));
        VibrationUtils.triggerVibration(mContext, 2);
    }

    private void toggleRingerMode() {
        if (mAudioManager != null) {
            int mode = mAudioManager.getRingerMode();
            if (mode == mAudioManager.RINGER_MODE_NORMAL) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
            updateRingerButtonState();
        }
    }

    private void updateTileButtonState(
            LaunchableImageView iv, 
            LaunchableFAB efab, 
            boolean active, 
            int activeResource, 
            int inactiveResource,
            String activeString,
            String inactiveString) {
        post(new Runnable() {
            @Override
            public void run() {
                if (iv != null) {
                    iv.setImageResource(active ? activeResource : inactiveResource);
                    setButtonActiveState(iv, null, active);
                }
                if (efab != null) {
                    efab.setIcon(mContext.getDrawable(active ? activeResource : inactiveResource));
                    efab.setText(active ? activeString : inactiveString);
                    setButtonActiveState(null, efab, active);
                }
            }
        });
    }
    
    public void updateTorchButtonState() {
        if (!isWidgetEnabled("torch")) return;
        String activeString = mContext.getResources().getString(TORCH_LABEL_ACTIVE);
        String inactiveString = mContext.getResources().getString(TORCH_LABEL_INACTIVE);
        updateTileButtonState(torchButton, torchButtonFab, isFlashOn, 
            TORCH_RES_ACTIVE, TORCH_RES_INACTIVE, activeString, inactiveString);
    }

    private BroadcastReceiver mRingerModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRingerButtonState();
        }
    };

    private final BluetoothController.Callback mBtCallback = new BluetoothController.Callback() {
        @Override
        public void onBluetoothStateChange(boolean enabled) {
            updateBtState();
        }

        @Override
        public void onBluetoothDevicesChanged() {
            updateBtState();
        }
    };

    private void updateWiFiButtonState(boolean enabled) {
        if (!isWidgetEnabled("wifi")) return;
        if (wifiButton == null && wifiButtonFab == null) return;
        final WifiCallbackInfo cbi = mWifiSignalCallback.mInfo;
        String inactiveString = mContext.getResources().getString(WIFI_LABEL_INACTIVE);
        updateTileButtonState(wifiButton, wifiButtonFab, enabled, 
            WIFI_ACTIVE, WIFI_INACTIVE, cbi.ssid != null ? removeDoubleQuotes(cbi.ssid) : inactiveString, inactiveString);
    }

    private void updateRingerButtonState() {
        if (!isWidgetEnabled("ringer")) return;
        if (ringerButton == null && ringerButtonFab == null) return;
        if (mAudioManager != null) {
            boolean isVibrateActive = mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;
            String inactiveString = mContext.getResources().getString(RINGER_LABEL_INACTIVE);
            updateTileButtonState(ringerButton, ringerButtonFab, isVibrateActive, 
                RINGER_ACTIVE, RINGER_INACTIVE, inactiveString, inactiveString);
        }
    }

    private void updateMobileDataState(boolean enabled) {
        if (!isWidgetEnabled("data")) return;
        if (dataButton == null && dataButtonFab == null) return;
        String networkName = mNetworkController == null ? "" : mNetworkController.getMobileDataNetworkName();
        boolean hasNetwork = !TextUtils.isEmpty(networkName) && mNetworkController != null 
            && mNetworkController.hasMobileDataFeature();
        String inactiveString = mContext.getResources().getString(DATA_LABEL_INACTIVE);
        updateTileButtonState(dataButton, dataButtonFab, enabled, 
            DATA_ACTIVE, DATA_INACTIVE, hasNetwork && enabled ? networkName : inactiveString, inactiveString);
    }
    
    private void toggleBluetoothState() {
        mBluetoothController.setBluetoothEnabled(!isBluetoothEnabled());
        updateBtState();
        mHandler.postDelayed(() -> {
            updateBtState();
        }, 250);
    }
    
    private void showBluetoothDialog(View view) {
        boolean isAutoOn = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_BT_AUTO_ON, 0) == 1;
        post(() -> 
            mBluetoothTileDialogViewModel.showDialog(mContext, view, isAutoOn));
        VibrationUtils.triggerVibration(mContext, 2);
    }
    
    private void updateBtState() {
        if (!isWidgetEnabled("bt")) return;
        if (btButton == null && btButtonFab == null) return;
        String deviceName = isBluetoothEnabled() ? mBluetoothController.getConnectedDeviceName() : "";
        boolean isConnected = !TextUtils.isEmpty(deviceName);
        String inactiveString = mContext.getResources().getString(BT_LABEL_INACTIVE);
        updateTileButtonState(btButton, btButtonFab, isBluetoothEnabled(), 
            BT_ACTIVE, BT_INACTIVE, isConnected ? deviceName : inactiveString, inactiveString);
    }
    
    private boolean isBluetoothEnabled() {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    @Nullable
    private static String removeDoubleQuotes(String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    protected static final class WifiCallbackInfo {
        boolean enabled;
        @Nullable
        String ssid;
    }

    protected final class WifiSignalCallback implements SignalCallback {
        final WifiCallbackInfo mInfo = new WifiCallbackInfo();
        @Override
        public void setWifiIndicators(@NonNull WifiIndicators indicators) {
            if (indicators.qsIcon == null) {
                updateWiFiButtonState(false);
                return;
            }
            mInfo.enabled = indicators.enabled;
            mInfo.ssid = indicators.description;
            updateWiFiButtonState(mInfo.enabled);
        }
    }

    private final class CellSignalCallback implements SignalCallback {
        @Override
        public void setMobileDataIndicators(@NonNull MobileDataIndicators indicators) {
            if (indicators.qsIcon == null) {
                updateMobileDataState(false);
                return;
            }
            updateMobileDataState(isMobileDataEnabled());
        }
        @Override
        public void setNoSims(boolean show, boolean simDetected) {
            updateMobileDataState(simDetected && isMobileDataEnabled());
        }
        @Override
        public void setIsAirplaneMode(@NonNull IconState icon) {
            updateMobileDataState(!icon.visible && isMobileDataEnabled());
        }
    }
    
    private boolean sameSessions(MediaController a, MediaController b) {
        if (a == b) {
            return true;
        }
        if (a == null) {
            return false;
        }
        return a.controlsSameSession(b);
    }
    
    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }
}
