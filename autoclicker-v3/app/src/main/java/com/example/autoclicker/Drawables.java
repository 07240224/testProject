package com.example.autoclicker;

import android.graphics.drawable.GradientDrawable;

final class Drawables {
    private Drawables() {}

    static GradientDrawable roundRect(int fillColor, float radiusPx, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusPx);
        if (strokeColor != 0) {
            drawable.setStroke(1, strokeColor);
        }
        return drawable;
    }
}
