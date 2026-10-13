package com.mbpsmeter.app.ui.sketch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghar ka naksha: ungli se deewarein/kamre banao, phir Walk mode me jahan
 * khare ho wahan tap karo — signal reading colored dot ban kar save ho jati hai.
 * Tamam coordinates NORMALIZED (0-1) me rakhe jate hain taake view size se farq na pare.
 */
public class SketchView extends View {

    public static final int MODE_DRAW = 0;   // deewar (wall) kheecho
    public static final int MODE_ROOM = 1;   // kamra (rect) banao
    public static final int MODE_WALK = 2;   // dekho + tap kar ke reading lo
    public static final int MODE_ERASE = 3; // mitana

    public interface OnSketchTapListener {
        void onTap(float x, float y); // normalized 0-1
    }

    public interface OnRoomDrawnListener {
        void onRoomDrawn(float l, float t, float r, float b); // normalized rect
    }

    public interface OnSketchChangeListener {
        void onChanged(); // walls/rooms/readings badle (auto-save ke liye)
    }

    private static class Room {
        float l, t, r, b;
        String name;
        Room(float l, float t, float r, float b, String name) {
            this.l = l; this.t = t; this.r = r; this.b = b;
            this.name = name == null ? "" : name;
        }
    }

    private static class Reading {
        float x, y;
        int dbm;
        String type;
        Reading(float x, float y, int dbm, String type) {
            this.x = x; this.y = y; this.dbm = dbm;
            this.type = type == null ? "" : type;
        }
    }

    private final List<float[]> walls = new ArrayList<>();      // har wall: {x1,y1,x2,y2}
    private final List<Room> rooms = new ArrayList<>();
    private final List<Reading> readings = new ArrayList<>();

    private int mode = MODE_DRAW;
    private OnSketchTapListener tapListener;
    private OnRoomDrawnListener roomListener;
    private OnSketchChangeListener changeListener;

    // drag preview state
    private float downX, downY, curX, curY;
    private boolean dragging = false;

    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roomFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roomBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roomTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dbmTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density = 1f;

