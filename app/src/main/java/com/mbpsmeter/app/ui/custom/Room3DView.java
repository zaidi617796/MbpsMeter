package com.mbpsmeter.app.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Room3DView: kamre ka isometric 3D-style view.
 * Farsh 4 corner zones + center me bata hua hai; har zone us corner ke
 * average signal ke rang me (hara/peela/surkh, grey = no data).
 * Zone par tap = us corner me reading record.
 *
 * corner ids: 0=center, 1=top-left, 2=top-right, 3=bottom-left, 4=bottom-right
 */
public class Room3DView extends View {

    public interface OnCornerPick {
        void onPick(int corner);
    }

    private final int[] avg = new int[]{-999, -999, -999, -999, -999};
    private OnCornerPick cb;
    private int pressed = -1;

    // kamre ke naap (units)
    private static final float W = 10f, D = 10f, H = 6f;
    private static final double COSA = 0.8660254; // cos(30)
    private static final double SINA = 0.5;       // sin(30)

    private float cx, cy, sc;
    private final float[][] quadPts = new float[4][8]; // 4 points per quad (screen)
    private float ccx, ccy, ccr; // center circle

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Room3DView(Context c) {
        super(c);
        init();
    }

    public Room3DView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        setClickable(true);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setColor(0xBFFFFFFF);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setData(int[] avgs, OnCornerPick cb) {
        if (avgs != null) {
            for (int i = 0; i < 5 && i < avgs.length; i++) avg[i] = avgs[i];
        }
        this.cb = cb;
        invalidate();
    }

    private int colorFor(int dbm) {
        if (dbm <= -999) return 0xFF2A3A5B;
        if (dbm >= -80) return 0xFF22C55E;
        if (dbm >= -95) return 0xFFF59E0B;
        return 0xFFEF4444;
    }

    private float[] proj(float x, float y, float z) {
        return new float[]{
                (float) (cx + (x - y) * COSA * sc),
                (float) (cy + (x + y) * SINA * sc - z * sc)
        };
    }

    @Override
    protected void onMeasure(int wms, int hms) {
        float d = getResources().getDisplayMetrics().density;
        int w = resolveSize((int) (340 * d), wms);
        int h = resolveSize((int) (270 * d), hms);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float needW = (float) ((W + D) * COSA);
        float needH = (float) ((W + D) * SINA + H);
        sc = Math.min(w * 0.94f / needW, h * 0.94f / needH);
        cx = w / 2f - (float) ((W - D) * COSA * sc / 2.0);
        cy = h / 2f - (float) (((W + D) * SINA - H) * sc / 2.0);
        textPaint.setTextSize(Math.max(11 * getResources().getDisplayMetrics().density, sc * 0.55f));

        // 4 floor quadrants (plan coords)
        float[][] q = {
                {0, 0, W / 2, 0, W / 2, D / 2, 0, D / 2},          // 1 top-left
                {W / 2, 0, W, 0, W, D / 2, W / 2, D / 2},          // 2 top-right
                {0, D / 2, W / 2, D / 2, W / 2, D, 0, D},          // 3 bottom-left
                {W / 2, D / 2, W, D / 2, W, D, W / 2, D},         // 4 bottom-right
        };
        for (int i = 0; i < 4; i++) {
            for (int p = 0; p < 4; p++) {
                float[] s = proj(q[i][p * 2], q[i][p * 2 + 1], 0);
                quadPts[i][p * 2] = s[0];
                quadPts[i][p * 2 + 1] = s[1];
            }
        }
        float[] c = proj(W / 2, D / 2, 0);
        ccx = c[0];
        ccy = c[1];
        ccr = sc * 0.55f;
    }

    private Path pathOf(float[] pts) {
        Path p = new Path();
        p.moveTo(pts[0], pts[1]);
        for (int i = 1; i < 4; i++) p.lineTo(pts[i * 2], pts[i * 2 + 1]);
        p.close();
        return p;
    }

    private Path wallPath(float x1, float y1, float x2, float y2) {
        float[] a = proj(x1, y1, 0), b = proj(x2, y2, 0);
        float[] c = proj(x2, y2, H), d = proj(x1, y1, H);
        Path p = new Path();
        p.moveTo(a[0], a[1]);
        p.lineTo(b[0], b[1]);
        p.lineTo(c[0], c[1]);
        p.lineTo(d[0], d[1]);
        p.close();
        return p;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // pichli deewarein
        wallPaint.setColor(0xFF1B2A4D);
        canvas.drawPath(wallPath(0, 0, W, 0), wallPaint);
        wallPaint.setColor(0xFF141F3A);
        canvas.drawPath(wallPath(0, 0, 0, D), wallPaint);

        // farsh ke 4 zones
        for (int i = 0; i < 4; i++) {
            fillPaint.setColor(colorFor(avg[i + 1]));
            fillPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(pathOf(quadPts[i]), fillPaint);
            canvas.drawPath(pathOf(quadPts[i]), linePaint);
            // dBm label zone ke markaz me
            float mx = (quadPts[i][0] + quadPts[i][4]) / 2f;
            float my = (quadPts[i][1] + quadPts[i][5]) / 2f;
            String t = avg[i + 1] <= -999 ? "–" : String.valueOf(avg[i + 1]);
            canvas.drawText(t, mx, my + textPaint.getTextSize() * 0.35f, textPaint);
            if (pressed == i + 1) {
                linePaint.setColor(0xFF00E5FF);
                linePaint.setStrokeWidth(6f);
                canvas.drawPath(pathOf(quadPts[i]), linePaint);
                linePaint.setColor(0xBFFFFFFF);
                linePaint.setStrokeWidth(3f);
            }
        }

        // center circle
        fillPaint.setColor(colorFor(avg[0]));
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(ccx, ccy, ccr, fillPaint);
        canvas.drawCircle(ccx, ccy, ccr, linePaint);
        String tc = avg[0] <= -999 ? "–" : String.valueOf(avg[0]);
        canvas.drawText(tc, ccx, ccy + textPaint.getTextSize() * 0.35f, textPaint);
        if (pressed == 0) {
            linePaint.setColor(0xFF00E5FF);
            linePaint.setStrokeWidth(6f);
            canvas.drawCircle(ccx, ccy, ccr, linePaint);
            linePaint.setColor(0xBFFFFFFF);
            linePaint.setStrokeWidth(3f);
        }
    }

    private boolean inPoly(float[] pts, float x, float y) {
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            float xi = pts[i * 2], yi = pts[i * 2 + 1];
            float xj = pts[j * 2], yj = pts[j * 2 + 1];
            if ((yi > y) != (yj > y)
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private int zoneAt(float x, float y) {
        float dx = x - ccx, dy = y - ccy;
        if (dx * dx + dy * dy <= ccr * ccr * 1.7f) return 0;
        for (int i = 0; i < 4; i++) {
            if (inPoly(quadPts[i], x, y)) return i + 1;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int z = zoneAt(e.getX(), e.getY());
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressed = z;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (z >= 0 && z == pressed && cb != null) cb.onPick(z);
                pressed = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}
