package com.example.autoclicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class TargetMarkerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean horizontalMode;

    public TargetMarkerView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setHorizontalMode(boolean horizontalMode) {
        this.horizontalMode = horizontalMode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.42f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(horizontalMode
                ? Color.argb(210, 35, 112, 215)
                : Color.argb(210, 220, 45, 45));
        paint.setShadowLayer(8f, 0f, 2f, Color.argb(100, 0, 0, 0));
        canvas.drawCircle(cx, cy, radius, paint);

        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, getWidth() * 0.055f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);

        if (horizontalMode) {
            float left = cx - radius * 0.72f;
            float right = cx + radius * 0.72f;
            float arrow = radius * 0.25f;
            canvas.drawLine(left, cy, right, cy, paint);
            Path arrows = new Path();
            arrows.moveTo(left + arrow, cy - arrow);
            arrows.lineTo(left, cy);
            arrows.lineTo(left + arrow, cy + arrow);
            arrows.moveTo(right - arrow, cy - arrow);
            arrows.lineTo(right, cy);
            arrows.lineTo(right - arrow, cy + arrow);
            canvas.drawPath(arrows, paint);
        } else {
            canvas.drawCircle(cx, cy, radius * 0.58f, paint);
            canvas.drawLine(cx - radius * 0.82f, cy, cx + radius * 0.82f, cy, paint);
            canvas.drawLine(cx, cy - radius * 0.82f, cx, cy + radius * 0.82f, paint);
        }
    }
}
