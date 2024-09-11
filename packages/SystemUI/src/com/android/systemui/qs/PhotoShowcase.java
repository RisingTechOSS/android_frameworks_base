/*
 * Copyright (C) 2024 the risingOS Android Project
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
package com.android.systemui.qs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.res.R;
import com.android.systemui.lockscreen.ActivityLauncherUtils;

public class PhotoShowcase extends ImageView {

    private Context mContext;
    private ActivityLauncherUtils mActivityLauncherUtils;

    private float radius;
    private Path path;
    private RectF rect;

    public PhotoShowcase(Context context) {
        super(context);
    }

    public PhotoShowcase(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhotoShowcase(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        init(getContext());
        updateImage();
    }

    private void init(Context context) {
        radius = context.getResources().getDimension(R.dimen.qs_controls_container_radius);
        path = new Path();
        mContext = context;
        mActivityLauncherUtils = new ActivityLauncherUtils(mContext);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                launchGalleryApp();
            }
        });
    }
    
    private void launchGalleryApp() {
        Intent galleryIntent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        mActivityLauncherUtils.launchAppIfAvailable(galleryIntent, R.string.gallery);
    }

    private void updateImage() {
        if (mContext == null) return;
        boolean qsPhotoShowCaseEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                "qs_widgets_photo_showcase_enabled", 0, UserHandle.USER_CURRENT) != 0;
        if (!qsPhotoShowCaseEnabled) {
            return;
        }
        String imagePath = Settings.System.getString(mContext.getContentResolver(), "qs_widgets_photo_showcase_path");
        if (imagePath != null) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                Log.e("PhotoShowcase", "Error loading image", e);
            }
        } else {
            setImageDrawable(mContext.getDrawable(R.drawable.qs_widget_default_photo));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect = new RectF(0, 0, this.getWidth(), this.getHeight());
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        super.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setImageBitmap(null);
        setImageDrawable(null);
    }
}
