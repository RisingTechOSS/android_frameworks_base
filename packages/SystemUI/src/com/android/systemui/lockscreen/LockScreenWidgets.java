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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.AttributeSet;
import android.os.UserHandle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.View;
import androidx.annotation.StringRes;

import com.android.settingslib.Utils;

import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.tuner.TunerService;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.android.internal.util.crdroid.OmniJawsClient;

public class LockScreenWidgets extends LinearLayout implements TunerService.Tunable, OmniJawsClient.OmniJawsObserver {

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

    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;

    private ActivityStarter mActivityStarter;
    private ConfigurationController mConfigurationController;
    private FlashlightController mFlashlightController;
    private StatusBarStateController mStatusBarStateController;

    private Context mContext;
    private ImageView mWidget1, mWidget2, mWidget3, mWidget4, mediaButton, torchButton, weatherButton;
    private ExtendedFloatingActionButton mediaButtonFab, torchButtonFab, weatherButtonFab;
    private int mDarkColor, mDarkColorActive, mLightColor, mLightColorActive;

    private CameraManager mCameraManager;
    private String mCameraId;
    private boolean isFlashOn = false;

    private String mMainLockscreenWidgetsList;
    private String mSecondaryLockscreenWidgetsList;
    private ExtendedFloatingActionButton[] mMainWidgetViews;
    private ImageView[] mSecondaryWidgetViews;
    private List<String> mMainWidgetsList = new ArrayList<>();
    private List<String> mSecondaryWidgetsList = new ArrayList<>();
    private String mWidgetImagePath;

    private AudioManager mAudioManager;
    private Metadata mMetadata = new Metadata();
    private RemoteController mRemoteController;
    private boolean mMediaActive = false;
    
    private boolean mDozing;

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
        mRemoteController = new RemoteController(mContext, mRCClientUpdateListener);
        mAudioManager.registerRemoteController(mRemoteController);
        mDarkColor = mContext.getResources().getColor(R.color.lockscreen_widget_background_color_dark);
        mLightColor = mContext.getResources().getColor(R.color.lockscreen_widget_background_color_light);
        mDarkColorActive = mContext.getResources().getColor(R.color.lockscreen_widget_active_color_dark);
        mLightColorActive = mContext.getResources().getColor(R.color.lockscreen_widget_active_color_light);
        if (mWeatherClient == null) {
            mWeatherClient = new OmniJawsClient(context);
        }
        try {
            mCameraId = mCameraManager.getCameraIdList()[0];
        } catch (Exception e) {}
        Dependency.get(TunerService.class).addTunable(this, LOCKSCREEN_WIDGETS, LOCKSCREEN_WIDGETS_EXTRAS);
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
                	weatherButtonFab.setText(formattedCondition);
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
    
    public void setActivityStarter(ActivityStarter as) {
        mActivityStarter = as;
    }

    public void setConfigurationController(ConfigurationController cc) {
        mConfigurationController = cc;
        if (mConfigurationController != null) {
            mConfigurationController.addCallback(mConfigurationListener);
        }
    }

    public void setFlashLightController(FlashlightController fc) {
        mFlashlightController = fc;
        if (mFlashlightController != null) {
            mFlashlightController.addCallback(mFlashlightCallback);
        }
    }
    
    public void setStatusBarStateController(StatusBarStateController sbsc) {
		mStatusBarStateController = sbsc;
		if (mStatusBarStateController != null) {
		    mStatusBarStateController.addCallback(mStatusBarStateListener);
		    mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
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
            updateFlashLightButtonState();
        }

        @Override
        public void onFlashlightError() {
        }

        @Override
        public void onFlashlightAvailabilityChanged(boolean available) {
            isFlashOn = mFlashlightController.isEnabled() && available;
            updateFlashLightButtonState();
        }
    };

