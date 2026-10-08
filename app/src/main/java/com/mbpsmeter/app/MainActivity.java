package com.mbpsmeter.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS = 44;
    private static final String PREFS = "mbpsmeter_v4";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root, content, nav;
    private TextView signalText, networkText, liveTrafficText, homeLocationText;
    private TextView testResultText, mapInfoText, historyText;
    private Button testButton, saveSignalButton;
    private MapView map;
    private TelephonyManager telephony;
    private Location currentLocation;
    private int currentDbm = -120;
    private int selectedTab = 0;
    private long lastRx = -1, lastTx = -1;
    private boolean testRunning = false;
    private LocationCallback locationCallback;
    private final SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        buildShell();
        requestPermissionsIfNeeded();
        startTrafficTicker();
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(11, 18, 32));
        TextView title = new TextView(this);
        title.setText("MbpsMeter  V4");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.addView(title);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.rgb(20, 31, 51));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        String[] labels = {"Home", "Speed Test", "Signal Map", "History"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            MaterialButton b = new MaterialButton(this);
            b.setText(labels[i]);
            b.setTextSize(10);
            b.setInsetTop(0); b.setInsetBottom(0);
            b.setCornerRadius(dp(10));
            b.setAllCaps(false);
            b.setOnClickListener(v -> showTab(tab));
            nav.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        }
        setContentView(root);
        showTab(0);
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private ScrollView scroll(LinearLayout panel) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.addView(panel);
        return s;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(8), dp(14), dp(16));
        return p;
    }

    private void showTab(int tab) {
        selectedTab = tab;
        content.removeAllViews();
        if (map != null) {
            try { map.onPause(); } catch (Exception ignored) {}
            map = null;
        }
        if (tab == 0) buildHome();
        else if (tab == 1) buildSpeedTest();
        else if (tab == 2) buildMap();
        else buildHistory();
    }

    private void buildHome() {
        LinearLayout p = panel();
        p.addView(text("LIVE NETWORK STATUS", 13));
        signalText = text("Cellular signal: waiting for permission/device reading…", 20);
        p.addView(signalText);
        networkText = text("Network: checking…", 15);
        p.addView(networkText);
        liveTrafficText = text("Device traffic: measuring…", 18);
        p.addView(liveTrafficText);
        homeLocationText = text("Location: not available yet", 14);
        p.addView(homeLocationText);
        p.addView(text("Tip: walk a short distance and collect readings in Signal Map. Only locations you measure can be compared.", 14));
        p.addView(button("Save current signal reading", this::saveSignalReading));
        content.addView(scroll(p), new LinearLayout.LayoutParams(-1, -1));
        updateHome();
    }

    private void updateHome() {
        if (signalText != null) signalText.setText("Cellular signal: " + currentDbm + " dBm");
        if (networkText != null) {
            String quality = currentDbm >= -85 ? "Strong" : currentDbm >= -100 ? "Fair" : "Weak";
            networkText.setText("Signal quality estimate: " + quality + "\nNote: dBm thresholds vary by radio technology.");
        }
        if (homeLocationText != null && currentLocation != null) {
            homeLocationText.setText(String.format(Locale.US, "Location: %.5f, %.5f",
                    currentLocation.getLatitude(), currentLocation.getLongitude()));
        }
    }

    private void buildSpeedTest() {
        LinearLayout p = panel();
        p.addView(text("INTERNET SPEED TEST", 18));
        p.addView(text("Run a test when you are ready. This transfers data and may use your mobile data allowance.", 14));
        testResultText = text("Download: -- Mbps\nUpload: -- Mbps\nStatus: ready", 20);
        p.addView(testResultText);
        testButton = button("Start speed test", this::runSpeedTest);
        p.addView(testButton);
        p.addView(text("Results depend on your connection and the public test server. Close other downloads for a fairer result.", 13));
        content.addView(scroll(p), new LinearLayout.LayoutParams(-1, -1));
    }

    private void runSpeedTest() {
        if (testRunning) return;
        testRunning = true;
        if (testButton != null) testButton.setEnabled(false);
        if (testResultText != null) testResultText.setText("Testing download…\nPlease wait.");
        new Thread(() -> {
            double down = -1, up = -1;
            String error = null;
            try {
                String url = "https://speed.cloudflare.com/__down?bytes=5000000";
                long start = System.nanoTime();
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setUseCaches(false);
                long bytes = 0;
                try (java.io.InputStream in = c.getInputStream()) {
                    byte[] buffer = new byte[32768]; int n;
                    while ((n = in.read(buffer)) != -1) bytes += n;
                }
                c.disconnect();
                double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
                if (seconds > 0 && bytes > 0) down = (bytes * 8.0 / seconds) / 1_000_000.0;

                if (testResultText != null) runOnUiThread(() -> testResultText.setText("Download complete.\nTesting upload…"));
                byte[] payload = new byte[1_000_000];
                java.util.Arrays.fill(payload, (byte) 65);
                HttpURLConnection u = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
                u.setRequestMethod("POST"); u.setDoOutput(true); u.setConnectTimeout(15000); u.setReadTimeout(20000);
                u.setRequestProperty("Content-Type", "application/octet-stream");
                long us = System.nanoTime();
                try (OutputStream out = u.getOutputStream()) { out.write(payload); }
                int code = u.getResponseCode();
                try (java.io.InputStream in = (code >= 200 && code < 400) ? u.getInputStream() : u.getErrorStream()) {
                    if (in != null) { byte[] b = new byte[4096]; while (in.read(b) != -1) {} }
                }
                u.disconnect();
                double usec = (System.nanoTime() - us) / 1_000_000_000.0;
                if (usec > 0 && code >= 200 && code < 400) up = (payload.length * 8.0 / usec) / 1_000_000.0;
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "test failed" : e.getMessage());
            }
            final double d = down, u = up;
            final String err = error;
            runOnUiThread(() -> {
                testRunning = false;
                if (testButton != null) testButton.setEnabled(true);
                if (testResultText != null) {
                    String result = String.format(Locale.US, "Download: %s\nUpload: %s\nStatus: %s",
                            d >= 0 ? String.format(Locale.US, "%.2f Mbps", d) : "failed",
                            u >= 0 ? String.format(Locale.US, "%.2f Mbps", u) : "failed",
                            err == null ? "complete" : err);
                    testResultText.setText(result);
                    if (d >= 0 || u >= 0) saveTest(d, u, err == null ? "complete" : err);
                }
            });
        }).start();
    }

    private void buildMap() {
        LinearLayout p = panel();
        mapInfoText = text("Signal Map: waiting for GPS permission/location.", 14);
        p.addView(mapInfoText);
        p.addView(button("Save current signal reading", this::saveSignalReading));
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(16.0);
        if (currentLocation != null) {
            GeoPoint point = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            map.getController().setCenter(point);
        } else {
            map.getController().setCenter(new GeoPoint(24.8607, 67.0011)); // fallback until GPS fix
        }
        p.addView(map, new LinearLayout.LayoutParams(-1, dp(390)));
        p.addView(text("Green = strong, yellow = fair, red = weak. These markers represent readings saved by this phone, not a live tower coverage database.", 13));
        content.addView(scroll(p), new LinearLayout.LayoutParams(-1, -1));
        drawSavedReadings();
    }

    private void drawSavedReadings() {
        if (map == null) return;
        JSONArray arr = getSavedSignals();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || !obj.has("lat") || !obj.has("lon")) continue;
            double lat = obj.optDouble("lat", 0), lon = obj.optDouble("lon", 0);
            int dbm = obj.optInt("dbm", -120);
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(lat, lon));
            m.setTitle((dbm >= -85 ? "Strong" : dbm >= -100 ? "Fair" : "Weak") + " signal");
            m.setSnippet(dbm + " dBm • " + obj.optString("time", ""));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(m);
            Polygon circle = new Polygon(map);
            circle.setPoints(Polygon.pointsAsCircle(new GeoPoint(lat, lon), 8));
            int color = dbm >= -85 ? 0x6034D399 : dbm >= -100 ? 0x60FACC15 : 0x60EF4444;
            circle.setFillColor(color); circle.setStrokeColor(color); circle.setStrokeWidth(2);
            map.getOverlays().add(circle);
        }
        if (currentLocation != null) {
            GeoPoint now = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            Marker m = new Marker(map); m.setPosition(now); m.setTitle("Current location");
            map.getOverlays().add(m); map.getController().setCenter(now);
        }
        map.invalidate();
    }

    private void buildHistory() {
        LinearLayout p = panel();
        p.addView(text("SAVED HISTORY", 18));
        historyText = text("", 14);
        p.addView(historyText);
        p.addView(button("Refresh history", () -> refreshHistory()));
        p.addView(button("Clear all history", () -> {
            prefs().edit().remove("signals").remove("tests").apply();
            refreshHistory();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        }));
        content.addView(scroll(p), new LinearLayout.LayoutParams(-1, -1));
        refreshHistory();
    }

    private void refreshHistory() {
        if (historyText == null) return;
        StringBuilder s = new StringBuilder();
        JSONArray signals = getSavedSignals();
        s.append("SIGNAL READINGS: ").append(signals.length()).append("\n\n");
        for (int i = signals.length() - 1; i >= 0; i--) {
            JSONObject o = signals.optJSONObject(i);
            if (o != null) s.append(o.optString("time", "")).append("\n")
                    .append(o.optInt("dbm", -120)).append(" dBm • ")
                    .append(o.optString("lat", "no GPS")).append(", ")
                    .append(o.optString("lon", "")).append("\n\n");
        }
        JSONArray tests = getSavedTests();
        s.append("\nSPEED TESTS: ").append(tests.length()).append("\n\n");
        for (int i = tests.length() - 1; i >= 0; i--) {
            JSONObject o = tests.optJSONObject(i);
            if (o != null) s.append(o.optString("time", "")).append("\nDownload: ")
                    .append(o.optString("down", "failed")).append(" • Upload: ")
                    .append(o.optString("up", "failed")).append("\n\n");
        }
        if (signals.length() == 0 && tests.length() == 0) s.append("No saved records yet. Save a signal reading or run a speed test.");
        historyText.setText(s.toString());
    }

    private String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date());
    }

    private JSONArray getSavedSignals() {
        try { return new JSONArray(prefs().getString("signals", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private JSONArray getSavedTests() {
        try { return new JSONArray(prefs().getString("tests", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void saveSignalReading() {
        if (currentLocation == null) {
            Toast.makeText(this, "GPS location not available yet. Allow location and wait a few seconds.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            JSONArray a = getSavedSignals();
            JSONObject o = new JSONObject();
            o.put("dbm", currentDbm);
            o.put("lat", currentLocation.getLatitude());
            o.put("lon", currentLocation.getLongitude());
            o.put("time", now());
            a.put(o);
            // Keep the latest 1000 records.
            JSONArray capped = new JSONArray();
            int start = Math.max(0, a.length() - 1000);
            for (int i = start; i < a.length(); i++) capped.put(a.get(i));
            prefs().edit().putString("signals", capped.toString()).apply();
            Toast.makeText(this, "Signal reading saved", Toast.LENGTH_SHORT).show();
            if (selectedTab == 3) refreshHistory();
            if (selectedTab == 2) { map.getOverlays().clear(); drawSavedReadings(); }
        } catch (Exception e) {
            Toast.makeText(this, "Could not save reading", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveTest(double down, double up, String status) {
        try {
            JSONArray a = getSavedTests();
            JSONObject o = new JSONObject();
            o.put("down", down >= 0 ? String.format(Locale.US, "%.2f Mbps", down) : "failed");
            o.put("up", up >= 0 ? String.format(Locale.US, "%.2f Mbps", up) : "failed");
            o.put("status", status); o.put("time", now());
            a.put(o);
            JSONArray capped = new JSONArray();
            int start = Math.max(0, a.length() - 500);
            for (int i = start; i < a.length(); i++) capped.put(a.get(i));
            prefs().edit().putString("tests", capped.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void requestPermissionsIfNeeded() {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.READ_PHONE_STATE);
        if (!missing.isEmpty()) ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSIONS);
        else startMonitoring();
    }

    private void startMonitoring() {
        startLocationUpdates();
        startSignalListener();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000).build();
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;
                currentLocation = loc;
                updateHome();
                if (mapInfoText != null) mapInfoText.setText(String.format(Locale.US,
                        "Current location: %.5f, %.5f\nSaved points appear on this map.", loc.getLatitude(), loc.getLongitude()));
            }
        };
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    @SuppressWarnings("deprecation")
    private void startSignalListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        try {
            telephony.listen(new PhoneStateListener() {
                @Override public void onSignalStrengthsChanged(SignalStrength strength) {
                    super.onSignalStrengthsChanged(strength);
                    int dbm = -120;
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 29 && !strength.getCellSignalStrengths().isEmpty())
                            dbm = strength.getCellSignalStrengths().get(0).getDbm();
                    } catch (Exception ignored) {}
                    currentDbm = dbm;
                    runOnUiThread(() -> updateHome());
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        } catch (Exception ignored) {}
    }

    private void startTrafficTicker() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
                if (lastRx >= 0 && rx >= lastRx && tx >= lastTx) {
                    double down = (rx - lastRx) * 8.0 / 1_000_000.0;
                    double up = (tx - lastTx) * 8.0 / 1_000_000.0;
                    if (liveTrafficText != null) liveTrafficText.setText(String.format(Locale.US,
                            "Device traffic (last interval)\n↓ %.3f Mbps    ↑ %.3f Mbps", down, up));
                }
                lastRx = rx; lastTx = tx;
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS) {
            boolean locationGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (locationGranted || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                startMonitoring();
            updateHome();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override protected void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (locationCallback != null) LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(locationCallback);
        try { if (telephony != null) telephony.listen(null, PhoneStateListener.LISTEN_NONE); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
