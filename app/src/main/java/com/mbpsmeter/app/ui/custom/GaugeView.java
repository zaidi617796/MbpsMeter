package com.mbpsmeter.app.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Needle wala circular gauge (screenshot style):
 * -110 se -50 dBm, ticks + numbers, beech me needle aur value.
 */
public class GaugeView extends View {

    private int value = -85;
    private final int min = -110;
    private final int max = -50;

    private Paint arcBg, arcProgress, tickPaint, labelPaint, textPaint, unitPaint, needlePaint, hubPaint;

    public GaugeView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        arcBg = paint(Color.parseColor("#2A3A6B"), 22f, Paint.Style.STROKE, Paint.Cap.ROUND);
        arcProgress = paint(Color.parseColor("#22C55E"), 22f, Paint.Style.STROKE, Paint.Cap.ROUND);
        tickPaint = paint(Color.parseColor("#5A6B8C"), 3f, Paint.Style.STROKE, Paint.Cap.BUTT);
        labelPaint = paint(Color.parseColor("#8A9ABF"), 0f, Paint.Style.FILL, Paint.Cap.BUTT);
        labelPaint.setTextSize(26f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        textPaint = paint(Color.WHITE, 0f, Paint.Style.FILL, Paint.Cap.BUTT);
        textPaint.setTextSize(84f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        unitPaint = paint(Color.parseColor("#8A9ABF"), 0f, Paint.Style.FILL, Paint.Cap.BUTT);
        unitPaint.setTextSize(28f);
        unitPaint.setTextAlign(Paint.Align.CENTER);
        needlePaint = paint(Color.parseColor("#EF4444"), 6f, Paint.Style.STROKE, Paint.Cap.ROUND);
        hubPaint = paint(Color.parseColor("#E2E8F0"), 0f, Paint.Style.FILL, Paint.Cap.BUTT);
    }

    private Paint paint(int color, float width, Paint.Style style, Paint.Cap cap) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(style);
        p.setStrokeWidth(width);
        p.setStrokeCap(cap);
        return p;
    }

    /** Wahi purana API — Live fragment baghair change ke chalega */
    public void setValueWithColor(int v) {
        value = Math.max(min, Math.min(max, v));
        if (v >= -80) arcProgress.setColor(Color.parseColor("#22C55E"));
        else if (v >= -95) arcProgress.setColor(Color.parseColor("#F59E0B"));
        else arcProgress.setColor(Color.parseColor("#EF4444"));
        invalidate();
    }

    private float angleFor(int dbm) {
        float frac = (float) (dbm - min) / (max - min);
        return 135f + 270f * frac;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) / 2f - 44f;
        RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        // background + progress arc
        canvas.drawArc(rect, 135f, 270f, false, arcBg);
        float frac = (float) (value - min) / (max - min);
        canvas.drawArc(rect, 135f, 270f * frac, false, arcProgress);

        // ticks + labels
        for (int dbm = min; dbm <= max; dbm += 10) {
            float a = (float) Math.toRadians(angleFor(dbm));
            float x1 = cx + (float) Math.cos(a) * (radius - 26f);
            float y1 = cy + (float) Math.sin(a) * (radius - 26f);
            float x2 = cx + (float) Math.cos(a) * (radius - 14f);
            float y2 = cy + (float) Math.sin(a) * (radius - 14f);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
            if ((dbm - min) % 20 == 0) {
                float lx = cx + (float) Math.cos(a) * (radius - 52f);
                float ly = cy + (float) Math.sin(a) * (radius - 52f) + 9f;
                canvas.drawText(String.valueOf(dbm), lx, ly, labelPaint);
            }
        }

        // needle
        float na = (float) Math.toRadians(angleFor(value));
        float nx = cx + (float) Math.cos(na) * (radius - 60f);
        float ny = cy + (float) Math.sin(na) * (radius - 60f);
        canvas.drawLine(cx, cy, nx, ny, needlePaint);
        canvas.drawCircle(cx, cy, 12f, hubPaint);

        // center value — needle hub ke neeche, taake number sui se na takraye
        canvas.drawText(String.valueOf(value), cx, cy + radius * 0.40f, textPaint);
        canvas.drawText("dBm", cx, cy + radius * 0.54f, unitPaint);
    }
}