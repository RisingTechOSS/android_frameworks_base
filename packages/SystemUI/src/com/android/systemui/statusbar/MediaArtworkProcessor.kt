/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.util.MathUtils
import android.graphics.BitmapFactory
import com.android.internal.graphics.ColorUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.MediaNotificationProcessor
import javax.inject.Inject

@SysUISingleton
class MediaArtworkProcessor @Inject constructor() {
    private val mTmpSize = Point()
    private var mArtworkCache: Bitmap? = null

    fun processArtwork(context: Context, artwork: Bitmap): Bitmap? {
        if (mArtworkCache != null) {
            return mArtworkCache
        }

        val rect = Rect(0, 0, artwork.width, artwork.height)
        context.display?.getSize(mTmpSize)
        MathUtils.fitRect(rect, Math.max(mTmpSize.x / DOWNSAMPLE, mTmpSize.y / DOWNSAMPLE))

        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inSampleSize = calculateInSampleSize(artwork.width, artwork.height, rect.width(), rect.height())
        options.inMutable = true

        val inBitmap: Bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inBitmap)
        canvas.drawBitmap(artwork, null, Rect(0, 0, rect.width(), rect.height()), null)

        val outBitmap = Bitmap.createBitmap(inBitmap)

        inBitmap.recycle()

        return outBitmap
    }

    fun clearCache() {
        mArtworkCache?.recycle()
        mArtworkCache = null
    }

    companion object {
        private const val DOWNSAMPLE = 2
        private const val COLOR_ALPHA = 255
        private const val BLUR_RADIUS = 1f
        private const val TAG = "MediaArtworkProcessor"

        private fun calculateInSampleSize(
            originalWidth: Int, originalHeight: Int, requiredWidth: Int, requiredHeight: Int
        ): Int {
            var inSampleSize = 1
            if (originalWidth > requiredWidth || originalHeight > requiredHeight) {
                val halfWidth = originalWidth / 2
                val halfHeight = originalHeight / 2

                while ((halfWidth / inSampleSize) >= requiredWidth && (halfHeight / inSampleSize) >= requiredHeight) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
