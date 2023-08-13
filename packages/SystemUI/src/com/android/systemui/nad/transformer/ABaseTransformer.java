package com.android.systemui.nad.transformer;

import androidx.viewpager.widget.ViewPager;
import android.view.View;

public abstract class ABaseTransformer implements ViewPager.PageTransformer {
    protected abstract void onTransform(View view, float f);

    public void transformPage(View page, float position) {
        if (Math.abs(position) < 0.0001) {
            resetTransformations(page);
            return;
        }

        onPreTransform(page, position);
        onTransform(page, position);
        onPostTransform(page, position);
    }

    private void resetTransformations(View page) {
        page.setRotationX(0.0f);
        page.setRotationY(0.0f);
        page.setRotation(0.0f);
        page.setScaleX(1.0f);
        page.setScaleY(1.0f);
        page.setPivotX(0.0f);
        page.setPivotY(0.0f);
        page.setTranslationY(0.0f);
        page.setTranslationX(0.0f);
        page.setAlpha(1.0f);
    }

    protected boolean hideOffscreenPages() {
        return true;
    }

    protected boolean isPagingEnabled() {
        return false;
    }

    protected void onPreTransform(View page, float position) {
        float width = (float) page.getWidth();
        page.setTranslationX(isPagingEnabled() ? 0.0f : (-width) * position);
        if (hideOffscreenPages()) {
            page.setAlpha(position > -1.0f && position < 1.0f ? 1.0f : 0.0f);
        } else {
            page.setAlpha(1.0f);
        }
    }

    protected void onPostTransform(View page, float position) {
    }

    protected static final float min(float val, float min) {
        return val < min ? min : val;
    }
}
