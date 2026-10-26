package com.mbpsmeter.app.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.LocationHelper;
import com.mbpsmeter.app.util.SupabaseSync;
import com.mbpsmeter.app.util.TowerDb;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MAP V6: colored pins + best spot distance/direction + Open in Maps.
 */
public class MapV4Fragment extends Fragment {

    private TextView tvBestSignal, tvBestLocation, tvBestDistance;
    private SharedPreferences prefs;
    private MapView osmMap;
    private MyLocationNewOverlay myLocOverlay;
    private boolean focusMode = false;

    // ---- Ilaqa Map ----
    private static final int MODE_MINE = 0, MODE_ILAQA = 1, MODE_TOWERS = 2;
    /** Grid cell size (degrees) — SQL view "ilaqa_grid" se identical formula. */
    private static final double CELL_DEG = 0.0015;
    private Button btnModeMine, btnModeIlaqa, btnModeTowers;
    private Button chipOpAll, chipOpJazz, chipOpZong, chipOpTelenor, chipOpUfone;
    private View scrollIlaqaOps, layoutIlaqaInfo;
    private TextView tvIlaqaSharing, tvIlaqaStatus, tvIlaqaInfo;
    private int mapMode = MODE_MINE;
    private String ilaqaOpKey = ""; // "" = All operators
    private JSONArray ilaqaData = null;
    private boolean ilaqaDirty = true;
    private boolean ilaqaCentered = false;
    private final Map<String, List<JSONObject>> ilaqaCells = new HashMap<>();
    private final Handler ilaqaHandler = new Handler(Looper.getMainLooper());
    private final Runnable ilaqaFetchRunnable = this::fetchIlaqaGrid;

