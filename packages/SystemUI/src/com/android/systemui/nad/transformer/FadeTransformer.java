package com.android.systemui.nad.transformer;

import android.view.View;

public class FadeTransformer extends ABaseTransformer {
    public void onTransform(View view, float position) {
        if (position == 0) {
            view.setPivotX(0);
            view.setPivotY(view.getHeight() * 0.5f);
            view.setRotationY(0);
            view.setScaleX(1.0f);
            view.setScaleY(1.0f);
            return;
        }

        float f = 0.0f;
        if (position < 0.0f) {
            f = (float) view.getWidth();
        }
        view.setPivotX(f);
        view.setPivotY(((float) view.getHeight()) * 0.5f);
        view.setRotationY(20.0f * position);
        float normalizedPosition = Math.abs(Math.abs(position) - 1.0f);
        view.setScaleX((float) (((double) (normalizedPosition / 2.0f)) + 0.5d));
        view.setScaleY((float) (((double) (normalizedPosition / 2.0f)) + 0.5d));
    }

    public boolean isPagingEnabled() {
        return true;
    }
}

