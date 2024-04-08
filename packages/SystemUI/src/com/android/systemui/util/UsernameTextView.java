/*
 * Copyright (C) 2023 The risingOS Android Project
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

import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.systemui.util.UserUtils;

public class UsernameTextView extends TextView {

    private String mUsername;
    private Context mContext;
    private UserUtils mUserUtils;

    public UsernameTextView(Context context) {
        super(context);
        mContext = context;
    }

    public UsernameTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public UsernameTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        init(mContext);
    }

    private void init(Context context) {
        mUserUtils = UserUtils.Companion.getInstance(context);
        mUsername = mUserUtils.getUserName();
        setText(mUsername);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String newUsername = mUserUtils.getUserName();
                if (!mUsername.equals(newUsername)) {
                    mUsername = newUsername;
                    setText(mUsername);
                }
                handler.postDelayed(this, 2000);
            }
        }, 2000);
    }
}
