package com.android.systemui.util;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.systemui.util.ArcProgressWidget;

public class MemoryProgressImageView extends ImageView {

    private Context mContext;
    private ActivityManager mActivityManager;

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
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateImageView();
                handler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private void updateImageView() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(memoryInfo);
        long usedMemory = memoryInfo.totalMem - memoryInfo.availMem;
        int usedMemoryPercentage = (int) ((usedMemory * 100) / memoryInfo.totalMem);
        Bitmap widgetBitmap = ArcProgressWidget.generateBitmap(
                mContext,
                usedMemoryPercentage,
                String.valueOf(usedMemoryPercentage) + "%",
                40,
                "RAM",
                28
        );
        setImageBitmap(widgetBitmap);
    }
}
