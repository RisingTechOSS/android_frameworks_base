package com.android.systemui.clocks;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;

import com.android.internal.util.crdroid.ThemeUtils;
import com.android.systemui.res.R;
import com.android.systemui.Dependency;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.tuner.TunerService;

public class ClockStyle extends RelativeLayout implements TunerService.Tunable {

    private static final int[] CLOCK_LAYOUTS = {
            0,
            R.layout.keyguard_clock_oos,
            R.layout.keyguard_clock_center,
            R.layout.keyguard_clock_cos,
            R.layout.keyguard_clock_custom,
            R.layout.keyguard_clock_miui,
            R.layout.keyguard_clock_ide,
            R.layout.keyguard_clock_hyper,
            R.layout.keyguard_clock_stylish,
            R.layout.keyguard_clock_sidebar,
            R.layout.keyguard_clock_minimal,
            R.layout.keyguard_clock_minimal2
    };

    int[] centerClocks = {2, 4, 8, 9, 10, 11};

    private static final int DEFAULT_STYLE = 0; // Disabled
    private static final String CLOCK_STYLE_KEY = "clock_style";
    private static final String CLOCK_STYLE = "system:" + CLOCK_STYLE_KEY;
    
    private final KeyguardStateController mKeyguardStateController;

    private ThemeUtils mThemeUtils;
    private Context mContext;
    private View currentClockView;
    private int mClockStyle;

    private static final long UPDATE_INTERVAL_MILLIS = 15 * 1000;
    private final Handler mHandler;
    private long lastUpdateTimeMillis = 0;
    private boolean mIsOnKeyguard = false;

    public ClockStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = Dependency.get(Dependency.MAIN_HANDLER);
        mThemeUtils = new ThemeUtils(context);
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE);
        mKeyguardStateController = Dependency.get(KeyguardStateController.class);
        mKeyguardStateController.addCallback(new KeyguardStateController.Callback() {
            @Override
            public void onKeyguardShowingChanged() {
                mIsOnKeyguard = mKeyguardStateController.isShowing();
            }
            @Override
            public void onKeyguardFadingAwayChanged() {
                mIsOnKeyguard = false;
            }
            @Override
            public void onKeyguardGoingAwayChanged() {
                mIsOnKeyguard = false;
            }
        });
    }

    private void updateClockOverlays() {
        mThemeUtils.setOverlayEnabled(
                "android.theme.customization.smartspace",
                mClockStyle != 0 ? "com.android.systemui.hide.smartspace" : "com.android.systemui",
                "com.android.systemui");
        mThemeUtils.setOverlayEnabled(
                "android.theme.customization.smartspace_offset",
                mClockStyle != 0 && isCenterClock(mClockStyle)
                        ? "com.android.systemui.smartspace_offset.smartspace"
                        : "com.android.systemui",
                "com.android.systemui");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        updateClockView();
    }

    private void updateTextClockViews(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View childView = viewGroup.getChildAt(i);
                updateTextClockViews(childView);
                if (childView instanceof TextClock) {
                    ((TextClock) childView).refreshTime();
                }
            }
        }
    }

    public void onTimeChanged() {
        if (!mIsOnKeyguard) return;
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastUpdateTimeMillis >= UPDATE_INTERVAL_MILLIS) {
            if (currentClockView != null) {
                updateTextClockViews(currentClockView);
                lastUpdateTimeMillis = currentTimeMillis;
            }
        }
    }

    private void updateClockView() {
        if (currentClockView != null) {
            ((ViewGroup) currentClockView.getParent()).removeView(currentClockView);
            currentClockView = null;
        }
        if (mClockStyle > 0 && mClockStyle < CLOCK_LAYOUTS.length) {
            ViewStub stub = findViewById(R.id.clock_view_stub);
            if (stub != null) {
                stub.setLayoutResource(CLOCK_LAYOUTS[mClockStyle]);
                currentClockView = stub.inflate();
                int gravity = isCenterClock(mClockStyle) ? Gravity.CENTER : Gravity.START;
                if (currentClockView instanceof LinearLayout) {
                    ((LinearLayout) currentClockView).setGravity(gravity);
                }
            }
        }
        updateClockOverlays();
    }

    private boolean isCenterClock(int clockStyle) {
        for (int centerClock : centerClocks) {
            if (centerClock == clockStyle) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (CLOCK_STYLE.equals(key)) {
            mClockStyle = TunerService.parseInteger(newValue, 0);
            updateClockView();
        }
    }
}
