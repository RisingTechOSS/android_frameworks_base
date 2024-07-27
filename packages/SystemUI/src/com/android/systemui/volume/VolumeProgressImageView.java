/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.volume;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.android.systemui.res.R;
import com.android.systemui.util.ArcProgressWidget;

public class VolumeProgressImageView extends ImageView {

    private static final int UPDATE_INTERVAL_MS = 1000; // Update every 1 second

    private Context mContext;
    private AudioManager mAudioManager;
    private int mVolumePercent = -1;
    private Handler mBackgroundHandler;
    private Handler mMainHandler;
    private Runnable mVolumeUpdater;
    private Bitmap mCurrentBitmap;

    public VolumeProgressImageView(Context context) {
        super(context);
        init(context);
    }

    public VolumeProgressImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VolumeProgressImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
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

    private void init(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        HandlerThread handlerThread = new HandlerThread("VolumeProgressImageViewThread");
        handlerThread.start();
        mBackgroundHandler = new Handler(handlerThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());

        mVolumeUpdater = new Runnable() {
            @Override
            public void run() {
                updateVolumePercent();
                mBackgroundHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
    }

    private void startVolumeUpdates() {
        mVolumeUpdater.run();
    }

    private void stopVolumeUpdates() {
        mBackgroundHandler.removeCallbacks(mVolumeUpdater);
        mBackgroundHandler.getLooper().quit();
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
        final int volumePercent = mVolumePercent == -1 ? 0 : mVolumePercent;
        final String volumeText = mVolumePercent == -1 ? "..." : String.valueOf(mVolumePercent) + "%";
        final Bitmap bitmap = ArcProgressWidget.generateBitmap(
                mContext,
                volumePercent,
                volumeText,
                40,
                ContextCompat.getDrawable(mContext, R.drawable.ic_volume_up),
                36
        );

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                recycleBitmap();
                mCurrentBitmap = bitmap;
                setImageBitmap(mCurrentBitmap);
            }
        });
    }

    private void recycleBitmap() {
        if (mCurrentBitmap != null && !mCurrentBitmap.isRecycled()) {
            mCurrentBitmap.recycle();
            mCurrentBitmap = null;
        }
    }
}
