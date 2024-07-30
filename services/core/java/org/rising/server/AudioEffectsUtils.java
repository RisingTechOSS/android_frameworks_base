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
    private static final short[] BASS_BOOST_STRENGTH = {0, 900, 700, 600, 700};
    private static final short[] SPATIAL_STRENGTH = {0, 1100, 1150, 1200, 1100};
    private static final short[] REVERB_PRESETS = {
            PresetReverb.PRESET_NONE,
            PresetReverb.PRESET_NONE,
            PresetReverb.PRESET_LARGEHALL,
            PresetReverb.PRESET_PLATE,
            PresetReverb.PRESET_MEDIUMROOM
    };
    private static final int mVariant = 0;
    private static final int mChannelCount = 1;
    private static final int[] bandVal = {31, 62, 125, 250,  500, 1000, 2000, 4000, 8000, 16000};
    private static final int maxBandCount = bandVal.length;

    private static final int[][] LOUDNESS_ENHANCER_SETTINGS = {
        {0},    // Disabled
        {1100}, // Music
        {1200}, // Games
        {1500}, // Theater
        {1100}  // Smart
    };

    private static final int[][] ENVIRONMENTAL_REVERB_SETTINGS = {
        {0, -1000},   // Disabled
        {1400, 0}, // Music
        {1700, -100}, // Games
        {2200, 0},    // Theater
        {1400, -200}  // Smart
    };

    private static final float[][] EQ_GAINS = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},      // Disabled
        {3, 2, 1, 0, -1, 0, 1, 2, 3, 4},     // Music
        {4, 3, 2, 1, 0, 1, 2, 3, 4, 5},      // Games
        {5, 4, 3, 2, 1, 2, 3, 4, 5, 6},      // Theater
        {2, 1, 0, -1, 0, 1, 2, 3, 2, 1}      // Smart
    };

    private static final float[][] MBC_SETTINGS = {
        {50, 50, 1.0f, -30, 5},  // Disabled
        {30, 70, 3.0f, -20, 6},  // Music
        {20, 50, 3.5f, -15, 4},  // Games
        {40, 90, 4.0f, -10, 8},  // Theater
        {35, 80, 2.5f, -25, 5}   // Smart
    };

    private final AudioManager mAudioManager;
    private int mCurrentMode = 0;

    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private PresetReverb mPresetReverb;
    private Virtualizer mVirtualizer;
    private LoudnessEnhancer mLoudnessEnhancer;
    private EnvironmentalReverb mEnvironmentalReverb;
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
            mVirtualizer = new Virtualizer(EFFECT_PRIORITY, 0);
            mLoudnessEnhancer = new LoudnessEnhancer(0);
            mEnvironmentalReverb = new EnvironmentalReverb(EFFECT_PRIORITY, 0);
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(mVariant, mChannelCount, true, maxBandCount, true, maxBandCount, true, maxBandCount, true);
            mDynamicsProcessing = new DynamicsProcessing(EFFECT_PRIORITY, 0, builder.build());
            mEq = new DynamicsProcessing.Eq(true, true, maxBandCount);
            mDynamicsProcessing.setEnabled(true);

            mEqualizer.setEnabled(true);
            mBassBoost.setEnabled(true);
            mPresetReverb.setEnabled(true);
            mVirtualizer.setEnabled(true);
            mLoudnessEnhancer.setEnabled(true);
            mEnvironmentalReverb.setEnabled(true);
            mDynamicsProcessing.setEnabled(true);
        } catch (Exception e) {
            Log.e("AudioEffectsUtils", "Error initializing audio effects", e);
        }
    }

    public void releaseEffects() {
        releaseEffect(mEqualizer);
        releaseEffect(mBassBoost);
        releaseEffect(mPresetReverb);
        releaseEffect(mVirtualizer);
        releaseEffect(mLoudnessEnhancer);
        releaseEffect(mEnvironmentalReverb);
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
        setReverb();
        setSpatialAudio();
        setLoudnessEnhancer();
        setEnvironmentalReverb();
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
        int targetLevel = 0;

        switch (mCurrentMode) {
            case 1: // Music
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.4, -1.3, -1.5);
                break;
            case 2: // Game
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.5, -1.4, -1.6);
                break;
            case 3: // Theater
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.6, -1.5, -1.7);
                break;
            case 4: // Smart
                targetLevel = calculateBandLevel(millibels, band, centerBand, -1.4, -1.3, -1.4);
                break;
            default:
                targetLevel = (int) (millibels * 0.95);
                break;
        }

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

    private void setReverb() {
        if (mPresetReverb == null) return;
        mPresetReverb.setPreset(REVERB_PRESETS[mCurrentMode]);
    }

    private void setSpatialAudio() {
        if (mVirtualizer == null) return;
        mVirtualizer.setStrength(SPATIAL_STRENGTH[mCurrentMode]);
        mVirtualizer.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO);
    }

    private void setLoudnessEnhancer() {
        if (mLoudnessEnhancer == null) return;
        mLoudnessEnhancer.setTargetGain(LOUDNESS_ENHANCER_SETTINGS[mCurrentMode][0]);
    }

    private void setEnvironmentalReverb() {
        if (mEnvironmentalReverb == null) return;
        mEnvironmentalReverb.setDecayTime(ENVIRONMENTAL_REVERB_SETTINGS[mCurrentMode][0]);
        mEnvironmentalReverb.setRoomLevel((short) ENVIRONMENTAL_REVERB_SETTINGS[mCurrentMode][1]);
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
        if (effect != null) {
            effect.setEnabled(false);
            effect.release();
        }
    }
}
