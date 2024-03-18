/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.keyguard;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.keyguard.KeyguardClockSwitch.LARGE;
import static com.android.keyguard.KeyguardClockSwitch.SMALL;
import static com.android.systemui.Flags.migrateClocksToBlueprint;
import static com.android.systemui.Flags.smartspaceRelocateToBottom;
import static com.android.systemui.flags.Flags.LOCKSCREEN_WALLPAPER_DREAM_ENABLED;
import static com.android.systemui.util.kotlin.JavaAdapterKt.collectFlow;

import android.annotation.Nullable;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.Dependency;
import com.android.systemui.Dumpable;
import com.android.systemui.crdroid.CurrentWeatherView;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlagsClassic;
import com.android.systemui.lockscreen.LockScreenWidgets;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.ui.view.InWindowLauncherUnlockAnimationManager;
import com.android.systemui.log.LogBuffer;
import com.android.systemui.log.core.LogLevel;
import com.android.systemui.log.dagger.KeyguardClockLog;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.clocks.ClockController;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.res.R;
import com.android.systemui.shared.clocks.ClockRegistry;
import com.android.systemui.shared.regionsampling.RegionSampler;
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController;
import com.android.systemui.statusbar.notification.AnimatableProperty;
import com.android.systemui.statusbar.notification.PropertyAnimator;
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerAlwaysOnDisplayViewBinder;
import com.android.systemui.statusbar.notification.shared.NotificationIconContainerRefactor;
import com.android.systemui.statusbar.notification.stack.AnimationProperties;
import com.android.systemui.statusbar.phone.NotificationIconAreaController;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.ViewController;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.settings.SecureSettings;

import com.android.systemui.afterlife.ClockStyle;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.inject.Inject;

import kotlinx.coroutines.DisposableHandle;

/**
 * Injectable controller for {@link KeyguardClockSwitch}.
 */