   private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mMediaActive = false;
            }
            updateMediaPlaybackState();
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            updateMediaPlaybackState();
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            updateMediaPlaybackState();
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        private String trackTitle;
         public void clear() {
            trackTitle = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateWidgetViews();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mConfigurationController != null) {
            mConfigurationController.removeCallback(mConfigurationListener);
        }
        if (mFlashlightController != null) {
            mFlashlightController.removeCallback(mFlashlightCallback);
        }
        if (mMainLockscreenWidgetsList != null 
            && !mMainLockscreenWidgetsList.contains("weather") 
        	&& mSecondaryLockscreenWidgetsList != null 
        	&& !mSecondaryLockscreenWidgetsList.contains("weather")) {
        	disableWeatherUpdates();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMainWidgetViews = new ExtendedFloatingActionButton[MAIN_WIDGETS_VIEW_IDS.length];
        for (int i = 0; i < mMainWidgetViews.length; i++) {
            mMainWidgetViews[i] = findViewById(MAIN_WIDGETS_VIEW_IDS[i]);
        }
        mSecondaryWidgetViews = new ImageView[WIDGETS_VIEW_IDS.length];
        for (int i = 0; i < mSecondaryWidgetViews.length; i++) {
            mSecondaryWidgetViews[i] = findViewById(WIDGETS_VIEW_IDS[i]);
        }
        updateWidgetViews();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_WIDGETS:
                mMainLockscreenWidgetsList = (String) newValue;
                if (mMainLockscreenWidgetsList != null) {
                    mMainWidgetsList = Arrays.asList(mMainLockscreenWidgetsList.split(","));
                }
                updateWidgetViews();
                break;
            case LOCKSCREEN_WIDGETS_EXTRAS:
                mSecondaryLockscreenWidgetsList = (String) newValue;
                if (mSecondaryLockscreenWidgetsList != null) {
                    mSecondaryWidgetsList = Arrays.asList(mSecondaryLockscreenWidgetsList.split(","));
                }
                updateWidgetViews();
                break;
            default:
                break;
        }
    }

    private void playbackStateUpdate(int state) {
        if (mediaButton == null && mediaButtonFab == null) return;
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mMediaActive) {
            mMediaActive = active;
        }
        updateMediaPlaybackState();
    }

    private void updateContainerVisibility() {
        final boolean isMainWidgetsEmpty = TextUtils.isEmpty(mMainLockscreenWidgetsList) 
        	|| mMainLockscreenWidgetsList == null;
        final boolean isSecondaryWidgetsEmpty = TextUtils.isEmpty(mSecondaryLockscreenWidgetsList) 
        	|| mSecondaryLockscreenWidgetsList == null;
        final boolean isEmpty = isMainWidgetsEmpty && isSecondaryWidgetsEmpty;
        final View mainWidgetsContainer = findViewById(R.id.main_widgets_container);
        if (mainWidgetsContainer != null) {
            mainWidgetsContainer.setVisibility(isMainWidgetsEmpty ? View.GONE : View.VISIBLE);
        }
        final View secondaryWidgetsContainer = findViewById(R.id.secondary_widgets_container);
        if (secondaryWidgetsContainer != null) {
            secondaryWidgetsContainer.setVisibility(isSecondaryWidgetsEmpty ? View.GONE : View.VISIBLE);
        }
        final boolean shouldHideContainer = isEmpty || mDozing;
        setVisibility(shouldHideContainer ? View.GONE : View.VISIBLE);
    }

    private void updateWidgetViews() {
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
    }

    private void updateMainWidgetResources(ExtendedFloatingActionButton efab, boolean active) {
        if (efab == null) return;
        efab.setElevation(0);
        setButtonActiveState(null, efab, false);
    }

    private void updateWidgetsResources(ImageView iv) {
        if (iv == null) return;
        iv.setBackgroundResource(R.drawable.lockscreen_widget_background_circle);
        setButtonActiveState(iv, null, false);
    }

    private boolean isNightMode() {
        final Configuration config = mContext.getResources().getConfiguration();
        return (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setUpWidgetWiews(ImageView iv, ExtendedFloatingActionButton efab, String type) {
        switch (type) {
            case "torch":
                if (iv != null) {
                    torchButton = iv;
                }
                if (efab != null) {
                    torchButtonFab = efab;
                }
                setUpWidgetResources(iv, efab, v -> toggleFlashlight(), R.drawable.ic_flashlight_off, R.string.quick_settings_flashlight_label);
                break;
            case "timer":
                setUpWidgetResources(iv, efab, v -> launchTimer(), R.drawable.ic_alarm, R.string.clock_timer);
                break;
            case "calculator":
                setUpWidgetResources(iv, efab, v -> launchCalculator(), R.drawable.ic_calculator, R.string.calculator);
                break;
            case "media":
                if (iv != null) {
                    mediaButton = iv;
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
                setUpWidgetResources(iv, efab, v -> launchWeatherApp(), R.drawable.ic_weather, R.string.weather_data_unavailable);
                enableWeatherUpdates();
                break;
            default:
                break;
        }
    }

    private void setUpWidgetResources(ImageView iv, ExtendedFloatingActionButton efab, View.OnClickListener cl, int drawableRes, int stringRes){
        if (efab != null) {
            efab.setOnClickListener(cl);
            efab.setIcon(mContext.getDrawable(drawableRes));
            efab.setText(mContext.getResources().getString(stringRes));
        }
        if (iv != null) {
            iv.setOnClickListener(cl);
            iv.setImageResource(drawableRes);
        }
    }

    private void setButtonActiveState(ImageView iv, ExtendedFloatingActionButton efab, boolean active) {
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

    private void toggleMediaPlaybackState() {
        if (isMusicActive()) {
            final KeyEvent pauseDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
            final KeyEvent pauseUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE);
            mAudioManager.dispatchMediaKeyEvent(pauseDownEvent);
            mAudioManager.dispatchMediaKeyEvent(pauseUpEvent);
        } else {
            launchMusicPlayerApp();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    final KeyEvent playDownEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
                    final KeyEvent playUpEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY);
                    mAudioManager.dispatchMediaKeyEvent(playDownEvent);
                    mAudioManager.dispatchMediaKeyEvent(playUpEvent);
                }
            }, 500);
        }
        updateMediaPlaybackState();
    }

    private void launchMusicPlayerApp() {
        PackageManager packageManager = mContext.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_MUSIC);
        List<ResolveInfo> musicApps = packageManager.queryIntentActivities(intent, 0);
        if (!musicApps.isEmpty()) {
            ResolveInfo musicApp = musicApps.get(0);
            String packageName = musicApp.activityInfo.packageName;
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(launchIntent);
            }
        }
    }

    private boolean isMusicActive() {
        return mMediaActive && mAudioManager.isMusicActive();
    }

    private void updateMediaPlaybackState() {
        final boolean isPlaying = isMusicActive();
        final int stateIcon = isPlaying
                ? R.drawable.ic_media_pause
                : R.drawable.ic_media_play;
        if (mediaButton != null) {
            mediaButton.setImageResource(stateIcon);
            setButtonActiveState(mediaButton, null, isPlaying);
        }
        if (mediaButtonFab != null) {
            final boolean canShowTrackTitle = isPlaying || mMetadata.trackTitle != null;
            mediaButtonFab.setIcon(mContext.getDrawable(isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play));
            mediaButtonFab.setText(canShowTrackTitle ? mMetadata.trackTitle : mContext.getResources().getString(R.string.controls_media_button_play));
            setButtonActiveState(null, mediaButtonFab, isPlaying);
        }
    }

    private void launchAppIfAvailable(Intent launchIntent, @StringRes int appTypeResId) {
        final PackageManager packageManager = mContext.getPackageManager();
        final List<ResolveInfo> apps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!apps.isEmpty() && mActivityStarter != null) {
            mActivityStarter.startActivity(launchIntent, true);
        } else {
            showNoDefaultAppFoundToast(appTypeResId);
        }
    }

    private void launchTimer() {
        final Intent launchIntent = new Intent(AlarmClock.ACTION_SET_TIMER);
        launchAppIfAvailable(launchIntent, R.string.clock_timer);
    }

    private void launchCalculator() {
        final Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
        launchAppIfAvailable(launchIntent, R.string.calculator);
    }

	private void launchWeatherApp() {
		final Intent launchIntent = new Intent();
		launchIntent.setAction(Intent.ACTION_MAIN);
		launchIntent.setClassName("org.omnirom.omnijaws", "org.omnirom.omnijaws.SettingsActivity");
		launchAppIfAvailable(launchIntent, R.string.omnijaws_weather);
	}

    private void toggleFlashlight() {
        if (torchButton == null && torchButtonFab == null) return;
        try {
            mCameraManager.setTorchMode(mCameraId, !isFlashOn);
            isFlashOn = !isFlashOn;
            updateFlashLightButtonState();
        } catch (Exception e) {}
    }

    private void updateFlashLightButtonState() {
        post(new Runnable() {
            @Override
            public void run() {
                if (torchButton != null) {
                    torchButton.setImageResource(isFlashOn ? R.drawable.ic_flashlight_on : R.drawable.ic_flashlight_off);
                    setButtonActiveState(torchButton, null, isFlashOn);
                }
                if (torchButtonFab != null) {
                    torchButtonFab.setIcon(mContext.getDrawable(isFlashOn ? R.drawable.ic_flashlight_on : R.drawable.ic_flashlight_off));
                    setButtonActiveState(null, torchButtonFab, isFlashOn);
                }
            }
        });
    }

    private void showNoDefaultAppFoundToast(@StringRes int appTypeResId) {
        final String appType = mContext.getString(appTypeResId);
        final String message = mContext.getString(R.string.no_default_app_found, appType);
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
    }
}
