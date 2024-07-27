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
package com.android.systemui.util;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.systemui.util.ArcProgressWidget;

public class MemoryProgressImageView extends ImageView {

    private static final int UPDATE_INTERVAL_MS = 5000;

    private Context mContext;
    private ActivityManager mActivityManager;
    private Bitmap mCurrentBitmap;
    private Handler mBackgroundHandler;
    private Handler mMainHandler;

    public MemoryProgressImageView(Context context) {
        super(context);
        init(context);
    }

    public MemoryProgressImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MemoryProgressImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        HandlerThread handlerThread = new HandlerThread("MemoryProgressImageViewThread");
        handlerThread.start();
        mBackgroundHandler = new Handler(handlerThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());

        scheduleUpdate();
    }

    private void scheduleUpdate() {
        mBackgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateImageView();
                scheduleUpdate();
            }
        }, UPDATE_INTERVAL_MS);
    }

    private void updateImageView() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(memoryInfo);
        long usedMemory = memoryInfo.totalMem - memoryInfo.availMem;
        int usedMemoryPercentage = (int) ((usedMemory * 100) / memoryInfo.totalMem);
        final Bitmap bitmap = ArcProgressWidget.generateBitmap(
                mContext,
                usedMemoryPercentage,
                String.valueOf(usedMemoryPercentage) + "%",
                40,
                "RAM",
                28
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBackgroundHandler.getLooper().quit();
    }
}
