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

    private static final short[] BASS_BOOST_STRENGTH = {0, 650, 650, 550, 650};
    private static final short[] REVERB_PRESETS = {
            PresetReverb.PRESET_NONE,
            PresetReverb.PRESET_SMALLROOM,
            PresetReverb.PRESET_LARGEHALL,
            PresetReverb.PRESET_PLATE,
            PresetReverb.PRESET_MEDIUMROOM
    };

    private static final int mVariant = 0;
    private static final int mChannelCount = 1;
    private static final int[] bandVal = {31, 62, 125, 250,  500, 1000, 2000, 4000, 8000, 16000};
    private static final int maxBandCount = bandVal.length;

    private static final int[][] LOUDNESS_ENHANCER_SETTINGS = {
        {0},   // Disabled
        {650}, // Music
        {650}, // Games
        {550}, // Theater
        {650}   // Smart
    };

    private static final float[][] EQ_GAINS = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},      // Disabled
        {2.5f, 1.5f, 1.0f, 0, -0.5f, 0, 1.0f, 1.5f, 2.0f, 3.0f},     // Music
        {3.0f, 2.0f, 1.5f, 0.5f, 0, 0.5f, 1.5f, 2.0f, 2.5f, 3.5f},      // Games
        {3.5f, 2.5f, 2.0f, 1.0f, 0.5f, 1.0f, 2.0f, 2.5f, 3.0f, 4.0f},      // Theater
        {2.0f, 1.0f, 0.5f, -0.5f, 0, 0.5f, 1.5f, 2.0f, 1.5f, 1.0f}      // Smart
    };

    private static final float[][] MBC_SETTINGS = {
        {60, 60, 1.0f, -30, 5},  // Disabled
        {35, 75, 2.5f, -25, 6},  // Music
        {30, 60, 3.0f, -20, 4},  // Games
        {45, 85, 3.5f, -15, 8},  // Theater
        {40, 70, 2.0f, -28, 5}   // Smart
    };

    private static final float[][] ELC_CURVE = {
        {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f}, // Reference (85dB SPL)
        {1.1f, 1.05f, 1.0f, 1.0f, 0.95f, 0.95f, 0.9f, 0.9f, 0.85f, 0.85f}, // Music (85dB SPL)
        {1.2f, 1.15f, 1.1f, 1.1f, 1.05f, 1.0f, 1.0f, 0.95f, 0.9f, 0.9f},  // Games (85dB SPL)
        {1.3f, 1.25f, 1.2f, 1.15f, 1.1f, 1.1f, 1.05f, 1.0f, 0.95f, 0.95f}, // Theater (85dB SPL)
        {1.05f, 1.05f, 1.0f, 1.0f, 0.95f, 0.95f, 0.9f, 0.9f, 0.85f, 0.85f}  // Smart (85dB SPL)
    };

    private final AudioManager mAudioManager;
    private int mCurrentMode = 0;

    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private PresetReverb mPresetReverb;
    private LoudnessEnhancer mLoudnessEnhancer;
    private DynamicsProcessing mDynamicsProcessing;
    private DynamicsProcessing.Eq mEq;
    private DynamicsProcessing.Mbc mMbc;

    public AudioEffectsUtils(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void initializeEffects() {
        try {
            mEqualizer = new Equalizer(EFFECT_PRIORITY, 0);
            mBassBoost = new BassBoost(EFFECT_PRIORITY, 0);
            mPresetReverb = new PresetReverb(EFFECT_PRIORITY, 0);
            mLoudnessEnhancer = new LoudnessEnhancer(0);
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(mVariant, mChannelCount, true, maxBandCount, true, maxBandCount, true, maxBandCount, true);
            mDynamicsProcessing = new DynamicsProcessing(EFFECT_PRIORITY, 0, builder.build());
            mEq = new DynamicsProcessing.Eq(true, true, maxBandCount);
            mDynamicsProcessing.setEnabled(true);
            mEqualizer.setEnabled(true);
            mBassBoost.setEnabled(true);
            mPresetReverb.setEnabled(true);
            mLoudnessEnhancer.setEnabled(true);
            mDynamicsProcessing.setEnabled(true);
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error initializing audio effects", e);
        }
    }

    public void releaseEffects() {
        releaseEffect(mEqualizer);
        releaseEffect(mBassBoost);
        releaseEffect(mPresetReverb);
        releaseEffect(mLoudnessEnhancer);
        releaseEffect(mDynamicsProcessing);
    }

    public void setEffects(int mode) {
        mCurrentMode = mode;
        try {
            setEffects();
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error setting audio effects", e);
        }
    }

    private void setEffects() {
        setBandLevels();
        setBassBoost();
        setLoudnessEnhancer();
        setDynamicsProcessing();
    }

    private void setBandLevels() {
        if (mEqualizer == null) return;
        short[] bandMillibels = new short[mEqualizer.getNumberOfBands()];
        for (short i = 0; i < bandMillibels.length; i++) {
            bandMillibels[i] = getBandLevelForMode(i);
            mEqualizer.setBandLevel(i, bandMillibels[i]);
        }
    }

    private short getBandLevelForMode(short band) {
        int centerBand = mEqualizer.getNumberOfBands() / 2;
        int millibels = mEqualizer.getBandLevelRange()[1];
        float elcFactor = ELC_CURVE[mCurrentMode][band];
        float targetLevelFloat;
        switch (mCurrentMode) {
            case 1: // Music
                targetLevelFloat = calculateBandLevel(millibels, band, centerBand, -1.4f, -1.3f, -1.5f) * elcFactor;
                break;
            case 2: // Game
                targetLevelFloat = calculateBandLevel(millibels, band, centerBand, -1.5f, -1.4f, -1.6f) * elcFactor;
                break;
            case 3: // Theater
                targetLevelFloat = calculateBandLevel(millibels, band, centerBand, -1.6f, -1.5f, -1.7f) * elcFactor;
                break;
            case 4: // Smart
                targetLevelFloat = calculateBandLevel(millibels, band, centerBand, -1.4f, -1.3f, -1.4f) * elcFactor;
                break;
            default:
                targetLevelFloat = millibels * 0.95f * elcFactor;
                break;
        }
        int targetLevel = (int) Math.max(0, Math.min(targetLevelFloat, millibels));
        return (short) targetLevel;
    }

    private int calculateBandLevel(int millibels, short band, int centerBand, double centerFactor, double lowerFactor, double upperFactor) {
        if (band == centerBand) {
            return (int) (millibels * centerFactor);
        } else if (band < centerBand) {
            return (int) (millibels * lowerFactor);
        } else {
            return (int) (millibels * upperFactor);
        }
    }

    private void setBassBoost() {
        if (mBassBoost == null) return;
        mBassBoost.setStrength(BASS_BOOST_STRENGTH[mCurrentMode]);
    }

    private void setLoudnessEnhancer() {
        if (mLoudnessEnhancer == null) return;
        mLoudnessEnhancer.setTargetGain(LOUDNESS_ENHANCER_SETTINGS[mCurrentMode][0]);
    }
    
    private void setReverb() {
        if (mPresetReverb == null) return;
        mPresetReverb.setPreset(REVERB_PRESETS[mCurrentMode]);
    }

    private void setDynamicsProcessing() {
        if (mDynamicsProcessing == null) return;
        try {
            if (mEq != null) {
                mEq.setEnabled(true);
                for (int i = 0; i < mEq.getBandCount(); i++) {
                    DynamicsProcessing.EqBand band = mEq.getBand(i);
                    band.setCutoffFrequency(bandVal[i]);
                    band.setGain(EQ_GAINS[mCurrentMode][i]);
                }
                mDynamicsProcessing.setPreEqAllChannelsTo(mEq);
                mDynamicsProcessing.setPostEqAllChannelsTo(mEq);
            }
            if (mMbc != null) {
                mMbc.setEnabled(true);
                for (int i = 0; i < mMbc.getBandCount(); i++) {
                    DynamicsProcessing.MbcBand band = mMbc.getBand(i);
                    band.setAttackTime(MBC_SETTINGS[mCurrentMode][0]);
                    band.setReleaseTime(MBC_SETTINGS[mCurrentMode][1]);
                    band.setRatio(MBC_SETTINGS[mCurrentMode][2]);
                    band.setThreshold(MBC_SETTINGS[mCurrentMode][3]);
                    band.setKneeWidth(MBC_SETTINGS[mCurrentMode][4]);
                }
                mDynamicsProcessing.setMbcAllChannelsTo(mMbc);
            }
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error setting DynamicsProcessing", e);
        }
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
