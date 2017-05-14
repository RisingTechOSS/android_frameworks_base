/*
 * Copyright (C) 2018-2022 crDroid Android Project
 * Copyright (C) 2018-2019 AICP
 * Copyright (C) 2023 the risingOS Android Project
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

package com.android.systemui.rising.logo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;

import java.util.ArrayList;

public class LogoImageView extends ImageView {

    private Context mContext;

    private boolean mAttached;

    private boolean mShowLogo;
    public int mLogoPosition;
    private int mLogoStyle;
    private int mTintColor = Color.WHITE;
    
    private ImageView leftLogo;
    private ImageView rightLogo;

    private static final String STATUS_BAR_LOGO =
            Settings.System.STATUS_BAR_LOGO;
    private static final String STATUS_BAR_LOGO_POSITION =
            Settings.System.STATUS_BAR_LOGO_POSITION;
    private static final String STATUS_BAR_LOGO_STYLE =
            Settings.System.STATUS_BAR_LOGO_STYLE;

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(STATUS_BAR_LOGO), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(STATUS_BAR_LOGO_POSITION),
                    false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(STATUS_BAR_LOGO_STYLE),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public LogoImageView(Context context) {
        this(context, null);
    }

    public LogoImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        View parent = (View) getParent();
        if (parent != null) {
            leftLogo = parent.findViewById(R.id.statusbar_logo_left);
            rightLogo = parent.findViewById(R.id.statusbar_logo_right);
            updateSettings();
        }
        
        mAttached = true;

        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached)
            return;

        mAttached = false;
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(areas, this, tint);
        if (mShowLogo) {
            updateLogo();
        }
    }

    private void updateLogoVisibility() {
    	if (leftLogo != null) {
    	    if (mShowLogo && mLogoPosition == 0) {
    	        leftLogo.setVisibility(View.VISIBLE);
    	    } else {
    	        leftLogo.setVisibility(View.GONE);
    	    }
    	} else if (rightLogo != null) {
    	    if (mShowLogo && mLogoPosition == 1) {
    	        rightLogo.setVisibility(View.VISIBLE);
    	    } else {
    	        rightLogo.setVisibility(View.GONE);
    	    }
    	}
    }

    public void updateLogo() {
    	Drawable drawable = null;
	Resources resources = mContext.getResources();
	int drawableResId;
        switch (mLogoStyle) {
            case 0:
            default:
                drawableResId = R.drawable.ic_rising_logo;
                break;
            case 1:
                drawableResId = R.drawable.ic_android_logo;
                break;
            case 2:
                drawableResId = R.drawable.ic_adidas;
                break;
            case 3:
                drawableResId = R.drawable.ic_alien;
                break;
            case 4:
                drawableResId = R.drawable.ic_apple_logo;
                break;
            case 5:
                drawableResId = R.drawable.ic_avengers;
                break;
            case 6:
                drawableResId = R.drawable.ic_batman;
                break;
            case 7:
                drawableResId = R.drawable.ic_batman_tdk;
                break;
            case 8:
                drawableResId = R.drawable.ic_beats;
                break;
            case 9:
                drawableResId = R.drawable.ic_biohazard;
                break;
            case 10:
                drawableResId = R.drawable.ic_blackberry;
                break;
            case 11:
                drawableResId = R.drawable.ic_cannabis;
                break;
            case 12:
                drawableResId = R.drawable.ic_emoticon_cool;
                break;
            case 13:
                drawableResId = R.drawable.ic_emoticon_devil;
                break;
            case 14:
                drawableResId = R.drawable.ic_fire;
                break;
            case 15:
                drawableResId = R.drawable.ic_heart;
                break;
            case 16:
                drawableResId = R.drawable.ic_nike;
                break;
            case 17:
                drawableResId = R.drawable.ic_pac_man;
                break;
            case 18:
                drawableResId = R.drawable.ic_puma;
                break;
            case 19:
                drawableResId = R.drawable.ic_rog;
                break;
            case 20:
                drawableResId = R.drawable.ic_spiderman;
                break;
            case 21:
                drawableResId = R.drawable.ic_superman;
                break;
            case 22:
                drawableResId = R.drawable.ic_windows;
                break;
            case 23:
                drawableResId = R.drawable.ic_xbox;
                break;
            case 24:
                drawableResId = R.drawable.ic_ghost;
                break;
            case 25:
                drawableResId = R.drawable.ic_ninja;
                break;
            case 26:
                drawableResId = R.drawable.ic_robot;
                break;
            case 27:
                drawableResId = R.drawable.ic_ironman;
                break;
            case 28:
                drawableResId = R.drawable.ic_captain_america;
                break;
            case 29:
                drawableResId = R.drawable.ic_flash;
                break;
            case 30:
                drawableResId = R.drawable.ic_tux_logo;
                break;
            case 31:
                drawableResId = R.drawable.ic_ubuntu_logo;
                break;
            case 32:
                drawableResId = R.drawable.ic_mint_logo;
                break;
        }
	drawable = resources.getDrawable(drawableResId);
        drawable.setTint(mTintColor);
        setImageDrawable(drawable);
    }

    public void updateSettings() {
        mShowLogo = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_LOGO, 0) != 0;
        mLogoPosition = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_LOGO_POSITION, 0);
        mLogoStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_LOGO_STYLE, 0);
        if (!mShowLogo) {
            setImageDrawable(null);
            setVisibility(View.GONE);
            return;
        }
        updateLogo();
        updateLogoVisibility();
    }
}