public class KeyguardClockSwitchController extends ViewController<KeyguardClockSwitch>
        implements Dumpable, TunerService.Tunable {
    private static final String TAG = "KeyguardClockSwitchController";

    private static final String LOCKSCREEN_WEATHER_ENABLED =
            "system:" + Settings.System.LOCKSCREEN_WEATHER_ENABLED;

    private static final String LOCKSCREEN_CLOCK_STYLE =
            "system:" + "clock_style";

    private static final String LOCKSCREEN_WIDGETS_ENABLED =
            "system:" + "lockscreen_widgets_enabled";

    private final StatusBarStateController mStatusBarStateController;
    private final ClockRegistry mClockRegistry;
    private final KeyguardSliceViewController mKeyguardSliceViewController;
    private final NotificationIconAreaController mNotificationIconAreaController;
    private final LockscreenSmartspaceController mSmartspaceController;
    private final SecureSettings mSecureSettings;
    private final DumpManager mDumpManager;
    private final ClockEventController mClockEventController;
    private final LogBuffer mLogBuffer;
    private final NotificationIconContainerAlwaysOnDisplayViewBinder mNicViewBinder;
    private final TunerService  mTunerService;
    private final ActivityStarter mActivityStarter;
    private final ConfigurationController mConfigurationController;
    private final FlashlightController mFlashlightController;

    private FrameLayout mSmallClockFrame; // top aligned clock
    private FrameLayout mLargeClockFrame; // centered clock
    private View mCustomClock; // custom clock
    private View mCustomClockFrame; // custom clock frame

    @KeyguardClockSwitch.ClockSize
    private int mCurrentClockSize = SMALL;

    private int mKeyguardSmallClockTopMargin = 0;
    private int mKeyguardLargeClockTopMargin = 0;
    private int mKeyguardDateWeatherViewInvisibility = View.INVISIBLE;
    private final ClockRegistry.ClockChangeListener mClockChangedListener;

    private ViewGroup mStatusArea;

    // If the SMARTSPACE flag is set, keyguard_slice_view is replaced by the following views.
    private ViewGroup mDateWeatherView;
    private View mWeatherView;
    private View mSmartspaceView;

    private final KeyguardUnlockAnimationController mKeyguardUnlockAnimationController;
    private final InWindowLauncherUnlockAnimationManager mInWindowLauncherUnlockAnimationManager;

    private CurrentWeatherView mCurrentWeatherView;
    private boolean mShowWeather;
    
    private LockScreenWidgets mLsWidgets;
    private boolean mShowLockscreenWidgets;

    private boolean mShownOnSecondaryDisplay = false;
    private boolean mOnlyClock = false;
    private boolean mIsActiveDreamLockscreenHosted = false;
    private final FeatureFlagsClassic mFeatureFlags;
    private KeyguardInteractor mKeyguardInteractor;
    private KeyguardClockInteractor mKeyguardClockInteractor;
    private final DelayableExecutor mUiExecutor;
    private final Executor mBgExecutor;
    private boolean mCanShowDoubleLineClock = true;
    private DisposableHandle mAodIconsBindHandle;
    @Nullable private NotificationIconContainer mAodIconContainer;

    @VisibleForTesting
    final Consumer<Boolean> mIsActiveDreamLockscreenHostedCallback =
            (Boolean isLockscreenHosted) -> {
                if (mIsActiveDreamLockscreenHosted == isLockscreenHosted) {
                    return;
                }
                mIsActiveDreamLockscreenHosted = isLockscreenHosted;
                updateKeyguardStatusAreaVisibility();
            };
    private final ContentObserver mDoubleLineClockObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean change) {
            updateDoubleLineClock();
        }
    };
    private boolean mEnableCustomClock = false;
    private int mClockStyle;
    private final ContentObserver mCustomClockObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean change) {
            updateCustomClock();
        }
    };
    private final ContentObserver mShowWeatherObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean change) {
            if (!mShowWeather) {
                setWeatherVisibility();
            }
        }
    };

    private final KeyguardUnlockAnimationController.KeyguardUnlockAnimationListener
            mKeyguardUnlockAnimationListener =
            new KeyguardUnlockAnimationController.KeyguardUnlockAnimationListener() {
                @Override
                public void onUnlockAnimationFinished() {
                    // For performance reasons, reset this once the unlock animation ends.
                    setClipChildrenForUnlock(true);
                }
            };

    @Inject
    public KeyguardClockSwitchController(
            KeyguardClockSwitch keyguardClockSwitch,
            StatusBarStateController statusBarStateController,
            ClockRegistry clockRegistry,
            KeyguardSliceViewController keyguardSliceViewController,
            NotificationIconAreaController notificationIconAreaController,
            LockscreenSmartspaceController smartspaceController,
            NotificationIconContainerAlwaysOnDisplayViewBinder nicViewBinder,
            KeyguardUnlockAnimationController keyguardUnlockAnimationController,
            SecureSettings secureSettings,
            @Main DelayableExecutor uiExecutor,
            @Background Executor bgExecutor,
            DumpManager dumpManager,
            ClockEventController clockEventController,
            @KeyguardClockLog LogBuffer logBuffer,
            KeyguardInteractor keyguardInteractor,
            KeyguardClockInteractor keyguardClockInteractor,
            FeatureFlagsClassic featureFlags,
            InWindowLauncherUnlockAnimationManager inWindowLauncherUnlockAnimationManager,
            ActivityStarter activityStarter,
            ConfigurationController configurationController,
            FlashlightController flashlightController) {
        super(keyguardClockSwitch);
        mStatusBarStateController = statusBarStateController;
        mClockRegistry = clockRegistry;
        mKeyguardSliceViewController = keyguardSliceViewController;
        mNotificationIconAreaController = notificationIconAreaController;
        mSmartspaceController = smartspaceController;
        mNicViewBinder = nicViewBinder;
        mSecureSettings = secureSettings;
        mUiExecutor = uiExecutor;
        mBgExecutor = bgExecutor;
        mKeyguardUnlockAnimationController = keyguardUnlockAnimationController;
        mDumpManager = dumpManager;
        mClockEventController = clockEventController;
        mLogBuffer = logBuffer;
        mView.setLogBuffer(mLogBuffer);
        mFeatureFlags = featureFlags;
        mActivityStarter = activityStarter;
        mConfigurationController = configurationController;
        mFlashlightController = flashlightController;
        mKeyguardInteractor = keyguardInteractor;
        mKeyguardClockInteractor = keyguardClockInteractor;
        mInWindowLauncherUnlockAnimationManager = inWindowLauncherUnlockAnimationManager;

        mClockChangedListener = new ClockRegistry.ClockChangeListener() {
            @Override
            public void onCurrentClockChanged() {
                if (!migrateClocksToBlueprint()) {
                    setClock(mClockRegistry.createCurrentClock());
                }
            }
            @Override
            public void onAvailableClocksChanged() { }
        };

        mTunerService = Dependency.get(TunerService.class);
    }

    /**
     * When set, limits the information shown in an external display.
     */
    public void setShownOnSecondaryDisplay(boolean shownOnSecondaryDisplay) {
        mShownOnSecondaryDisplay = shownOnSecondaryDisplay;
    }

    /**
     * Mostly used for alternate displays, limit the information shown
     *
     * @deprecated use {@link KeyguardClockSwitchController#setShownOnSecondaryDisplay}
     */
    @Deprecated
    public void setOnlyClock(boolean onlyClock) {
        mOnlyClock = onlyClock;
    }

    /**
     * Used for status view to pass the screen offset from parent view
     */
    public void setLockscreenClockY(int clockY) {
        updateCustomClock();
        if (mView.screenOffsetYPadding != clockY) {
            mView.screenOffsetYPadding = clockY;
            mView.post(() -> mView.updateClockTargetRegions());
        }
    }

    /**
     * Attach the controller to the view it relates to.
     */
    @Override
    protected void onInit() {
        mKeyguardSliceViewController.init();

        if (!migrateClocksToBlueprint()) {
            mSmallClockFrame = mView.findViewById(R.id.lockscreen_clock_view);
            mLargeClockFrame = mView.findViewById(R.id.lockscreen_clock_view_large);
            mCurrentWeatherView = mView.findViewById(R.id.weather_container);
            mCustomClock = mView.findViewById(R.id.clock_ls);
            mCustomClockFrame = mView.findViewById(R.id.clock_frame);
            View kgWidgets = mView.findViewById(R.id.keyguard_widgets);
            mLsWidgets = (LockScreenWidgets) kgWidgets;
            mLsWidgets.setActivityStarter(mActivityStarter);
            mLsWidgets.setConfigurationController(mConfigurationController);
            mLsWidgets.setFlashLightController(mFlashlightController);
            mLsWidgets.setStatusBarStateController(mStatusBarStateController);
        }

        if (!mOnlyClock) {
            mDumpManager.unregisterDumpable(getClass().getSimpleName()); // unregister previous
            mDumpManager.registerDumpable(getClass().getSimpleName(), this);
        }

        if (mFeatureFlags.isEnabled(LOCKSCREEN_WALLPAPER_DREAM_ENABLED)) {
            mStatusArea = mView.findViewById(R.id.keyguard_status_area);
            collectFlow(mStatusArea, mKeyguardInteractor.isActiveDreamLockscreenHosted(),
                    mIsActiveDreamLockscreenHostedCallback);
        }
    }

    public KeyguardClockSwitch getView() {
        return mView;
    }

    private void hideSliceViewAndNotificationIconContainer() {
        View ksv = mView.findViewById(R.id.keyguard_slice_view);
        ksv.setVisibility(View.GONE);

        View nic = mView.findViewById(
                R.id.left_aligned_notification_icon_container);
        if (nic != null) {
            nic.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onViewAttached() {
        mClockRegistry.registerClockChangeListener(mClockChangedListener);
        setClock(mClockRegistry.createCurrentClock());
        mClockEventController.registerListeners(mView);
        mKeyguardSmallClockTopMargin =
                mView.getResources().getDimensionPixelSize(R.dimen.keyguard_clock_top_margin);
        mKeyguardLargeClockTopMargin =
                mView.getResources().getDimensionPixelSize(
                        com.android.systemui.customization.R.dimen.keyguard_large_clock_top_margin);
        mKeyguardDateWeatherViewInvisibility =
                mView.getResources().getInteger(R.integer.keyguard_date_weather_view_invisibility);

        if (mShownOnSecondaryDisplay) {
            mView.setLargeClockOnSecondaryDisplay(true);
            mClockEventController.setLargeClockOnSecondaryDisplay(true);
            displayClock(LARGE, /* animate= */ false);
            hideSliceViewAndNotificationIconContainer();
            return;
        }

        if (mOnlyClock) {
            hideSliceViewAndNotificationIconContainer();
            return;
        }
        updateAodIcons();
        mStatusArea = mView.findViewById(R.id.keyguard_status_area);

        mBgExecutor.execute(() -> {
            mSecureSettings.registerContentObserverForUser(
                    Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK,
                    false, /* notifyForDescendants */
                    mDoubleLineClockObserver,
                    UserHandle.USER_ALL
            );

            mSecureSettings.registerContentObserverForUser(
                    Settings.Secure.LOCK_SCREEN_WEATHER_ENABLED,
                    false, /* notifyForDescendants */
                    mShowWeatherObserver,
                    UserHandle.USER_ALL
            );
            
            mSecureSettings.registerContentObserverForUser(
                    Settings.System.getUriFor("clock_style"),
                    false, /* notifyForDescendants */
                    mCustomClockObserver,
                    UserHandle.USER_ALL
            );
        });

        updateDoubleLineClock();

        mKeyguardUnlockAnimationController.addKeyguardUnlockAnimationListener(
                mKeyguardUnlockAnimationListener);

        mTunerService.addTunable(this, 
            LOCKSCREEN_WEATHER_ENABLED,
            LOCKSCREEN_CLOCK_STYLE, 
            LOCKSCREEN_WEATHER_ENABLED, 
            LOCKSCREEN_WIDGETS_ENABLED);

        updateViews();
    }

    private void updateViews() {
        mUiExecutor.execute(() -> {
            updateCustomClock();
            if (mSmartspaceController.isEnabled()) {
                removeViewsFromStatusArea();

                View ksv = mView.findViewById(R.id.keyguard_slice_view);
                if (ksv != null) {
                    ksv.setVisibility(mShowWeather ? View.VISIBLE : View.GONE);
                }

                if (mStatusArea == null) {
                    return;
                }

                if (!mShowWeather) {
                    addSmartspaceView();
                    if (mSmartspaceController.isDateWeatherDecoupled() && !migrateClocksToBlueprint()) {
                        addDateWeatherView();
                        setDateWeatherVisibility();
                        setWeatherVisibility();
                    }
                }
            }
        });
    }

    int getNotificationIconAreaHeight() {
        if (migrateClocksToBlueprint()) {
            return 0;
        } else if (NotificationIconContainerRefactor.isEnabled()) {
            return mAodIconContainer != null ? mAodIconContainer.getHeight() : 0;
        } else {
            return mNotificationIconAreaController.getHeight();
        }
    }

    @Nullable
    View getAodNotifIconContainer() {
        return mAodIconContainer;
    }

    @Override
    protected void onViewDetached() {
        mTunerService.removeTunable(this);
        mClockRegistry.unregisterClockChangeListener(mClockChangedListener);
        mClockEventController.unregisterListeners();
        setClock(null);

        mBgExecutor.execute(() -> {
            mSecureSettings.unregisterContentObserver(mDoubleLineClockObserver);
            mSecureSettings.unregisterContentObserver(mShowWeatherObserver);
            mSecureSettings.unregisterContentObserver(mCustomClockObserver);
        });

        mKeyguardUnlockAnimationController.removeKeyguardUnlockAnimationListener(
                mKeyguardUnlockAnimationListener);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_CLOCK_STYLE:
                mClockStyle =
                        TunerService.parseInteger(newValue, 0);
                mEnableCustomClock = mClockStyle != 0;
                updateDoubleLineClock();
                break;
            case LOCKSCREEN_WEATHER_ENABLED:
                mShowWeather =
                        TunerService.parseIntegerSwitch(newValue, false);
                updateWeatherView();
                break;
            case LOCKSCREEN_WIDGETS_ENABLED:
                mShowLockscreenWidgets =
                        TunerService.parseIntegerSwitch(newValue, false);
                mLsWidgets.updateWidgetViews();
                updateDoubleLineClock();
                break;
            default:
                break;
        }
    }

    public void updateWeatherView() {
        mUiExecutor.execute(() -> {
            if (mCurrentWeatherView != null) {
                if (mShowWeather && !mOnlyClock) {
                    mCurrentWeatherView.enableUpdates();
                    mCurrentWeatherView.setVisibility(View.VISIBLE);
                } else {
                    mCurrentWeatherView.disableUpdates();
                    mCurrentWeatherView.setVisibility(View.GONE);
                }
            }
        });
        updateViews();
    }

    void onLocaleListChanged() {
        updateViews();
    }

    private void addDateWeatherView() {
        updateCustomClock();
        if (migrateClocksToBlueprint()) {
            return;
        }
        mDateWeatherView = (ViewGroup) mSmartspaceController.buildAndConnectDateView(mView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        mStatusArea.addView(mDateWeatherView, 0, lp);
        int startPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.below_clock_padding_start);
        int endPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.below_clock_padding_end);
        mDateWeatherView.setPaddingRelative(startPadding, 0, endPadding, 0);
        addWeatherView();
    }

    private void addWeatherView() {
        if (migrateClocksToBlueprint()) {
            return;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        int startPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.below_clock_padding_start);
        mWeatherView = mSmartspaceController.buildAndConnectWeatherView(mView);
        // Place weather right after the date, before the extras
        final int index = mDateWeatherView.getChildCount() == 0 ? 0 : 1;
        mDateWeatherView.addView(mWeatherView, index, lp);
        mWeatherView.setPaddingRelative(startPadding, 0, 4, 0);
    }

    private void addSmartspaceView() {
        if (migrateClocksToBlueprint()) {
            return;
        }

        if (smartspaceRelocateToBottom()) {
            return;
        }

        if (mStatusArea == null) {
            return;
        }

        mSmartspaceView = mSmartspaceController.buildAndConnectView(mView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        mStatusArea.addView(mSmartspaceView, 0, lp);
        int startPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.below_clock_padding_start);
        int endPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.below_clock_padding_end);
        mSmartspaceView.setPaddingRelative(startPadding, 0, endPadding, 0);
        mKeyguardUnlockAnimationController.setLockscreenSmartspace(mSmartspaceView);
        mInWindowLauncherUnlockAnimationManager.setLockscreenSmartspace(mSmartspaceView);

        mView.setSmartspace(mSmartspaceView);
    }

    /**
     * Apply dp changes on configuration change
     */
    public void onConfigChanged() {
        mView.onConfigChanged();
        updateCustomClock();
        mKeyguardSmallClockTopMargin =
                mView.getResources().getDimensionPixelSize(R.dimen.keyguard_clock_top_margin);
        mKeyguardLargeClockTopMargin =
                mView.getResources().getDimensionPixelSize(
                        com.android.systemui.customization.R.dimen.keyguard_large_clock_top_margin);
        mKeyguardDateWeatherViewInvisibility =
                mView.getResources().getInteger(R.integer.keyguard_date_weather_view_invisibility);
        mView.updateClockTargetRegions();
        setDateWeatherVisibility();
    }

    /**
     * Enable or disable split shade center specific positioning
     */
    public void setSplitShadeCentered(boolean splitShadeCentered) {
        mView.setSplitShadeCentered(splitShadeCentered);
    }

    /**
     * Set if the split shade is enabled
     */
    public void setSplitShadeEnabled(boolean splitShadeEnabled) {
        mSmartspaceController.setSplitShadeEnabled(splitShadeEnabled);
    }

    /**
     * Set which clock should be displayed on the keyguard. The other one will be automatically
     * hidden.
     */
    public void displayClock(@KeyguardClockSwitch.ClockSize int clockSize, boolean animate) {
        if (clockSize == LARGE && !mCanShowDoubleLineClock) {
            return;
        }

        mCurrentClockSize = clockSize;
        setDateWeatherVisibility();

        ClockController clock = getClock();
        boolean appeared = mView.switchToClock(clockSize, animate);
        if (clock != null && animate && appeared && clockSize == LARGE) {
            mUiExecutor.executeDelayed(() -> clock.getLargeClock().getAnimations().enter(),
                    KeyguardClockSwitch.CLOCK_IN_START_DELAY_MILLIS);
        }
    }

    /**
     * Animates the clock view between folded and unfolded states
     */
    public void animateFoldToAod(float foldFraction) {
        ClockController clock = getClock();
        if (clock != null) {
            clock.getSmallClock().getAnimations().fold(foldFraction);
            clock.getLargeClock().getAnimations().fold(foldFraction);
        }
    }

    /**
     * Refresh clock. Called in response to TIME_TICK broadcasts.
     */
    void refresh() {
        mLogBuffer.log(TAG, LogLevel.INFO, "refresh");
        if (mSmartspaceController != null) {
            mSmartspaceController.requestSmartspaceUpdate();
        }
        ClockController clock = getClock();
        if (clock != null) {
            clock.getSmallClock().getEvents().onTimeTick();
            clock.getLargeClock().getEvents().onTimeTick();
        }
        if (mCustomClock != null) {
        	((ClockStyle) mCustomClock).onTimeChanged();
        }
    }

    /**
     * Update position of the view, with optional animation. Move the slice view and the clock
     * slightly towards the center in order to prevent burn-in. Y positioning occurs at the
     * view parent level. The large clock view will scale instead of using x position offsets, to
     * keep the clock centered.
     */
    void updatePosition(int x, float scale, AnimationProperties props, boolean animate) {
        x = getCurrentLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -x : x;
        if (!migrateClocksToBlueprint()) {
            PropertyAnimator.setProperty(mSmallClockFrame, AnimatableProperty.TRANSLATION_X,
                    x, props, animate);
            PropertyAnimator.setProperty(mLargeClockFrame, AnimatableProperty.SCALE_X,
                    scale, props, animate);
            PropertyAnimator.setProperty(mLargeClockFrame, AnimatableProperty.SCALE_Y,
                    scale, props, animate);

        }

        if (mStatusArea != null) {
            PropertyAnimator.setProperty(mStatusArea, KeyguardStatusAreaView.TRANSLATE_X_AOD,
                    x, props, animate);
        }
    }

    /**
     * Get y-bottom position of the currently visible clock on the keyguard.
     * We can't directly getBottom() because clock changes positions in AOD for burn-in
     */
    int getClockBottom(int statusBarHeaderHeight) {
        ClockController clock = getClock();
        if (clock == null) {
            return 0;
        }

        if (migrateClocksToBlueprint()) {
            return 0;
        }

        if (mLargeClockFrame.getVisibility() == View.VISIBLE) {
            // This gets the expected clock bottom if mLargeClockFrame had a top margin, but it's
            // top margin only contributed to height and didn't move the top of the view (as this
            // was the computation previously). As we no longer have a margin, we add this back
            // into the computation manually.
            int frameHeight = mLargeClockFrame.getHeight();
            int clockHeight = clock.getLargeClock().getView().getHeight();
            if (!mEnableCustomClock) {
                return frameHeight / 2 + clockHeight / 2 + mKeyguardLargeClockTopMargin / -2;
            } else {
            	return 0;
            }
            
        } else {
            int clockHeight = clock.getSmallClock().getView().getHeight();
            if (!mEnableCustomClock) {
                return clockHeight + statusBarHeaderHeight + mKeyguardSmallClockTopMargin;
            } else {
            	return 0;
            }
        }
    }

    /**
     * Get the height of the currently visible clock on the keyguard.
     */
    int getClockHeight() {
        ClockController clock = getClock();
        if (clock == null) {
            return 0;
        }

        if (mLargeClockFrame.getVisibility() == View.VISIBLE) {
        	if (!mEnableCustomClock) {
                return clock.getLargeClock().getView().getHeight();
            } else {
            	return 0;
            }
        } else {
            if (!mEnableCustomClock) {
                return clock.getSmallClock().getView().getHeight();
            } else {
            	return 0;
            }
        }
    }

    boolean isClockTopAligned() {
        if (migrateClocksToBlueprint()) {
            return mKeyguardClockInteractor.getClockSize().getValue() == LARGE;
        }
        return mLargeClockFrame.getVisibility() != View.VISIBLE;
    }

    private void updateAodIcons() {
        if (!migrateClocksToBlueprint()) {
            NotificationIconContainer nic = (NotificationIconContainer)
                    mView.findViewById(
                            com.android.systemui.res.R.id.left_aligned_notification_icon_container);
            if (NotificationIconContainerRefactor.isEnabled()) {
                if (mAodIconsBindHandle != null) {
                    mAodIconsBindHandle.dispose();
                }
                if (nic != null) {
                    mAodIconsBindHandle = mNicViewBinder.bindWhileAttached(nic);
                    mAodIconContainer = nic;
                }
            } else {
                mNotificationIconAreaController.setupAodIcons(nic);
                mAodIconContainer = nic;
            }
        }
    }
    private void setClock(ClockController clock) {
        if (migrateClocksToBlueprint()) {
            return;
        }
        if (clock != null && mLogBuffer != null) {
            mLogBuffer.log(TAG, LogLevel.INFO, "New Clock");
        }

        mClockEventController.setClock(clock);
        mView.setClock(clock, mStatusBarStateController.getState());
        setDateWeatherVisibility();
    }

    @Nullable
    public ClockController getClock() {
        if (migrateClocksToBlueprint()) {
            return mKeyguardClockInteractor.getClock();
        } else {
            return mClockEventController.getClock();
        }
    }

    private int getCurrentLayoutDirection() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault());
    }

    private void updateDoubleLineClock() {
        updateCustomClock();
        if (migrateClocksToBlueprint()) {
            return;
        }
        mCanShowDoubleLineClock = mSecureSettings.getIntForUser(
            Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK, mView.getResources()
                    .getInteger(com.android.internal.R.integer.config_doublelineClockDefault),
            UserHandle.USER_CURRENT) != 0;

        if (mEnableCustomClock || mShowLockscreenWidgets) {
            mCanShowDoubleLineClock = false;
        }

        if (!mCanShowDoubleLineClock) {
            mUiExecutor.execute(() -> displayClock(KeyguardClockSwitch.SMALL,
                    /* animate */ true));
        }
    }
    
    private void updateCustomClock() {
        ViewGroup.LayoutParams smallClockParams = mSmallClockFrame.getLayoutParams();
        ViewGroup.LayoutParams largeClockParams = mLargeClockFrame.getLayoutParams();
        RelativeLayout.LayoutParams newStatusAreaParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        newStatusAreaParams.addRule(RelativeLayout.BELOW, mEnableCustomClock ? R.id.clock_ls : R.id.lockscreen_clock_view);
        if (mStatusArea != null 
            && !paramsEquals(mStatusArea.getLayoutParams(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, mEnableCustomClock ? R.id.clock_ls : R.id.lockscreen_clock_view)) {
            mStatusArea.setLayoutParams(newStatusAreaParams);
        }
        if (mEnableCustomClock) {
            if (!paramsEquals(smallClockParams, 0, 0, 0)) {
                smallClockParams.width = 0;
                smallClockParams.height = 0;
                mSmallClockFrame.setLayoutParams(smallClockParams);
            }
            if (!paramsEquals(largeClockParams, 0, 0, 0)) {
                largeClockParams.width = 0;
                largeClockParams.height = 0;
                mLargeClockFrame.setLayoutParams(largeClockParams);
            }
            mCustomClockFrame.setVisibility(View.VISIBLE);
        } else {
            if (!paramsEquals(smallClockParams, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0)) {
                smallClockParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                smallClockParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mSmallClockFrame.setLayoutParams(smallClockParams);
            }
            if (!paramsEquals(largeClockParams, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 0)) {
                largeClockParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                largeClockParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                mLargeClockFrame.setLayoutParams(largeClockParams);
            }
            mCustomClockFrame.setVisibility(View.GONE);
        }
    }

    private boolean paramsEquals(ViewGroup.LayoutParams params1, int width, int height, int belowRule) {
        if (params1 instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams relativeParams = (RelativeLayout.LayoutParams) params1;
            return relativeParams.width == width && relativeParams.height == height &&
                   relativeParams.getRules()[RelativeLayout.BELOW] == belowRule;
        } else {
            return params1.width == width && params1.height == height;
        }
    }

    private void setDateWeatherVisibility() {
        updateCustomClock();
        if (mDateWeatherView != null) {
            mUiExecutor.execute(() -> {
                if (mEnableCustomClock) {
                    mDateWeatherView.setVisibility(View.GONE);
                } else {
                    mDateWeatherView.setVisibility(clockHasCustomWeatherDataDisplay()
                            ? mKeyguardDateWeatherViewInvisibility
                            : View.VISIBLE);
                }
            });
        }
    }

    private void setWeatherVisibility() {
        if (mWeatherView != null) {
            mUiExecutor.execute(() -> {
                mWeatherView.setVisibility(
                        mSmartspaceController.isWeatherEnabled() ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void updateKeyguardStatusAreaVisibility() {
        if (mStatusArea != null) {
            mUiExecutor.execute(() -> {
                mStatusArea.setVisibility(
                        mIsActiveDreamLockscreenHosted ? View.INVISIBLE : View.VISIBLE);
            });
        }
    }

    /**
     * Sets the clipChildren property on relevant views, to allow the smartspace to draw out of
     * bounds during the unlock transition.
     */
    private void setClipChildrenForUnlock(boolean clip) {
        if (mStatusArea != null) {
            mStatusArea.setClipChildren(clip);
        }
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("currentClockSizeLarge: " + (mCurrentClockSize == LARGE));
        pw.println("mCanShowDoubleLineClock: " + mCanShowDoubleLineClock);
        mView.dump(pw, args);
        mClockRegistry.dump(pw, args);
        ClockController clock = getClock();
        if (clock != null) {
            clock.dump(pw);
        }
        final RegionSampler smallRegionSampler = mClockEventController.getSmallRegionSampler();
        if (smallRegionSampler != null) {
            smallRegionSampler.dump(pw);
        }
        final RegionSampler largeRegionSampler = mClockEventController.getLargeRegionSampler();
        if (largeRegionSampler != null) {
            largeRegionSampler.dump(pw);
        }
    }

    /** Returns true if the clock handles the display of weather information */
    private boolean clockHasCustomWeatherDataDisplay() {
        ClockController clock = getClock();
        if (clock == null) {
            return false;
        }

        return ((mCurrentClockSize == LARGE) ? clock.getLargeClock() : clock.getSmallClock())
                .getConfig().getHasCustomWeatherDataDisplay() && !mShowWeather;
    }

    private void removeViewsFromStatusArea() {
        if (mStatusArea == null) {
            return;
        }
        for (int i = mStatusArea.getChildCount() - 1; i >= 0; i--) {
            final View childView = mStatusArea.getChildAt(i);
            if (childView.getTag(R.id.tag_smartspace_view) != null) {
                mStatusArea.removeViewAt(i);
            }
        }
    }
}