    private float bestLat = 0, bestLng = 0;
    private int bestDbm = -120;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map_v4, container, false);
        tvBestSignal = v.findViewById(R.id.tvBestSignal);
        tvBestLocation = v.findViewById(R.id.tvBestLocation);
        tvBestDistance = v.findViewById(R.id.tvBestDistance);
        v.findViewById(R.id.btnNavigateBest).setOnClickListener(x -> centerBest());
        osmMap = v.findViewById(R.id.osmMap);
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        // ---- Mode selector: Meri readings / Ilaqa Map / Towers ----
        btnModeMine = v.findViewById(R.id.btnModeMine);
        btnModeIlaqa = v.findViewById(R.id.btnModeIlaqa);
        btnModeTowers = v.findViewById(R.id.btnModeTowers);
        scrollIlaqaOps = v.findViewById(R.id.scrollIlaqaOps);
        tvIlaqaSharing = v.findViewById(R.id.tvIlaqaSharing);
        tvIlaqaStatus = v.findViewById(R.id.tvIlaqaStatus);
        layoutIlaqaInfo = v.findViewById(R.id.layoutIlaqaInfo);
        tvIlaqaInfo = v.findViewById(R.id.tvIlaqaInfo);
        chipOpAll = v.findViewById(R.id.chipOpAll);
        chipOpJazz = v.findViewById(R.id.chipOpJazz);
        chipOpZong = v.findViewById(R.id.chipOpZong);
        chipOpTelenor = v.findViewById(R.id.chipOpTelenor);
        chipOpUfone = v.findViewById(R.id.chipOpUfone);
        // Purani setting se mode lo (show_towers wale users Towers mode me khulenge)
        mapMode = prefs.getInt("map_mode",
                prefs.getBoolean("show_towers", false) ? MODE_TOWERS : MODE_MINE);
        // Opt-in khatam ho chuka ho to Ilaqa mode me mat kholo
        if (mapMode == MODE_ILAQA && !SupabaseSync.ilaqaOptIn(prefs)) mapMode = MODE_MINE;
        ilaqaOpKey = prefs.getString("ilaqa_op_filter", "");
        btnModeMine.setOnClickListener(x -> selectMode(MODE_MINE));
        btnModeIlaqa.setOnClickListener(x -> selectMode(MODE_ILAQA));
        btnModeTowers.setOnClickListener(x -> selectMode(MODE_TOWERS));
        chipOpAll.setOnClickListener(x -> selectIlaqaOp(""));
        chipOpJazz.setOnClickListener(x -> selectIlaqaOp("jazz"));
        chipOpZong.setOnClickListener(x -> selectIlaqaOp("zong"));
        chipOpTelenor.setOnClickListener(x -> selectIlaqaOp("telenor"));
        chipOpUfone.setOnClickListener(x -> selectIlaqaOp("ufone"));
        tvIlaqaSharing.setText(R.string.ilaqa_sharing_on);
        tvIlaqaSharing.setOnClickListener(x -> showIlaqaOffDialog());
        v.findViewById(R.id.btnIlaqaClose).setOnClickListener(
                x -> layoutIlaqaInfo.setVisibility(View.GONE));
        updateModeButtons();
        // Map move/zoom par (Ilaqa mode me) grid dobara lao — debounce ke saath
        osmMap.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { scheduleIlaqaFetch(); return false; }
            @Override public boolean onZoom(ZoomEvent event) { scheduleIlaqaFetch(); return false; }
        });
        if (mapMode == MODE_ILAQA) scheduleIlaqaFetch();
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.setBuiltInZoomControls(false);
        // Blue dot: meri exact location dikhao
        try {
            myLocOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), osmMap);
            myLocOverlay.enableMyLocation();
            myLocOverlay.enableFollowLocation();
            osmMap.getOverlays().add(myLocOverlay);
        } catch (Exception ignored) {}
        loadAll();
        return v;
    }

    // ================= ILAQA MAP =================

    private void styleModeBtn(Button b, boolean selected) {
        if (b == null) return;
        b.setBackgroundResource(selected ? R.drawable.bg_gradient_btn : R.drawable.bg_pill_dark);
        b.setTextColor(selected ? Color.WHITE : Color.parseColor("#E2E8F0"));
    }

    private void updateModeButtons() {
        styleModeBtn(btnModeMine, mapMode == MODE_MINE);
        styleModeBtn(btnModeIlaqa, mapMode == MODE_ILAQA);
        styleModeBtn(btnModeTowers, mapMode == MODE_TOWERS);
        boolean ilaqa = mapMode == MODE_ILAQA;
        if (scrollIlaqaOps != null) scrollIlaqaOps.setVisibility(ilaqa ? View.VISIBLE : View.GONE);
        if (tvIlaqaSharing != null) tvIlaqaSharing.setVisibility(ilaqa ? View.VISIBLE : View.GONE);
        if (!ilaqa) {
            if (tvIlaqaStatus != null) tvIlaqaStatus.setVisibility(View.GONE);
            if (layoutIlaqaInfo != null) layoutIlaqaInfo.setVisibility(View.GONE);
        }
        styleModeBtn(chipOpAll, ilaqaOpKey.isEmpty());
        styleModeBtn(chipOpJazz, ilaqaOpKey.equals("jazz"));
        styleModeBtn(chipOpZong, ilaqaOpKey.equals("zong"));
        styleModeBtn(chipOpTelenor, ilaqaOpKey.equals("telenor"));
        styleModeBtn(chipOpUfone, ilaqaOpKey.equals("ufone"));
    }

    private void selectMode(int mode) {
        // Ilaqa mode pehli dafa: pehle saaf consent dialog
        if (mode == MODE_ILAQA && !SupabaseSync.ilaqaOptIn(prefs)) {
            showIlaqaConsent();
            return;
        }
        if (mode == MODE_TOWERS && !prefs.getBoolean("tower_note_seen", false)) {
            prefs.edit().putBoolean("tower_note_seen", true).apply();
            Toast.makeText(requireContext(), R.string.tower_approx, Toast.LENGTH_LONG).show();
        }
        mapMode = mode;
        prefs.edit().putInt("map_mode", mode).apply();
        if (mode != MODE_ILAQA) ilaqaCentered = false;
        updateModeButtons();
        loadAll();
        if (mode == MODE_ILAQA) scheduleIlaqaFetch();
    }

    private void showIlaqaConsent() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ilaqa_consent_title)
                .setMessage(R.string.ilaqa_consent_msg)
                .setPositiveButton(R.string.ilaqa_consent_agree, (d, w) -> {
                    prefs.edit().putBoolean("ilaqa_optin", true).apply();
                    selectMode(MODE_ILAQA);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showIlaqaOffDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ilaqa_off_title)
                .setMessage(R.string.ilaqa_off_msg)
                .setPositiveButton(R.string.ilaqa_off_confirm, (d, w) -> {
                    prefs.edit().putBoolean("ilaqa_optin", false).apply();
                    selectMode(MODE_MINE);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void selectIlaqaOp(String key) {
        ilaqaOpKey = key;
        prefs.edit().putString("ilaqa_op_filter", key).apply();
        updateModeButtons();
        if (layoutIlaqaInfo != null) layoutIlaqaInfo.setVisibility(View.GONE);
        ilaqaDirty = true;
        drawIlaqaGrid();
    }

    private void scheduleIlaqaFetch() {
        if (mapMode != MODE_ILAQA) return;
        ilaqaHandler.removeCallbacks(ilaqaFetchRunnable);
        ilaqaHandler.postDelayed(ilaqaFetchRunnable, 700);
    }

    private void setIlaqaStatus(int resId) {
        if (tvIlaqaStatus == null) return;
        if (resId == 0) {
            tvIlaqaStatus.setVisibility(View.GONE);
        } else {
            tvIlaqaStatus.setText(resId);
            tvIlaqaStatus.setVisibility(View.VISIBLE);
        }
    }

    /** Viewport se cell range (SQL view wala formula: cell_x = floor(lng/0.0015), cell_y = floor(lat/0.0015)) */
    private long[] ilaqaCellRange() {
        try {
            BoundingBox bb = osmMap.getBoundingBox();
            long minX = (long) Math.floor(bb.getLonWest() / CELL_DEG);
            long maxX = (long) Math.floor(bb.getLonEast() / CELL_DEG);
            long minY = (long) Math.floor(bb.getLatSouth() / CELL_DEG);
            long maxY = (long) Math.floor(bb.getLatNorth() / CELL_DEG);
            // Bohat zoom-out par query mehdood rakho (center ke aas paas)
            if (maxX - minX > 400 || maxY - minY > 400) {
                long cx = (long) Math.floor(bb.getCenterLongitude() / CELL_DEG);
                long cy = (long) Math.floor(bb.getCenterLatitude() / CELL_DEG);
                minX = cx - 200; maxX = cx + 200; minY = cy - 200; maxY = cy + 200;
            }
            return new long[]{minX, maxX, minY, maxY};
        } catch (Exception e) {
            return null;
        }
    }

    private void fetchIlaqaGrid() {
        if (mapMode != MODE_ILAQA || getContext() == null || osmMap == null) return;
        long[] range = ilaqaCellRange();
        if (range == null) {
            // Map abhi layout nahi hua — thodi dair baad dobara
            ilaqaHandler.postDelayed(ilaqaFetchRunnable, 600);
            return;
        }
        if (ilaqaData == null) setIlaqaStatus(R.string.ilaqa_loading);
        SupabaseSync.fetchIlaqaGrid(requireContext(), range[0], range[1], range[2], range[3], rows -> {
            if (getActivity() == null || mapMode != MODE_ILAQA) return;
            if (rows != null) {
                ilaqaData = rows;
                SupabaseSync.cacheIlaqaGrid(prefs, rows.toString());
                drawIlaqaGrid();
                setIlaqaStatus(rows.length() == 0 ? R.string.ilaqa_empty : 0);
            } else if (ilaqaData == null) {
                // Fetch fail (view abhi server par nahi / network nahi): cache dikhao, warna empty state
                String cached = SupabaseSync.cachedIlaqaGrid(prefs);
                if (cached != null) {
                    try {
                        ilaqaData = new JSONArray(cached);
                        drawIlaqaGrid();
                        setIlaqaStatus(R.string.ilaqa_cached_note);
                    } catch (Exception e) {
                        setIlaqaStatus(R.string.ilaqa_empty);
                    }
                } else {
                    setIlaqaStatus(R.string.ilaqa_empty);
                }
            }
        });
    }

    /** Cell ka rang: community averages hamesha >= -70 hote hain, is liye strong-range scale. */
    private int gridColorFor(double avgDbm) {
        if (avgDbm >= -60) return Color.parseColor("#22C55E");
        if (avgDbm >= -67) return Color.parseColor("#F59E0B");
        return Color.parseColor("#EF4444");
    }

    /** Operator naam ko chip key me badlo ("Telenor Pakistan" -> "telenor") */
    private String normOpKey(String op) {
        String s = op == null ? "" : op.toLowerCase(Locale.US).trim();
        if (s.contains("jazz")) return "jazz";
        if (s.contains("zong")) return "zong";
        if (s.contains("telenor")) return "telenor";
        if (s.contains("ufone")) return "ufone";
        return s;
    }

    /**
     * Ilaqa grid draw karo — SIRF aggregated cells (Polygon), raw pins kabhi nahi.
     * Apni best-spot pin bhi is mode me nahi dikhti; sirf maujooda location ka dot.
     */
    private void drawIlaqaGrid() {
        if (osmMap == null) return;
        try {
            if (!ilaqaCentered) {
                try {
                    osmMap.getController().setZoom(15.0);
                    osmMap.getController().setCenter(decideCenter());
                } catch (Exception ignored) {}
                ilaqaCentered = true;
            }
            osmMap.getOverlays().clear();
            ilaqaCells.clear();
            if (ilaqaData != null) {
                // Rows ko cell ke hisab se group karo
                for (int i = 0; i < ilaqaData.length(); i++) {
                    try {
                        JSONObject r = ilaqaData.getJSONObject(i);
                        String key = r.optLong("cell_x", 0) + ":" + r.optLong("cell_y", 0);
                        List<JSONObject> list = ilaqaCells.get(key);
                        if (list == null) {
                            list = new ArrayList<>();
                            ilaqaCells.put(key, list);
                        }
                        list.add(r);
                    } catch (Exception ignored) {}
                }
                for (Map.Entry<String, List<JSONObject>> e : ilaqaCells.entrySet()) {
                    try {
                        // Filter ke hisab se is cell ki numainda row chuno
                        JSONObject chosen = null;
                        for (JSONObject r : e.getValue()) {
                            if (ilaqaOpKey.isEmpty()) {
                                if (chosen == null
                                        || r.optDouble("avg_dbm", -999) > chosen.optDouble("avg_dbm", -999)) {
                                    chosen = r; // All = best operator per cell
                                }
                            } else if (normOpKey(r.optString("op", "")).equals(ilaqaOpKey)) {
                                chosen = r;
                                break;
                            }
                        }
                        if (chosen == null) continue;
                        long cx = chosen.optLong("cell_x", 0);
                        long cy = chosen.optLong("cell_y", 0);
                        double lng0 = cx * CELL_DEG, lat0 = cy * CELL_DEG;
                        List<GeoPoint> pts = new ArrayList<>();
                        pts.add(new GeoPoint(lat0, lng0));
                        pts.add(new GeoPoint(lat0, lng0 + CELL_DEG));
                        pts.add(new GeoPoint(lat0 + CELL_DEG, lng0 + CELL_DEG));
                        pts.add(new GeoPoint(lat0 + CELL_DEG, lng0));
                        Polygon poly = new Polygon();
                        poly.setPoints(pts);
                        int col = gridColorFor(chosen.optDouble("avg_dbm", -120));
                        poly.setFillColor(Color.argb(110, Color.red(col), Color.green(col), Color.blue(col)));
                        poly.setStrokeColor(col);
                        poly.setStrokeWidth(2f);
                        poly.setId(e.getKey());
                        poly.setOnClickListener((p, mv, pos) -> {
                            showIlaqaCellInfo(p.getId());
                            return true;
                        });
                        osmMap.getOverlays().add(poly);
                    } catch (Exception ignored) {}
                }
            }
            // Meri maujooda location (neela dot) — ye reading pin nahi hai
            try {
                Location me = myLocation();
                if (me != null && validLatLng(me.getLatitude(), me.getLongitude())) {
                    Marker lm = new Marker(osmMap);
                    lm.setPosition(new GeoPoint(me.getLatitude(), me.getLongitude()));
                    lm.setTitle(getString(R.string.my_location));
                    lm.setIcon(dotIcon(Color.parseColor("#3B82F6")));
                    lm.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    osmMap.getOverlays().add(lm);
                }
            } catch (Exception ignored) {}
            osmMap.invalidate();
        } catch (Exception e) { e.printStackTrace(); }
        ilaqaDirty = false;
    }

    /** Cell tap: har operator ki average + count, aur sab se best */
    private void showIlaqaCellInfo(String cellKey) {
        List<JSONObject> rows = ilaqaCells.get(cellKey);
        if (rows == null || rows.isEmpty() || tvIlaqaInfo == null) return;
        try {
            List<JSONObject> sorted = new ArrayList<>(rows);
            Collections.sort(sorted, (a, b) -> Double.compare(
                    b.optDouble("avg_dbm", -999), a.optDouble("avg_dbm", -999)));
            StringBuilder sb = new StringBuilder(getString(R.string.ilaqa_cell_title));
            int devices = 0;
            for (JSONObject r : sorted) {
                devices = Math.max(devices, r.optInt("device_count", 0));
                sb.append("\n").append(getString(R.string.ilaqa_op_line,
                        r.optString("op", "?"),
                        Math.round(r.optDouble("avg_dbm", 0)),
                        r.optLong("reading_count", 0)));
            }
            if (devices > 0) {
                sb.append("\n").append(getString(R.string.ilaqa_cell_devices, devices));
            }
            sb.append("\n").append(getString(R.string.ilaqa_best_line,
                    sorted.get(0).optString("op", "?")));
            tvIlaqaInfo.setText(sb.toString());
            layoutIlaqaInfo.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {}
    }

    private int colorFor(int dbm) {
        if (dbm >= -80) return Color.parseColor("#22C55E");
        if (dbm >= -95) return Color.parseColor("#F59E0B");
        return Color.parseColor("#EF4444");
    }

    /** Tower pins: har Cell ID ka andazan maqam + average/best signal. */
    private void drawTowerPins() {
        try {
            List<TowerDb.Tower> towers = TowerDb.load(requireContext());
            if (towers.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_towers_yet, Toast.LENGTH_LONG).show();
                return;
            }
            for (TowerDb.Tower t : towers) {
                try {
                    if (!validLatLng(t.lat, t.lng)) continue;
                    Marker m = new Marker(osmMap);
                    m.setPosition(new GeoPoint(t.lat, t.lng));
                    String title = getString(R.string.tower_title, t.cellId);
                    if (!t.op.isEmpty()) title += " | " + t.op;
                    if (!t.band.isEmpty()) title += " | " + t.band;
                    m.setTitle(title);
                    m.setSnippet(getString(R.string.tower_snippet, t.avgDbm, t.count, t.bestDbm));
                    m.setIcon(towerIcon());
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    osmMap.getOverlays().add(m);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /** Tower ka icon: purple circle me safed antenna. */
    private BitmapDrawable towerIcon() {
        int s = 84;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        c.drawCircle(s / 2f, s / 2f, 34f, white);
        Paint purple = new Paint(Paint.ANTI_ALIAS_FLAG);
        purple.setColor(Color.parseColor("#A855F7"));
        c.drawCircle(s / 2f, s / 2f, 27f, purple);
        Paint w = new Paint(Paint.ANTI_ALIAS_FLAG);
        w.setColor(Color.WHITE);
        w.setStrokeWidth(5f);
        w.setStyle(Paint.Style.STROKE);
        w.setStrokeCap(Paint.Cap.ROUND);
        float cx = s / 2f;
        // Mast (khamba)
        c.drawLine(cx, 24, cx - 11, 56, w);
        c.drawLine(cx, 24, cx + 11, 56, w);
        // Cross bars
        c.drawLine(cx - 7, 42, cx + 7, 42, w);
        c.drawLine(cx - 4, 33, cx + 4, 33, w);
        // Base
        c.drawLine(cx - 15, 58, cx + 15, 58, w);
        // Antenna tip
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.WHITE);
        c.drawCircle(cx, 20, 4.5f, dot);
        return new BitmapDrawable(getResources(), bmp);
    }

    private BitmapDrawable dotIcon(int color) {
        int s = 72;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.WHITE);
        c.drawCircle(s / 2f, s / 2f, 30f, outer);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(color);
        c.drawCircle(s / 2f, s / 2f, 22f, inner);
        return new BitmapDrawable(getResources(), bmp);
    }

    private void loadAll() {
        // Best values pehle parho (type mismatch se bachne ke liye safe)
        try { bestLat = prefs.getFloat("best_lat", 0); } catch (Exception e) { bestLat = 0; }
        try { bestLng = prefs.getFloat("best_lng", 0); } catch (Exception e) { bestLng = 0; }
        try { bestDbm = prefs.getInt("best_dbm", -120); } catch (Exception e) { bestDbm = -120; }

        // Ilaqa mode: sirf aggregated grid — apni pins/best pin bilkul nahi
        if (mapMode == MODE_ILAQA) {
            updateBestCard();
            if (ilaqaDirty) drawIlaqaGrid();
            return;
        }

        // 1. View PEHLE set karo - pins me koi masla ho tab bhi map sahi zoom par khule
        GeoPoint center = decideCenter();
        try {
            osmMap.getController().setZoom(17.0);
            osmMap.getController().setCenter(center);
        } catch (Exception ignored) {}

        // 2. Pins - har reading alag try me taake ek kharab reading baqiyon ko na roke
        try {
            osmMap.getOverlays().clear();
            if (mapMode == MODE_TOWERS) {
                drawTowerPins();
            } else {
                JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        JSONObject o = arr.getJSONObject(i);
                        double lat = o.optDouble("lat", Double.NaN);
                        double lng = o.optDouble("lng", Double.NaN);
                        if (!validLatLng(lat, lng)) continue;
                        int dbm = o.optInt("dbm", -120);
                        Marker m = new Marker(osmMap);
                        m.setPosition(new GeoPoint(lat, lng));
                        String title = dbm + " dBm | " + o.optString("type", "");
                        String room = o.optString("room", "");
                        if (!room.isEmpty()) title = room + " | " + title;
                        m.setTitle(title);
                        m.setIcon(dotIcon(colorFor(dbm)));
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                        osmMap.getOverlays().add(m);
                    } catch (Exception ignored) {}
                }
            }

            if (validLatLng(bestLat, bestLng) && bestDbm > -120) {
                try {
                    Marker b = new Marker(osmMap);
                    b.setPosition(new GeoPoint(bestLat, bestLng));
                    b.setTitle(getString(R.string.best) + ": " + bestDbm + " dBm");
                    b.setIcon(dotIcon(Color.parseColor("#00E5FF")));
                    b.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    osmMap.getOverlays().add(b);
                } catch (Exception ignored) {}
            }

            // Meri maujooda location (neela dot)
            try {
                Location me = myLocation();
                if (me != null && validLatLng(me.getLatitude(), me.getLongitude())) {
                    Marker lm = new Marker(osmMap);
                    lm.setPosition(new GeoPoint(me.getLatitude(), me.getLongitude()));
                    lm.setTitle(getString(R.string.my_location));
                    lm.setIcon(dotIcon(Color.parseColor("#3B82F6")));
                    lm.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    osmMap.getOverlays().add(lm);
                }
            } catch (Exception ignored) {}

            osmMap.invalidate();
        } catch (Exception e) { e.printStackTrace(); }

        updateBestCard();
    }

    private boolean validLatLng(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }

    /** Center ka faisla: live location -> aakhri valid reading -> best spot -> Karachi */
    private GeoPoint decideCenter() {
        try {
            Location me = myLocation();
            if (me != null && validLatLng(me.getLatitude(), me.getLongitude())) {
                return new GeoPoint(me.getLatitude(), me.getLongitude());
            }
        } catch (Exception ignored) {}
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                double lat = o.optDouble("lat", Double.NaN);
                double lng = o.optDouble("lng", Double.NaN);
                if (validLatLng(lat, lng)) return new GeoPoint(lat, lng);
            }
        } catch (Exception ignored) {}
        if (validLatLng(bestLat, bestLng)) return new GeoPoint(bestLat, bestLng);
        return new GeoPoint(24.8607, 67.0011);
    }

    private void updateBestCard() {
        if (bestDbm <= -120 || bestLat == 0) {
            tvBestSignal.setText(getString(R.string.best) + ": -- dBm");
            tvBestDistance.setText(R.string.no_reading_yet);
            tvBestLocation.setText("");
            return;
        }
        tvBestSignal.setText(getString(R.string.best) + ": " + bestDbm + " dBm");
        tvBestLocation.setText(String.format(Locale.getDefault(), "%.5f, %.5f", bestLat, bestLng));

        Location me = myLocation();
        if (me != null) {
            Location target = new Location("best");
            target.setLatitude(bestLat);
            target.setLongitude(bestLng);
            float dist = me.distanceTo(target);
            String dir = compass(me.bearingTo(target));
            String distStr = dist < 1000
                    ? String.format(Locale.getDefault(), "%.0f m", dist)
                    : String.format(Locale.getDefault(), "%.1f km", dist / 1000);
            tvBestDistance.setText(String.format(getString(R.string.best_spot_go), distStr, dir));
        } else {
            tvBestDistance.setText(R.string.best_spot_found);
        }
    }

    private String compass(float bearing) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = Math.round(((bearing + 360) % 360) / 45f) % 8;
        return dirs[idx];
    }

    private Location myLocation() {
        // Pehle active helper se lo (GPS actively maanga hua)
        try {
            Location h = LocationHelper.getLast();
            if (h != null && validLatLng(h.getLatitude(), h.getLongitude())) return h;
        } catch (Exception ignored) {}
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (Exception ignored) { return null; }
    }

    private void centerBest() {
        if (bestLat == 0) {
            Toast.makeText(getContext(), R.string.first_record, Toast.LENGTH_SHORT).show();
            return;
        }
        osmMap.getController().animateTo(new GeoPoint(bestLat, bestLng));
        osmMap.getController().setZoom(17.0);
    }

    @Override public void onResume() {
        super.onResume();
        if (osmMap != null) {
            osmMap.onResume();
            scheduleIlaqaFetch();
            // History se aayi hui focus location? (reading par click)
            try {
                SharedPreferences fp = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
                String fla = fp.getString("map_focus_lat", "");
                String fln = fp.getString("map_focus_lng", "");
                if (!fla.isEmpty() && !fln.isEmpty()) {
                    double flt = Double.parseDouble(fla), flg = Double.parseDouble(fln);
                    fp.edit().remove("map_focus_lat").remove("map_focus_lng").apply();
                    focusMode = true;
                    try { if (myLocOverlay != null) myLocOverlay.disableFollowLocation(); } catch (Exception ignored) {}
                    osmMap.getController().setZoom(17.0);
                    osmMap.getController().animateTo(new org.osmdroid.util.GeoPoint(flt, flg));
                    Toast.makeText(getContext(), R.string.map_focus_toast, Toast.LENGTH_SHORT).show();
                } else {
                    focusMode = false;
                    try { if (myLocOverlay != null) myLocOverlay.enableFollowLocation(); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            // Active location shuru karo taake blue dot aur center sahi aaye
            try {
                LocationHelper.start(requireContext());
                LocationHelper.setListener(loc -> {
                    if (getActivity() == null || osmMap == null) return;
                    getActivity().runOnUiThread(() -> {
                        try {
                            // Focus mode me user location par wapas na jao
                            if (!focusMode) {
                                osmMap.getController().setCenter(
                                        new org.osmdroid.util.GeoPoint(loc.getLatitude(), loc.getLongitude()));
                            }
                            loadAll();
                        } catch (Exception ignored) {}
                    });
                });
            } catch (Exception ignored) {}
            loadAll();
        }
    }
    @Override public void onPause() {
        super.onPause();
        try { ilaqaHandler.removeCallbacks(ilaqaFetchRunnable); } catch (Exception ignored) {}
        try { if (myLocOverlay != null) myLocOverlay.disableMyLocation(); } catch (Exception ignored) {}
        try { LocationHelper.clearListener(); } catch (Exception ignored) {}
        try { LocationHelper.stop(); } catch (Exception ignored) {}
        if (osmMap != null) osmMap.onPause();
    }
}
