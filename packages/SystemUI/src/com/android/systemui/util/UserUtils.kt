/*
 * Copyright (C) 2024 The risingOS Android Project
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

package com.android.systemui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import android.widget.ImageView
import com.android.internal.util.UserIcons
import com.android.settingslib.drawable.CircleFramedDrawable

class UserUtils private constructor(private val context: Context) {
    private val userManager: UserManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    fun setUserAvatarToView(avatarView: ImageView) {
        avatarView.apply {
            setImageDrawable(getCircularUserIcon())
            visibility = View.VISIBLE
            setOnClickListener { navigateToUserSettings() }
        }
    }

    fun getCircularUserIcon(): Drawable {
        var bitmapUserIcon = userManager.getUserIcon(UserHandle.myUserId())
        if (bitmapUserIcon == null) {
            val defaultUserIcon = UserIcons.getDefaultUserIcon(
                    context.resources, UserHandle.myUserId(), false) as Drawable
            bitmapUserIcon = UserIcons.convertToBitmap(defaultUserIcon)
        }
        val iconSize = context.resources.getDimension(com.android.internal.R.dimen.user_icon_size).toInt()
        val drawableUserIcon = CircleFramedDrawable(bitmapUserIcon,
                iconSize) as Drawable
        return drawableUserIcon
    }

    private fun navigateToUserSettings() {
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName("com.android.settings", "com.android.settings.Settings\$UserSettingsActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    fun getUserName(): String {
        return userManager.userName
    }

    companion object {
        @Volatile
        private var instance: UserUtils? = null

        fun getInstance(context: Context): UserUtils {
            return instance ?: synchronized(this) {
                instance ?: UserUtils(context).also { instance = it }
            }
        }
    }
}
