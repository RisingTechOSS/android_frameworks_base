package com.android.systemui.volume;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.android.systemui.res.R;
import com.android.systemui.util.ArcProgressWidget;

public class VolumeProgressImageView extends ImageView {

    private Context mContext;
    private AudioManager mAudioManager;
    private int mVolumePercent = -1;
    private Handler mHandler;
    private Runnable mVolumeUpdater;

    public VolumeProgressImageView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public VolumeProgressImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public VolumeProgressImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startVolumeUpdates();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopVolumeUpdates();
    }

    private void init() {
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mVolumeUpdater = new Runnable() {
            @Override
            public void run() {
                updateVolumePercent();
                mHandler.postDelayed(this, 1000);
            }
        };
    }

    private void startVolumeUpdates() {
        mVolumeUpdater.run();
    }

    private void stopVolumeUpdates() {
        mHandler.removeCallbacks(mVolumeUpdater);
    }

    private void updateVolumePercent() {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int newVolumePercent = (currentVolume * 100) / maxVolume;
        if (newVolumePercent != mVolumePercent) {
            mVolumePercent = newVolumePercent;
            updateImageView();
        }
    }

    private void updateImageView() {
        Bitmap widgetBitmap = ArcProgressWidget.generateBitmap(
                mContext,
                mVolumePercent == - 1 ? 0 : mVolumePercent,
                mVolumePercent == - 1 ? "..." : String.valueOf(mVolumePercent) + "%",
                40,
                ContextCompat.getDrawable(mContext, R.drawable.ic_volume_up),
                36
        );
        setImageBitmap(widgetBitmap);
    }
}
