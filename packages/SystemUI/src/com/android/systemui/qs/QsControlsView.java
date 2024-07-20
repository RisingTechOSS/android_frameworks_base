/*
 * Copyright (C) 2024 the risingOS Android Project
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
package com.android.systemui.qs;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.PorterDuff;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionLegacyHelper;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.hardware.camera2.CameraManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.internal.graphics.ColorUtils;

import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.Utils;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.animation.view.LaunchableImageView;
import com.android.systemui.lockscreen.ActivityLauncherUtils;
import com.android.systemui.media.dialog.MediaOutputDialogFactory;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.tiles.dialog.bluetooth.BluetoothTileDialogViewModel;
import com.android.systemui.qs.tiles.dialog.InternetDialogManager;
import com.android.systemui.qs.VerticalSlider;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.MobileDataIndicators;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BluetoothController.Callback;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.NotificationMediaManager;

import com.android.internal.util.android.VibrationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import android.os.SystemClock;
import android.view.KeyEvent;

public class QsControlsView extends FrameLayout {

    public static final int AIRPLANE_ACTIVE = R.drawable.qs_airplane_icon_on;
    public static final int AIRPLANE_INACTIVE = R.drawable.qs_airplane_icon_off;
    public static final int BT_ACTIVE = R.drawable.qs_bluetooth_icon_on;
    public static final int BT_INACTIVE = R.drawable.qs_bluetooth_icon_off;
    public static final int DATA_ACTIVE = R.drawable.ic_signal_cellular_alt_24;
    public static final int DATA_INACTIVE = R.drawable.ic_mobiledata_off_24;
    public static final int WIFI_ACTIVE = R.drawable.ic_wifi_24;
    public static final int WIFI_INACTIVE = R.drawable.ic_wifi_off_24;

    private List<View> mControlTiles = new ArrayList<>();
    private List<View> mMediaPlayerViews = new ArrayList<>();
    private List<View> mWidgetViews = new ArrayList<>();
    private List<Runnable> metadataCheckRunnables = new ArrayList<>();

    private View mMediaCard;
    private View mClockTimer, mCalculator, mCamera, mPagerLayout, mMediaLayout, mAccessLayout, mWidgetsLayout;
    private ImageView mTorch;
    private LaunchableImageView mDataButton, mWifiButton, mBtButton, mAirplaneButton;

    private VerticalSlider mBrightnessSlider, mVolumeSlider;

    private final AccessPointController mAccessPointController;
    private final ActivityStarter mActivityStarter;
    private final FalsingManager mFalsingManager;
    private final FlashlightController mFlashlightController;
    private final MediaOutputDialogFactory mMediaOutputDialogFactory;
    private final NotificationMediaManager mNotifManager;
    private final ActivityLauncherUtils mActivityLauncherUtils;
    private final NetworkController mNetworkController;
    private final BluetoothController mBluetoothController;
    private final BluetoothTileDialogViewModel mBluetoothTileDialogViewModel;
    private final DataUsageController mDataController;
    private final InternetDialogManager mInternetDialogManager;
    
    protected final CellSignalCallback mCellSignalCallback = new CellSignalCallback();
    protected final WifiSignalCallback mWifiSignalCallback = new WifiSignalCallback();

    private ViewPager mViewPager;
    private PagerAdapter pagerAdapter;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;

    private int mAccentColor, mBgColor, mTintColor, mContainerColor;
    
    private Context mContext;

    private TextView mMediaTitle, mMediaArtist;
    private ImageView mMediaPrevBtn, mMediaPlayBtn, mMediaNextBtn, mMediaAlbumArtBg, mPlayerIcon;
    
    private MediaController mController;
    private MediaMetadata mMediaMetadata;
    private boolean mInflated = false;
    private Bitmap mAlbumArt = null;
    
    private boolean isClearingMetadata = false;
    
    private Handler mHandler;
    private Runnable mMediaUpdater;

    public QsControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mPagerLayout = LayoutInflater.from(mContext).inflate(R.layout.qs_controls_tile_pager, null);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (Exception e) {}
        mActivityLauncherUtils = new ActivityLauncherUtils(context);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mAccessPointController = Dependency.get(AccessPointController.class);
        mBluetoothController = Dependency.get(BluetoothController.class);
        mBluetoothTileDialogViewModel = Dependency.get(BluetoothTileDialogViewModel.class);
        mFalsingManager = Dependency.get(FalsingManager.class);
        mFlashlightController = Dependency.get(FlashlightController.class);
        mMediaOutputDialogFactory = Dependency.get(MediaOutputDialogFactory.class);
        mNotifManager = Dependency.get(NotificationMediaManager.class);
        mInternetDialogManager = Dependency.get(InternetDialogManager.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mDataController = mNetworkController.getMobileDataController();
    }

    private final MediaController.Callback mMediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            updateMediaController();
        }
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            mMediaMetadata = metadata;
            if (mViewPager != null && 
                mMediaMetadata != null) {
                mViewPager.setCurrentItem(0);
            }
            updateMediaController();
        }
    };
    
    private final FlashlightController.FlashlightListener mFlashlightCallback =
            new FlashlightController.FlashlightListener() {

        @Override
        public void onFlashlightChanged(boolean enabled) {
            isFlashOn = enabled;
            updateTiles();
        }

        @Override
        public void onFlashlightError() {
        }

        @Override
        public void onFlashlightAvailabilityChanged(boolean available) {
            isFlashOn = mFlashlightController.isEnabled() && available;
            updateTiles();
        }
    };

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE && isAttachedToWindow()) {
            updateMediaController();
            updateResources();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mInflated = true;
		mViewPager = findViewById(R.id.qs_controls_pager);
        mBrightnessSlider = findViewById(R.id.qs_controls_brightness_slider);
        mVolumeSlider = findViewById(R.id.qs_controls_volume_slider);
        mMediaLayout = mPagerLayout.findViewById(R.id.qs_controls_media);
        mAccessLayout = mPagerLayout.findViewById(R.id.qs_controls_tile_access);
        mWidgetsLayout = mPagerLayout.findViewById(R.id.qs_controls_tile_widgets);
        mWifiButton = mAccessLayout.findViewById(R.id.wifi_btn);
        mBtButton = mAccessLayout.findViewById(R.id.bt_btn);
        mAirplaneButton = mAccessLayout.findViewById(R.id.airplane_btn);
        mDataButton = mAccessLayout.findViewById(R.id.data_btn);
        mTorch = mWidgetsLayout.findViewById(R.id.qs_flashlight);
        mClockTimer = mWidgetsLayout.findViewById(R.id.qs_clock_timer);
        mCalculator = mWidgetsLayout.findViewById(R.id.qs_calculator);
        mCamera = mWidgetsLayout.findViewById(R.id.qs_camera);
        mMediaAlbumArtBg = mMediaLayout.findViewById(R.id.media_art_bg);
        mMediaAlbumArtBg.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mMediaTitle = mMediaLayout.findViewById(R.id.media_title);
        mMediaArtist = mMediaLayout.findViewById(R.id.artist_name);
        mMediaPrevBtn = mMediaLayout.findViewById(R.id.previous_button);
        mMediaPlayBtn = mMediaLayout.findViewById(R.id.play_button);
        mMediaNextBtn = mMediaLayout.findViewById(R.id.next_button);
        mPlayerIcon = mMediaLayout.findViewById(R.id.player_icon);
        mMediaCard = mMediaLayout.findViewById(R.id.media_cardview);
        collectViews(mControlTiles, (View) mTorch, mClockTimer, mCalculator, mCamera);
        collectViews(mMediaPlayerViews, mMediaPrevBtn, mMediaPlayBtn, mMediaNextBtn, 
                mMediaAlbumArtBg, mPlayerIcon, mMediaTitle, mMediaArtist);
        collectViews(mWidgetViews, mMediaLayout, mAccessLayout, mWidgetsLayout);
        setupViewPager();
        mHandler = Dependency.get(Dependency.MAIN_HANDLER);
        mMediaUpdater = new Runnable() {
            @Override
            public void run() {
                updateMediaController();
                mHandler.postDelayed(this, 1000);
            }
        };
        updateMediaController();
        updateTileButtonState(mAirplaneButton, isAirplaneModeEnabled(), AIRPLANE_ACTIVE, AIRPLANE_INACTIVE);
	}

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mInflated) {
            return;
        }
        setClickListeners();
        updateResources();
        mBluetoothController.addCallback(mBtCallback);
        mFlashlightController.addCallback(mFlashlightCallback);
        mNetworkController.addCallback(mCellSignalCallback);
        mNetworkController.addCallback(mWifiSignalCallback);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(mAirplaneReceiver, filter);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBluetoothController.removeCallback(mBtCallback);
        mFlashlightController.removeCallback(mFlashlightCallback);
        mNetworkController.removeCallback(mCellSignalCallback);
        mNetworkController.removeCallback(mWifiSignalCallback);
        mContext.unregisterReceiver(mAirplaneReceiver);
    }

    private void setClickListeners() {
        mTorch.setOnClickListener(view -> toggleFlashlight());
        mClockTimer.setOnClickListener(view -> mActivityLauncherUtils.launchTimer());
        mCalculator.setOnClickListener(view -> mActivityLauncherUtils.launchCalculator());
        mCamera.setOnClickListener(view -> mActivityLauncherUtils.launchCamera());
        mBtButton.setOnClickListener(view -> toggleBluetoothState());
        mBtButton.setOnLongClickListener(v -> { showBluetoothDialog(v); return true; });
        mAirplaneButton.setOnClickListener(view -> toggleAirPlaneMode());
        mDataButton.setOnClickListener(view -> toggleMobileData());
        mDataButton.setOnLongClickListener(v -> { showInternetDialog(v); return true; });
        mWifiButton.setOnClickListener(view -> toggleWiFi());
        mWifiButton.setOnLongClickListener(v -> { showInternetDialog(v); return true; });
        mMediaPlayBtn.setOnClickListener(view -> performMediaAction(MediaAction.TOGGLE_PLAYBACK));
        mMediaPrevBtn.setOnClickListener(view -> performMediaAction(MediaAction.PLAY_PREVIOUS));
        mMediaNextBtn.setOnClickListener(view -> performMediaAction(MediaAction.PLAY_NEXT));
        mMediaAlbumArtBg.setOnClickListener(view -> mActivityLauncherUtils.launchMediaPlayerApp());
        ((LaunchableImageView) mMediaAlbumArtBg).setOnLongClickListener(view -> {
            showMediaOutputDialog();
            return true;
        });
    }

    private void clearMediaMetadata() {
        if (isClearingMetadata) return;
        isClearingMetadata = true;
        mMediaMetadata = null;
        mAlbumArt = null; 
        isClearingMetadata = false;
        if (mMediaPlayBtn != null) {
            mMediaPlayBtn.setImageResource(R.drawable.ic_media_play);
        }
    }
    
    private void updateMediaController() {
        MediaController localController = getActiveLocalMediaController();
        if (localController != null && !mNotifManager.sameSessions(mController, localController)) {
            if (mController != null) {
                mController.unregisterCallback(mMediaCallback);
                mController = null;
            }
            mController = localController;
            mController.registerCallback(mMediaCallback);
        }
        mMediaMetadata = isMediaControllerAvailable() ? mController.getMetadata() : null;
        updateMediaPlaybackState();
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

    private boolean isMediaControllerAvailable() {
        final MediaController mediaController = getActiveLocalMediaController();
        return mediaController != null && !TextUtils.isEmpty(mediaController.getPackageName());
    }

    private void updateMediaPlaybackState() {
        updateMediaMetadata();
        postDelayed(() -> {
            updateMediaMetadata();
        }, 250);
    }

    private void updateMediaMetadata() {
        Bitmap albumArt = mMediaMetadata == null ? null : mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (albumArt != null) {
            new ProcessArtworkTask().execute(albumArt);
        } else {
            mMediaAlbumArtBg.setImageBitmap(null);
        }
        updateMediaViews();
    }
    
    private boolean isMediaPlaying() {
        return isMediaControllerAvailable() 
            && PlaybackState.STATE_PLAYING == mNotifManager.getMediaControllerPlaybackState(mController);
    }

    private void updateMediaViews() {
        if (!isMediaPlaying()) {
            clearMediaMetadata();
        }
        if (mMediaPlayBtn != null) {
            mMediaPlayBtn.setImageResource(isMediaPlaying() ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
        }
        CharSequence title = mMediaMetadata == null ? null : mMediaMetadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence artist = mMediaMetadata == null ? null : mMediaMetadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
        mMediaTitle.setText(title != null ? title : mContext.getString(R.string.no_media_playing));
        mMediaArtist.setText(artist != null ? artist : "");
        mPlayerIcon.setImageIcon(mNotifManager == null ? null : mNotifManager.getMediaIcon());
        final int mediaItemColor = getMediaItemColor();
        for (View view : mMediaPlayerViews) {
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(mediaItemColor);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setImageTintList(ColorStateList.valueOf(mediaItemColor));
            }
        }
    }

    private class ProcessArtworkTask extends AsyncTask<Bitmap, Void, Bitmap> {
        protected Bitmap doInBackground(Bitmap... bitmaps) {
            Bitmap bitmap = bitmaps[0];
            if (bitmap == null) {
                return null;
            }
            int width = mMediaAlbumArtBg.getWidth();
            int height = mMediaAlbumArtBg.getHeight();
            return getScaledRoundedBitmap(bitmap, width, height);
        }
        protected void onPostExecute(Bitmap result) {
            if (result == null) return;
            if (mAlbumArt == null || mAlbumArt != result) {
                mAlbumArt = result;
                final int mediaFadeLevel = mContext.getResources().getInteger(R.integer.media_player_fade);
                final int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, mNotifManager == null ? Color.BLACK : mNotifManager.getMediaBgColor(), mediaFadeLevel / 100f);
                mMediaAlbumArtBg.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
                mMediaAlbumArtBg.setImageBitmap(mAlbumArt);
            }
        }
    }

    private Bitmap getScaledRoundedBitmap(Bitmap bitmap, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        float radius = mContext.getResources().getDimensionPixelSize(R.dimen.qs_controls_slider_corner_radius);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        if (scaledBitmap == null) {
            return null;
        }
        Bitmap output = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        RectF rect = new RectF(0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        canvas.drawRoundRect(rect, radius, radius, paint);
        return output;
    }

    private void setupViewPager() {
        if (mViewPager == null) return;
        pagerAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return mWidgetViews.size();
            }
            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                View view = mWidgetViews.get(position);
                container.addView(view);
                return view;
            }
            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }
        };
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.setCurrentItem(0);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    mMediaLayout.setVisibility(View.VISIBLE);
                    mAccessLayout.setVisibility(View.GONE);
                    mWidgetsLayout.setVisibility(View.GONE);
                    updateMediaController();
                } else if (position == 1) {
                    mMediaLayout.setVisibility(View.GONE);
                    mAccessLayout.setVisibility(View.VISIBLE);
                    mWidgetsLayout.setVisibility(View.GONE);
                } else if (position == 2) {
                    mMediaLayout.setVisibility(View.GONE);
                    mAccessLayout.setVisibility(View.GONE);
                    mWidgetsLayout.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onPageScrollStateChanged(int state) {}
        });
    }

    public void updateColors() {
        mAccentColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_active_color_dark : R.color.lockscreen_widget_active_color_light);
        mBgColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_bg_color_dark : R.color.qs_controls_bg_color_light);
        mTintColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_bg_color_light : R.color.qs_controls_bg_color_dark);
        mContainerColor = mContext.getResources().getColor(isNightMode() ? R.color.qs_controls_container_bg_color_dark : R.color.qs_controls_container_bg_color_light);
        updateTiles();
        updateMediaPlaybackState();
    }
    
    private boolean isNightMode() {
        return (mContext.getResources().getConfiguration().uiMode 
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    
    private int qsPanelStyle() {
        return Settings.System.getIntForUser(mContext.getContentResolver(), 
            Settings.System.QS_PANEL_STYLE, 0, UserHandle.USER_CURRENT);
    }
    
    private boolean translucentQsStyle() {
        return (qsPanelStyle() == 1 || qsPanelStyle() == 2);
    }
    
    private void updateTiles() {
        int accentColor = translucentQsStyle() ? Utils.applyAlpha(0.2f, mAccentColor) : mAccentColor;
        int bgColor = translucentQsStyle() ? Utils.applyAlpha(0.8f, mBgColor) : mBgColor;
        int containerColor = translucentQsStyle() ? Utils.applyAlpha(0.8f, mContainerColor) : mContainerColor;
        int activeTintColor = translucentQsStyle() ? mAccentColor : mBgColor;
        int tintColor = translucentQsStyle() ? mAccentColor : mTintColor;
        if (mMediaCard != null) {
            mMediaCard.getBackground().setTint(containerColor);
        }
        for (View view : mControlTiles) {
            if (view instanceof ImageView) {
                ImageView tile = (ImageView) view;
                int backgroundResource;
                int imageTintColor;
                int backgroundTintColor;
                if (tile == mTorch) {
                    backgroundResource = isFlashOn ? R.drawable.qs_controls_tile_background_active : R.drawable.qs_controls_tile_background;
                    imageTintColor = isFlashOn ? activeTintColor : tintColor;
                    backgroundTintColor = isFlashOn ? accentColor : bgColor;
                } else if (tile == mCamera) {
                    backgroundResource = R.drawable.qs_controls_tile_background_active;
                    imageTintColor = activeTintColor;
                    backgroundTintColor = accentColor;
                } else {
                    backgroundResource = R.drawable.qs_controls_tile_background;
                    imageTintColor = tintColor;
                    backgroundTintColor = bgColor;
                }
                tile.setBackgroundResource(backgroundResource);
                tile.setImageTintList(ColorStateList.valueOf(imageTintColor));
                tile.setBackgroundTintList(ColorStateList.valueOf(backgroundTintColor));
            }
        }
    }
    
    private int getMediaItemColor() {
        return isMediaPlaying() ? Color.WHITE : mTintColor;
    }

    public void updateResources() {
        if (mBrightnessSlider != null && mVolumeSlider != null) {
            mBrightnessSlider.updateSliderPaint();
            mVolumeSlider.updateSliderPaint();
        }
        updateColors();
    }

    private void collectViews(List<View> viewList, View... views) {
        for (View view : views) {
            if (!viewList.contains(view)) {
                viewList.add(view);
            }
        }
    }

    private void toggleFlashlight() {
        if (mActivityStarter == null) return;
        try {
            cameraManager.setTorchMode(cameraId, !isFlashOn);
            isFlashOn = !isFlashOn;
            updateTiles();
        } catch (Exception e) {}
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    private void performMediaAction(MediaAction action) {
        updateMediaController();
        switch (action) {
            case TOGGLE_PLAYBACK:
                toggleMediaPlaybackState();
                break;
            case PLAY_PREVIOUS:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case PLAY_NEXT:
                dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
        }
        updateMediaPlaybackState();
    }
    
    private void toggleMediaPlaybackState() {
        if (isMediaPlaying()) {
            mHandler.removeCallbacks(mMediaUpdater);
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PAUSE);
            updateMediaController();
            if (mMediaPlayBtn != null) {
                mMediaPlayBtn.setImageResource(R.drawable.ic_media_play);
            }
        } else {
            mMediaUpdater.run();
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY);
            if (mMediaPlayBtn != null) {
                mMediaPlayBtn.setImageResource(R.drawable.ic_media_pause);
            }
        }
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

    private void showMediaOutputDialog() {
        String packageName = mActivityLauncherUtils.getActiveMediaPackage();
        if (!packageName.isEmpty()) {
            mMediaOutputDialogFactory.create(packageName, true, (LaunchableImageView) mMediaAlbumArtBg);
        }
    }
    
    private enum MediaAction {
        TOGGLE_PLAYBACK,
        PLAY_PREVIOUS,
        PLAY_NEXT
    }

    private void updateWiFiButtonState(boolean enabled) {;
        final WifiCallbackInfo cbi = mWifiSignalCallback.mInfo;
        updateTileButtonState(mWifiButton, enabled, WIFI_ACTIVE, WIFI_INACTIVE);
    }

    private void updateTileButtonState(
            LaunchableImageView iv,  boolean active, 
            int activeResource, int inactiveResource) {
        post(new Runnable() {
            @Override
            public void run() {
                if (iv != null) {
                    iv.setImageResource(active ? activeResource : inactiveResource);
                    setButtonActiveState(iv, active);
                }
            }
        });
    }
    
    private void setButtonActiveState(LaunchableImageView iv, boolean active) {
        int accentColor = translucentQsStyle() ? Utils.applyAlpha(0.2f, mAccentColor) : mAccentColor;
        int bgColor = translucentQsStyle() ? Utils.applyAlpha(0.8f, mBgColor) : mBgColor;
        int activeTintColor = translucentQsStyle() ? mAccentColor : mBgColor;
        int bgTint = active ? accentColor : bgColor;
        int inactiveTintColor = translucentQsStyle() ? mAccentColor : mTintColor;
        int tintColor = active ? activeTintColor : inactiveTintColor;
        if (iv != null) {
            iv.setBackgroundTintList(ColorStateList.valueOf(bgTint));
            iv.setImageTintList(ColorStateList.valueOf(tintColor));
            iv.setBackgroundResource(active ?  R.drawable.qs_controls_tile_background_active :  R.drawable.qs_controls_tile_background);
        }
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
    
    private void updateMobileDataState(boolean enabled) {
        updateTileButtonState(mDataButton, enabled, DATA_ACTIVE, DATA_INACTIVE);
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
        updateTileButtonState(mBtButton, isBluetoothEnabled(), BT_ACTIVE, BT_INACTIVE);
    }
    
    private boolean isBluetoothEnabled() {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }
    
    private void toggleAirPlaneMode() {
        Settings.Global.putInt(mContext.getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, isAirplaneModeEnabled() ? 0 : 1);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", !isAirplaneModeEnabled());
        mContext.sendBroadcast(intent);
    }
    
    private boolean isAirplaneModeEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
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
    
    private final BroadcastReceiver mAirplaneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                updateTileButtonState(mAirplaneButton, isAirplaneModeEnabled(), AIRPLANE_ACTIVE, AIRPLANE_INACTIVE);
            }
        }
    };
}
