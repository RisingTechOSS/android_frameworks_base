/*
 * Copyright (C) 2019 The PixelExperience Project
 *               2023-2024 The risingOS Android Project
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
package com.android.server.policy;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

import com.android.internal.util.android.VibrationUtils;

public class SwipeToScreenshotListener implements PointerEventListener {
    private static final String TAG = "SwipeToScreenshotListener";
    private static final int THREE_GESTURE_STATE_NONE = 0;
    private static final int THREE_GESTURE_STATE_DETECTING = 1;
    private static final int THREE_GESTURE_STATE_DETECTED_FALSE = 2;
    private static final int THREE_GESTURE_STATE_DETECTED_TRUE = 3;
    private static final int THREE_GESTURE_STATE_NO_DETECT = 4;
    private static final int THREE_GESTURE_STATE_LONG_PRESS = 5;
    private static final long LONG_PRESS_TIMEOUT = 800;
    private static final float MAX_MOVE_THRESHOLD = 50.0f;
    private boolean mBootCompleted;
    private Context mContext;
    private boolean mDeviceProvisioned = false;
    private float[] mInitMotionY;
    private float[] mInitMotionX;
    private int[] mPointerIds;
    private int mThreeGestureState = THREE_GESTURE_STATE_NONE;
    private int mThreeGestureThreshold;
    private int mThreshold;
    private final Callbacks mCallbacks;
    private DisplayMetrics mDisplayMetrics;
    private long mDownTime;
    private boolean isLongPressTriggered = false;

    public SwipeToScreenshotListener(Context context, Callbacks callbacks) {
        mPointerIds = new int[3];
        mInitMotionY = new float[3];
        mInitMotionX = new float[3];
        mContext = context;
        mCallbacks = callbacks;
        mDisplayMetrics = mContext.getResources().getDisplayMetrics();
        mThreshold = (int) (50.0f * mDisplayMetrics.density);
        mThreeGestureThreshold = mThreshold * 3;
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        if (!mBootCompleted) {
            mBootCompleted = SystemProperties.getBoolean("sys.boot_completed", false);
            return;
        }
        if (!mDeviceProvisioned) {
            mDeviceProvisioned = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
            return;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mDownTime = event.getDownTime();
            changeThreeGestureState(THREE_GESTURE_STATE_NONE);
            isLongPressTriggered = false;
        } else if (mThreeGestureState == THREE_GESTURE_STATE_NONE && event.getPointerCount() == 3) {
            if (checkIsStartThreeGesture(event)) {
                changeThreeGestureState(THREE_GESTURE_STATE_DETECTING);
                for (int i = 0; i < 3; i++) {
                    mPointerIds[i] = event.getPointerId(i);
                    mInitMotionY[i] = event.getY(i);
                    mInitMotionX[i] = event.getX(i);
                }
            } else {
                changeThreeGestureState(THREE_GESTURE_STATE_NO_DETECT);
            }
        }
        if (mThreeGestureState == THREE_GESTURE_STATE_DETECTING) {
            if (event.getPointerCount() != 3) {
                changeThreeGestureState(THREE_GESTURE_STATE_DETECTED_FALSE);
                return;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float distanceY = 0.0f;
                float distanceX = 0.0f;
                int i = 0;
                while (i < 3) {
                    int index = event.findPointerIndex(mPointerIds[i]);
                    if (index < 0 || index >= 3) {
                        changeThreeGestureState(THREE_GESTURE_STATE_DETECTED_FALSE);
                        return;
                    } else {
                        distanceY += event.getY(index) - mInitMotionY[i];
                        distanceX += event.getX(index) - mInitMotionX[i];
                        i++;
                    }
                }
                if (Math.abs(distanceY) >= ((float) mThreeGestureThreshold) || Math.abs(distanceX) >= ((float) mThreeGestureThreshold)) {
                    changeThreeGestureState(THREE_GESTURE_STATE_DETECTED_TRUE);
                    doAction();
                    return;
                }
                if (!isLongPressTriggered && event.getEventTime() - mDownTime >= LONG_PRESS_TIMEOUT && Math.abs(distanceY) < MAX_MOVE_THRESHOLD && Math.abs(distanceX) < MAX_MOVE_THRESHOLD) {
                    changeThreeGestureState(THREE_GESTURE_STATE_LONG_PRESS);
                    doLongPressAction();
                    return;
                }
            }
        }
    }

    private void doAction() {
        final int swipeGestureAction = Settings.System.getInt(mContext.getContentResolver(),
                "three_finger_gesture_action", 0);
        if (mCallbacks == null || swipeGestureAction == 0) return;
        doActionInternal(swipeGestureAction);
    }

    private void doLongPressAction() {
        final int longPressGestureAction = Settings.System.getInt(mContext.getContentResolver(),
                "three_finger_long_press_action", 0);
        if (mCallbacks == null || longPressGestureAction == 0) return;
        doActionInternal(longPressGestureAction);
        isLongPressTriggered = true;
    }
    
    private void doActionInternal(int action) {
        switch (action) {
            case 1:
                mCallbacks.onToggleTorch();
                break;
            case 2:
                mCallbacks.onMediaKeyDispatch();
                break;
            case 3:
                mCallbacks.onToggleVolumePanel();
                break;
            case 4:
                mCallbacks.onTurnScreenOnOrOff();
                break;
            case 5:
                mCallbacks.onClearAllNotifications();
                break;
            case 6:
                mCallbacks.onToggleRingerModes();
                break;
            case 7:
                mCallbacks.onScreenshotTaken();
                break;
            case 8:
                mCallbacks.onKillApp();
                break;
            case 9:
                mCallbacks.onVoiceLaunch();
                break;
            case 10:
                mCallbacks.onLaunchSearch();
                break;
            default:
                break;
        }
        VibrationUtils.triggerVibration(mContext, 2);
    }

    private void changeThreeGestureState(int state) {
        if (mThreeGestureState != state) {
            mThreeGestureState = state;
            boolean shouldEnableProp = mThreeGestureState == THREE_GESTURE_STATE_DETECTED_TRUE ||
                mThreeGestureState == THREE_GESTURE_STATE_DETECTING;
            try {
                SystemProperties.set("sys.android.screenshot", shouldEnableProp ? "true" : "false");
            } catch(Exception e) {
                Log.e(TAG, "Exception when setprop", e);
            }
        }
    }

    private boolean checkIsStartThreeGesture(MotionEvent event) {
        if (event.getEventTime() - event.getDownTime() > 500) {
            return false;
        }
        int height = mDisplayMetrics.heightPixels;
        int width = mDisplayMetrics.widthPixels;
        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        for (int i = 0; i < event.getPointerCount(); i++) {
            float x = event.getX(i);
            float y = event.getY(i);
            if (y > ((float) (height - mThreshold))) {
                return false;
            }
            maxX = Math.max(maxX, x);
            minX = Math.min(minX, x);
            maxY = Math.max(maxY, y);
            minY = Math.min(minY, y);
        }
        if (maxY - minY <= mDisplayMetrics.density * 150.0f) {
            return maxX - minX <= ((float) (width < height ? width : height));
        }
        return false;
    }

    interface Callbacks {
        void onVoiceLaunch();
        void onLaunchSearch();
        void onClearAllNotifications();
        void onMediaKeyDispatch();
        void onScreenshotTaken();
        void onToggleRingerModes();
        void onToggleTorch();
        void onToggleVolumePanel();
        void onKillApp();
        void onTurnScreenOnOrOff();
    }
}
