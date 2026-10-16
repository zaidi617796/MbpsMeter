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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.LocationHelper;
import com.mbpsmeter.app.util.TowerDb;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.List;
import java.util.Locale;

/**
 * MAP V6: colored pins + best spot distance/direction + Open in Maps.
 */
public class MapV4Fragment extends Fragment {

    private TextView tvBestSignal, tvBestLocation, tvBestDistance;
    private Button btnTowers;
    private SharedPreferences prefs;
    private MapView osmMap;
    private MyLocationNewOverlay myLocOverlay;
    private boolean focusMode = false;
    private boolean showTowers = false;

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
        btnTowers = v.findViewById(R.id.btnTowers);
        showTowers = prefs.getBoolean("show_towers", false);
        updateTowerBtn();
        btnTowers.setOnClickListener(x -> {
            showTowers = !showTowers;
            prefs.edit().putBoolean("show_towers", showTowers).apply();
            updateTowerBtn();
            if (showTowers && !prefs.getBoolean("tower_note_seen", false)) {
                prefs.edit().putBoolean("tower_note_seen", true).apply();
                Toast.makeText(requireContext(), R.string.tower_approx, Toast.LENGTH_LONG).show();
            }
            loadAll();
        });
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

    private void updateTowerBtn() {
        if (btnTowers == null) return;
        try {
            btnTowers.setText(showTowers ? getString(R.string.show_readings) : getString(R.string.show_towers));
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

        // 1. View PEHLE set karo - pins me koi masla ho tab bhi map sahi zoom par khule
        GeoPoint center = decideCenter();
        try {
            osmMap.getController().setZoom(17.0);
            osmMap.getController().setCenter(center);
        } catch (Exception ignored) {}

        // 2. Pins - har reading alag try me taake ek kharab reading baqiyon ko na roke
        try {
            osmMap.getOverlays().clear();
            if (showTowers) {
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
        try { if (myLocOverlay != null) myLocOverlay.disableMyLocation(); } catch (Exception ignored) {}
        try { LocationHelper.clearListener(); } catch (Exception ignored) {}
        try { LocationHelper.stop(); } catch (Exception ignored) {}
        if (osmMap != null) osmMap.onPause();
    }
}