    public SketchView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public SketchView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx);
    }

    public SketchView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init(ctx);
    }

    private void init(Context ctx) {
        try {
            density = ctx.getResources().getDisplayMetrics().density;
        } catch (Exception ignored) { density = 1f; }

        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setStrokeWidth(8f * density);
        wallPaint.setStrokeCap(Paint.Cap.ROUND);
        wallPaint.setColor(Color.parseColor("#F1F5F9"));

        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setStrokeWidth(6f * density);
        previewPaint.setStrokeCap(Paint.Cap.ROUND);
        previewPaint.setColor(Color.parseColor("#00E5FF"));
        previewPaint.setAlpha(160);
        previewPaint.setPathEffect(new DashPathEffect(new float[]{12f * density, 8f * density}, 0));

        roomFillPaint.setStyle(Paint.Style.FILL);
        roomFillPaint.setColor(Color.parseColor("#00E5FF"));
        roomFillPaint.setAlpha(40);

        roomBorderPaint.setStyle(Paint.Style.STROKE);
        roomBorderPaint.setStrokeWidth(3f * density);
        roomBorderPaint.setColor(Color.parseColor("#00E5FF"));
        roomBorderPaint.setAlpha(200);

        roomTextPaint.setColor(Color.parseColor("#FFFFFF"));
        roomTextPaint.setTextSize(12f * density);
        roomTextPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(Color.parseColor("#1A2440"));

        dotPaint.setStyle(Paint.Style.FILL);
        dotRingPaint.setStyle(Paint.Style.STROKE);
        dotRingPaint.setStrokeWidth(2f * density);
        dotRingPaint.setColor(Color.parseColor("#FFFFFF"));

        dbmTextPaint.setColor(Color.parseColor("#FFFFFF"));
        dbmTextPaint.setTextSize(11f * density);
        dbmTextPaint.setTextAlign(Paint.Align.CENTER);
        dbmTextPaint.setShadowLayer(4f * density, 0, 0, Color.BLACK);
    }

    // ---------- public API ----------

    public void setMode(int mode) {
        this.mode = mode;
        dragging = false;
        invalidate();
    }

    public int getMode() { return mode; }

    public void setOnSketchTapListener(OnSketchTapListener l) { tapListener = l; }
    public void setOnRoomDrawnListener(OnRoomDrawnListener l) { roomListener = l; }
    public void setOnSketchChangeListener(OnSketchChangeListener l) { changeListener = l; }

    private void notifyChanged() {
        if (changeListener != null) {
            try { changeListener.onChanged(); } catch (Exception ignored) {}
        }
    }

    /** Akhri banai cheez wapas lo (pehle wall, phir room) */
    public void undo() {
        try {
            if (!walls.isEmpty()) walls.remove(walls.size() - 1);
            else if (!rooms.isEmpty()) rooms.remove(rooms.size() - 1);
            invalidate();
            notifyChanged();
        } catch (Exception ignored) {}
    }

    public void removeLastRoom() {
        try {
            if (!rooms.isEmpty()) rooms.remove(rooms.size() - 1);
            invalidate();
            notifyChanged();
        } catch (Exception ignored) {}
    }

    public void clearAll() {
        try {
            walls.clear();
            rooms.clear();
            readings.clear();
            invalidate();
            notifyChanged();
        } catch (Exception ignored) {}
    }

    /** Akhri be-naam kamre ko naam do */
    public void setRoomName(String name) {
        try {
            for (int i = rooms.size() - 1; i >= 0; i--) {
                Room r = rooms.get(i);
                if (r.name == null || r.name.isEmpty()) {
                    r.name = name == null ? "" : name;
                    break;
                }
            }
            invalidate();
            notifyChanged();
        } catch (Exception ignored) {}
    }

    public void addReading(float x, float y, int dbm, String type) {
        try {
            readings.add(new Reading(clamp01(x), clamp01(y), dbm, type));
            invalidate();
            notifyChanged();
        } catch (Exception ignored) {}
    }

    // ---------- JSON persistence ----------

    public String getWallsJson() {
        try {
            JSONArray a = new JSONArray();
            for (float[] w : walls) {
                JSONArray p = new JSONArray();
                for (float v : w) p.put(v);
                a.put(p);
            }
            return a.toString();
        } catch (Exception e) { return "[]"; }
    }

    public void setWallsJson(String json) {
        walls.clear();
        try {
            if (json == null || json.isEmpty()) return;
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.optJSONArray(i);
                if (p != null && p.length() >= 4) {
                    walls.add(new float[]{
                            clamp01((float) p.optDouble(0)),
                            clamp01((float) p.optDouble(1)),
                            clamp01((float) p.optDouble(2)),
                            clamp01((float) p.optDouble(3))});
                }
            }
        } catch (Exception ignored) {}
        invalidate();
    }

    public String getRoomsJson() {
        try {
            JSONArray a = new JSONArray();
            for (Room r : rooms) {
                JSONObject o = new JSONObject();
                o.put("l", r.l); o.put("t", r.t); o.put("r", r.r); o.put("b", r.b);
                o.put("n", r.name);
                a.put(o);
            }
            return a.toString();
        } catch (Exception e) { return "[]"; }
    }

    public void setRoomsJson(String json) {
        rooms.clear();
        try {
            if (json == null || json.isEmpty()) return;
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                rooms.add(new Room(
                        clamp01((float) o.optDouble("l")),
                        clamp01((float) o.optDouble("t")),
                        clamp01((float) o.optDouble("r")),
                        clamp01((float) o.optDouble("b")),
                        o.optString("n", "")));
            }
        } catch (Exception ignored) {}
        invalidate();
    }

    public String getReadingsJson() {
        try {
            JSONArray a = new JSONArray();
            for (Reading r : readings) {
                JSONObject o = new JSONObject();
                o.put("x", r.x); o.put("y", r.y); o.put("d", r.dbm); o.put("t", r.type);
                a.put(o);
            }
            return a.toString();
        } catch (Exception e) { return "[]"; }
    }

    public void setReadingsJson(String json) {
        readings.clear();
        try {
            if (json == null || json.isEmpty()) return;
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                readings.add(new Reading(
                        clamp01((float) o.optDouble("x")),
                        clamp01((float) o.optDouble("y")),
                        o.optInt("d", -120),
                        o.optString("t", "")));
            }
        } catch (Exception ignored) {}
        invalidate();
    }

    // ---------- touch ----------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return false;
        try {
            float x = ev.getX(), y = ev.getY();
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; downY = y; curX = x; curY = y;
                    dragging = (mode == MODE_DRAW || mode == MODE_ROOM);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        curX = x; curY = y;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    return handleUp(x, y, w, h);
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    invalidate();
                    return true;
            }
        } catch (Exception ignored) {}
        return super.onTouchEvent(ev);
    }

    private boolean handleUp(float x, float y, int w, int h) {
        boolean wasDragging = dragging;
        dragging = false;
        if (mode == MODE_DRAW && wasDragging) {
            // ek seedhi deewar per drag
            float[] wall = {clamp01(downX / w), clamp01(downY / h),
                    clamp01(x / w), clamp01(y / h)};
            // bohat chhoti line ko ignore karo
            float dx = (x - downX), dy = (y - downY);
            if (dx * dx + dy * dy > (10 * density) * (10 * density)) {
                walls.add(wall);
                notifyChanged();
            }
            invalidate();
            return true;
        }
        if (mode == MODE_ROOM && wasDragging) {
            float l = clamp01(Math.min(downX, x) / w);
            float t = clamp01(Math.min(downY, y) / h);
            float r = clamp01(Math.max(downX, x) / w);
            float b = clamp01(Math.max(downY, y) / h);
            if ((r - l) * w > 20 * density && (b - t) * h > 20 * density) {
                rooms.add(new Room(l, t, r, b, ""));
                notifyChanged();
                if (roomListener != null) {
                    try { roomListener.onRoomDrawn(l, t, r, b); } catch (Exception ignored) {}
                }
            }
            invalidate();
            return true;
        }
        if (mode == MODE_ERASE) {
            eraseAt(x, y, w, h);
            return true;
        }
        if (mode == MODE_WALK) {
            float dx = (x - downX), dy = (y - downY);
            if (dx * dx + dy * dy < (12 * density) * (12 * density)) {
                // tap
                if (tapListener != null) {
                    try { tapListener.onTap(clamp01(x / w), clamp01(y / h)); }
                    catch (Exception ignored) {}
                }
            }
            return true;
        }
        return true;
    }

    private void eraseAt(float x, float y, int w, int h) {
        try {
            // pehle wall: midpoint 30px ke andar?
            float thresh = 30f * density;
            for (int i = walls.size() - 1; i >= 0; i--) {
                float[] wl = walls.get(i);
                float mx = wl[0] * w + wl[2] * w;
                mx /= 2f;
                float my = (wl[1] * h + wl[3] * h) / 2f;
                float dx = x - mx, dy = y - my;
                if (dx * dx + dy * dy <= thresh * thresh) {
                    walls.remove(i);
                    invalidate();
                    notifyChanged();
                    return;
                }
            }
            // phir room: point rect ke andar?
            float nx = x / w, ny = y / h;
            for (int i = rooms.size() - 1; i >= 0; i--) {
                Room r = rooms.get(i);
                if (nx >= r.l && nx <= r.r && ny >= r.t && ny <= r.b) {
                    rooms.remove(i);
                    invalidate();
                    notifyChanged();
                    return;
                }
            }
            // reading: dot ke qareeb tap?
            float dotR = 10f * density;
            for (int i = readings.size() - 1; i >= 0; i--) {
                Reading rd = readings.get(i);
                float dx = x - rd.x * w, dy = y - rd.y * h;
                if (dx * dx + dy * dy <= (dotR + 10f * density) * (dotR + 10f * density)) {
                    readings.remove(i);
                    invalidate();
                    notifyChanged();
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    // ---------- draw ----------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        try {
            // background
            c.drawColor(Color.parseColor("#0A1433"));
            // halka grid
            float step = Math.min(w, h) / 10f;
            if (step > 0) {
                for (float gx = step; gx < w; gx += step) c.drawLine(gx, 0, gx, h, gridPaint);
                for (float gy = step; gy < h; gy += step) c.drawLine(0, gy, w, gy, gridPaint);
            }
            // kamre
            for (Room r : rooms) {
                float l = r.l * w, t = r.t * h, rr = r.r * w, b = r.b * h;
                c.drawRect(l, t, rr, b, roomFillPaint);
                c.drawRect(l, t, rr, b, roomBorderPaint);
                if (r.name != null && !r.name.isEmpty()) {
                    c.drawText(r.name, (l + rr) / 2f, t + 16f * density, roomTextPaint);
                }
            }
            // deewarein
            for (float[] wl : walls) {
                c.drawLine(wl[0] * w, wl[1] * h, wl[2] * w, wl[3] * h, wallPaint);
            }
            // drag preview
            if (dragging) {
                if (mode == MODE_DRAW) {
                    c.drawLine(downX, downY, curX, curY, previewPaint);
                } else if (mode == MODE_ROOM) {
                    float l = Math.min(downX, curX), t = Math.min(downY, curY);
                    float rr = Math.max(downX, curX), b = Math.max(downY, curY);
                    c.drawRect(l, t, rr, b, previewPaint);
                }
            }
            // readings: colored dots
            float dotR = 9f * density;
            for (Reading rd : readings) {
                float cx = rd.x * w, cy = rd.y * h;
                dotPaint.setColor(dotColor(rd.dbm));
                c.drawCircle(cx, cy, dotR, dotPaint);
                c.drawCircle(cx, cy, dotR, dotRingPaint);
                c.drawText(rd.dbm + "", cx, cy + dotR + 14f * density, dbmTextPaint);
            }
        } catch (Exception ignored) {}
    }

    private int dotColor(int dbm) {
        if (dbm >= -70) return Color.parseColor("#22C55E"); // strong
        if (dbm >= -85) return Color.parseColor("#F59E0B"); // fair
        return Color.parseColor("#EF4444"); // weak
    }

    private float clamp01(float v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
