package com.android.systemui.nad.transformer;

import android.view.View;

public class ForegroundToBackgroundTransformer extends ABaseTransformer {
    protected void onTransform(View view, float position) {
        if (Math.abs(position) < 0.0001) {
            view.setScaleX(1.0f);
            view.setScaleY(1.0f);
            view.setPivotX(view.getWidth() * 0.5f);
            view.setPivotY(view.getHeight() * 0.5f);
            view.setTranslationX(0.0f);
            return;
        }

        float height = (float) view.getHeight();
        float width = (float) view.getWidth();
        float f = 1.0f - Math.abs(position);
        float scale = cubicEaseOut(f);
        scale = Math.max(scale, 0.5f);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setPivotX(width * 0.5f);
        view.setPivotY(height * 0.5f);
        view.setTranslationX(position > 0.0f ? width * position : (-width) * position * 0.25f);
    }
    
    private float cubicEaseOut(float t) {
        float f = t - 1.0f;
        return f * f * f + 1.0f;
    }
}

