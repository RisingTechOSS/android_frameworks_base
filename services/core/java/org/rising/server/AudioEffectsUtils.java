/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
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
package org.rising.server;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.*;
import android.util.Log;

public class AudioEffectsUtils {

    private static final int EFFECT_PRIORITY = Integer.MAX_VALUE;

    private final AudioManager mAudioManager;

    private BassBoost mBassBoost;
    private LoudnessEnhancer mLoudnessEnhancer;

    public AudioEffectsUtils(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void initializeEffects() {
        try {
            mBassBoost = new BassBoost(EFFECT_PRIORITY, 0);
            mLoudnessEnhancer = new LoudnessEnhancer(0);
            mBassBoost.setEnabled(true);
            mLoudnessEnhancer.setEnabled(true);
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error initializing audio effects", e);
        }
    }

    public void releaseEffects() {
        releaseEffect(mBassBoost);
        releaseEffect(mLoudnessEnhancer);
    }

    public void updateEffects(int loudnessGain, int bassBoostStrength) {
        try {
            setBassBoost(bassBoostStrength);
            setLoudnessEnhancer(loudnessGain);
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error updating audio effects", e);
        }
    }

    private void setBassBoost(int strength) {
        if (mBassBoost == null) return;
        mBassBoost.setStrength((short) strength);
    }

    private void setLoudnessEnhancer(int gain) {
        if (mLoudnessEnhancer == null) return;
        mLoudnessEnhancer.setTargetGain(gain);
    }

    private void releaseEffect(AudioEffect effect) {
        try {
            if (effect != null) {
                effect.setEnabled(false);
                effect.release();
            }
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error releasing audio effects", e);
        }
    }
}
