package com.android.systemui.clocks;

import com.android.systemui.res.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class LsWidget extends LinearLayout {
    private final Context mContext;
    private ProgressBar mBatteryProgress;
    private TextView mBatteryStatus;
    private TextView mBatteryLevel;
    protected String chargingSpeed;
    protected String chargingStatus;
    protected int extraCurrent;
    protected int extraLevel;
    protected int extraStatus;
    private BroadcastReceiver mBatteryReceiver;
    private ProgressBar mVolumeProgress;
    private TextView mVolumeLevel;
    protected float temperature;
    private final AudioManager mAudioManager;
    private final Handler mHandler;
    private int lastVolumeLevel;
    private final int mCurrentDivider;

    public LsWidget(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();
        lastVolumeLevel = -1;
        mCurrentDivider = mContext.getResources().getInteger(R.integer.config_currentInfoDivider);
        initBatteryReceiver();
        context.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void initBatteryReceiver() {
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                extraStatus = intent.getIntExtra("status", 1);
                extraLevel = intent.getIntExtra("level", 0);
                extraCurrent = intent.getIntExtra("max_charging_current", -1) / mCurrentDivider;
                temperature = intent.getIntExtra("temperature", -1) / 10;
                Resources resources = context.getResources();
                chargingStatus = getBatteryStatusString(resources, extraStatus);
                chargingSpeed = getChargingSpeedString(extraStatus, extraLevel);
                initBatteryStatus();
            }
        };
    }

    private String getBatteryStatusString(Resources resources, int status) {
        switch (status) {
            case 5:
            case 100:
                return resources.getString(R.string.battery_info_status_full);
            case 2:
                return resources.getString(R.string.battery_info_status_charging);
            case 3:
                return resources.getString(R.string.battery_info_status_discharging);
            case 4:
                return resources.getString(R.string.battery_info_status_not_charging);
            default:
                return "";
        }
    }

    private String getChargingSpeedString(int status, int level) {
        if (status == 2 && level < 100) {
            return " • " + extraCurrent + "mA";
        }
        return "";
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBatteryProgress = findViewById(R.id.battery_progressbar);
        mVolumeProgress = findViewById(R.id.volume_progressbar);
        mBatteryLevel = findViewById(R.id.battery_level);
        mBatteryStatus = findViewById(R.id.battery_status);
        mVolumeLevel = findViewById(R.id.volume_level);

        initSoundManager();
        startVolumeMonitor();
    }

    protected void initBatteryStatus() {
        mBatteryProgress.setProgress(extraLevel);
        mBatteryProgress.setInterpolator(new PathInterpolator(0.33f, 0.11f, 0.2f, 1.0f));
        mBatteryProgress.setIndeterminate(extraStatus == 2 && extraLevel != 100);
        mBatteryLevel.setText(extraLevel + "%");
        mBatteryStatus.setText(chargingStatus + chargingSpeed);
    }

    protected void initSoundManager() {
        int volLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volPercent = (int) (((float) volLevel / maxVolLevel) * 100);
        mVolumeProgress.setProgress(volPercent);
        mVolumeLevel.setText(volPercent + "%");
    }

    private void startVolumeMonitor() {
        mHandler.post(volumeRunnable);
    }

    private final Runnable volumeRunnable = new Runnable() {
        @Override
        public void run() {
            int currentVolumeLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (currentVolumeLevel != lastVolumeLevel) {
                lastVolumeLevel = currentVolumeLevel;
                initSoundManager();
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mBatteryReceiver);
        mHandler.removeCallbacks(volumeRunnable);
    }
}
