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
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

private static final int PERMISSION_CODE = 44;
private static final String PREFS = "mbpsmeter_v4";

private final Handler handler = new Handler(Looper.getMainLooper());

private LinearLayout root, content, navigation;
private TextView signalText, networkText, locationText;
private TextView trafficText, speedText, mapInfoText;
private TextView historyText, bestText, directionText;
private Button speedButton;

private MapView map;
private TelephonyManager telephony;
private LocationCallback locationCallback;
private Location currentLocation;

private int currentDbm = -120;
private int currentTab = 0;

private long previousRx = -1;
private long previousTx = -1;

private boolean speedTestRunning = false;
private boolean historySaving = false;

private final Runnable trafficRunnable = new Runnable() {
    @Override
    public void run() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();

        if (rx >= 0 && tx >= 0 && previousRx >= 0
                && rx >= previousRx && tx >= previousTx) {

            double down = (rx - previousRx) * 8.0 / 1000000.0;
            double up = (tx - previousTx) * 8.0 / 1000000.0;

            if (trafficText != null) {
                trafficText.setText(String.format(
                        Locale.US,
                        "Device network traffic\n↓ %.3f Mbps     ↑ %.3f Mbps",
                        down, up
                ));
            }
        }

        previousRx = rx;
        previousTx = tx;

        handler.postDelayed(this, 1000);
    }
};

private SharedPreferences prefs() {
    return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
}

private int dp(float value) {
    return (int) (value * getResources()
            .getDisplayMetrics().density + 0.5f);
}

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Configuration.getInstance().load(
            getApplicationContext(),
            getSharedPreferences("osmdroid", MODE_PRIVATE)
    );

    Configuration.getInstance().setUserAgentValue(getPackageName());

    telephony = (TelephonyManager)
            getSystemService(Context.TELEPHONY_SERVICE);

    buildApplication();
    requestAppPermissions();

    handler.postDelayed(trafficRunnable, 1000);
}

private void buildApplication() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(11, 18, 32));

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setPadding(dp(18), dp(14), dp(18), dp(14));
    header.setBackgroundColor(Color.rgb(17, 27, 47));

    TextView title = makeText("MbpsMeter", 27, true);
    title.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(title);

    TextView subtitle = makeText(
            "YOUR NETWORK • YOUR SIGNAL",
            11, true
    );
    subtitle.setTextColor(Color.rgb(56, 189, 248));
    header.addView(subtitle);

    root.addView(header);

    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);

    root.addView(content,
            new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0, 1
            ));

    navigation = new LinearLayout(this);
    navigation.setOrientation(LinearLayout.HORIZONTAL);
    navigation.setPadding(dp(3), dp(5), dp(3), dp(5));
    navigation.setBackgroundColor(Color.rgb(20, 31, 51));

    root.addView(navigation,
            new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(62)
            ));

    String[] labels = {
            "LIVE", "SPEED", "MAP", "HISTORY"
    };

    for (int i = 0; i < labels.length; i++) {
        final int tab = i;

        Button button = new Button(this);
        button.setText(labels[i]);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(1), 0, dp(1), 0);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(30, 41, 59)
                )
        );

        button.setOnClickListener(v -> showTab(tab));

        navigation.addView(button,
                new LinearLayout.LayoutParams(0, -1, 1));
    }

    setContentView(root);
    showTab(0);
}

private TextView makeText(
        String value, int size, boolean bold) {

    TextView text = new TextView(this);
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(Color.WHITE);

    if (bold) {
        text.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );
    }

    text.setPadding(dp(10), dp(8), dp(10), dp(8));
    return text;
}

private Button makeButton(
        String label, Runnable action) {

    Button button = new Button(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setOnClickListener(v -> action.run());

    return button;
}

private LinearLayout makePanel() {
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(12), dp(8), dp(12), dp(18));
    return panel;
}

private ScrollView makeScroll(LinearLayout panel) {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.addView(panel);
    return scroll;
}

private void showTab(int tab) {
    currentTab = tab;

    if (map != null) {
        try {
            map.onPause();
        } catch (Exception ignored) {
        }

        map = null;
    }

    content.removeAllViews();

    if (tab == 0) {
        buildLiveTab();
    } else if (tab == 1) {
        buildSpeedTab();
    } else if (tab == 2) {
        buildMapTab();
    } else {
        buildHistoryTab();
    }
}

private void buildLiveTab() {
    LinearLayout panel = makePanel();

    TextView heading = makeText(
            "LIVE NETWORK MONITOR", 17, true
    );
    panel.addView(heading);

    signalText = makeText(
            currentDbm + " dBm", 32, true
    );
    signalText.setTextColor(Color.rgb(52, 211, 153));
    panel.addView(signalText);

    networkText = makeText(
            "Checking cellular network...", 14, false
    );
    panel.addView(networkText);

    trafficText = makeText(
            "Measuring device traffic...", 16, true
    );
    panel.addView(trafficText);

    locationText = makeText(
            "Waiting for GPS location...", 13, false
    );
    panel.addView(locationText);

    panel.addView(makeButton(
            "SAVE SIGNAL NOW",
            this::saveSignalReading
    ));

    panel.addView(makeText(
            "Walk to different nearby places and save readings. "
                    + "The app compares places that you have actually measured.",
            13, false
    ));

    content.addView(
            makeScroll(panel),
            new LinearLayout.LayoutParams(-1, -1)
    );

    updateLiveScreen();
}

private void updateLiveScreen() {
    if (signalText != null) {
        signalText.setText(currentDbm + " dBm");

        int color = currentDbm >= -85
                ? Color.rgb(52, 211, 153)
                : currentDbm >= -100
                ? Color.rgb(250, 204, 21)
                : Color.rgb(248, 113, 113);

        signalText.setTextColor(color);
    }

    if (networkText != null) {
        String quality = currentDbm >= -85
                ? "STRONG SIGNAL"
                : currentDbm >= -100
                ? "FAIR SIGNAL"
                : "WEAK SIGNAL";

        networkText.setText(
                quality
                        + "\nLower absolute dBm generally means weaker signal."
        );
    }

    if (locationText != null) {
        if (currentLocation == null) {
            locationText.setText(
                    "Waiting for GPS location..."
            );
        } else {
            locationText.setText(String.format(
                    Locale.US,
                    "GPS: %.5f, %.5f",
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude()
            ));
        }
    }
}

private void buildSpeedTab() {
    LinearLayout panel = makePanel();

    panel.addView(makeText(
            "INTERNET SPEED TEST", 20, true
    ));

    panel.addView(makeText(
            "A real internet speed test transfers data. "
                    + "Mobile data charges may apply.", 14, false
    ));

    speedText = makeText(
            "DOWNLOAD\n-- Mbps\n\nUPLOAD\n-- Mbps\n\nStatus: Ready",
            22, true
    );

    panel.addView(speedText);

    speedButton = makeButton(
            "START SPEED TEST",
            this::runSpeedTest
    );

    panel.addView(speedButton);

    panel.addView(makeText(
            "For more reliable results, pause other downloads "
                    + "and uploads during the test.", 13, false
    ));

    content.addView(
            makeScroll(panel),
            new LinearLayout.LayoutParams(-1, -1)
    );
}

private void runSpeedTest() {
    if (speedTestRunning) {
        return;
    }

    speedTestRunning = true;

    if (speedButton != null) {
        speedButton.setEnabled(false);
    }

    if (speedText != null) {
        speedText.setText("Testing download speed...\nPlease wait.");
    }

    new Thread(() -> {
        double download = -1;
        double upload = -1;
        String error = "Test failed";

        try {
            long start = System.nanoTime();
            long bytes = 0;

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(
                            "https://speed.cloudflare.com/__down?bytes=5000000"
                    ).openConnection();

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setUseCaches(false);

            try (InputStream input =
                         connection.getInputStream()) {

                byte[] buffer = new byte[32768];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    bytes += count;
                }
            }

            connection.disconnect();

            double seconds =
                    (System.nanoTime() - start) / 1e9;

            if (seconds > 0 && bytes > 0) {
                download = bytes * 8.0 / seconds / 1e6;
            }

            byte[] payload = new byte[500000];
            java.util.Arrays.fill(payload, (byte) 65);

            HttpURLConnection uploadConnection =
                    (HttpURLConnection) new URL(
                            "https://speed.cloudflare.com/__up"
                    ).openConnection();

            uploadConnection.setRequestMethod("POST");
            uploadConnection.setDoOutput(true);
            uploadConnection.setConnectTimeout(15000);
            uploadConnection.setReadTimeout(25000);
            uploadConnection.setFixedLengthStreamingMode(
                    payload.length
            );
            uploadConnection.setRequestProperty(
                    "Content-Type",
                    "application/octet-stream"
            );

            long uploadStart = System.nanoTime();

            try (OutputStream output =
                         uploadConnection.getOutputStream()) {
                output.write(payload);
                output.flush();
            }

            int responseCode =
                    uploadConnection.getResponseCode();

            InputStream responseStream =
                    responseCode >= 200 && responseCode < 400
                            ? uploadConnection.getInputStream()
                            : uploadConnection.getErrorStream();

            if (responseStream != null) {
                try (InputStream input = responseStream) {
                    byte[] buffer = new byte[4096];

                    while (input.read(buffer) != -1) {
                        // Consume response.
                    }
                }
            }

            double uploadSeconds =
                    (System.nanoTime() - uploadStart) / 1e9;

            if (responseCode >= 200 && responseCode < 400
                    && uploadSeconds > 0) {
                upload = payload.length * 8.0
                        / uploadSeconds / 1e6;
                error = "Complete";
            } else {
                error = "Upload server returned HTTP "
                        + responseCode;
            }

            uploadConnection.disconnect();

        } catch (Exception e) {
            error = e.getClass().getSimpleName()
                    + ": "
                    + (e.getMessage() == null
                    ? "Connection failed" : e.getMessage());
        }

        final double finalDownload = download;
        final double finalUpload = upload;
        final String finalError = error;

        runOnUiThread(() -> {
            speedTestRunning = false;

            if (speedButton != null) {
                speedButton.setEnabled(true);
            }

            if (speedText != null) {
                String down = finalDownload >= 0
                        ? String.format(
                        Locale.US, "%.2f Mbps", finalDownload)
                        : "Failed";

                String up = finalUpload >= 0
                        ? String.format(
                        Locale.US, "%.2f Mbps", finalUpload)
                        : "Failed";

                speedText.setText(
                        "DOWNLOAD\n" + down
                                + "\n\nUPLOAD\n" + up
                                + "\n\nStatus: " + finalError
                );
            }

            if (finalDownload >= 0 || finalUpload >= 0) {
                saveSpeedTest(
                        finalDownload,
                        finalUpload,
                        finalError
                );
            }
        });

    }).start();
}

private void buildMapTab() {
    LinearLayout panel = makePanel();

    mapInfoText = makeText(
            "Loading saved signal readings...", 14, false
    );
    panel.addView(mapInfoText);

    panel.addView(makeButton(
            "SAVE CURRENT SIGNAL",
            this::saveSignalReading
    ));

    panel.addView(makeButton(
            "REFRESH MAP",
            this::refreshMap
    ));

    map = new MapView(this);
    map.setTileSource(TileSourceFactory.MAPNIK);
    map.setMultiTouchControls(true);
    map.setTilesScaledToDpi(true);

    map.getController().setZoom(17.0);

    if (currentLocation != null) {
        map.getController().setCenter(
                new GeoPoint(
                        currentLocation.getLatitude(),
                        currentLocation.getLongitude()
                )
        );
    } else {
        map.getController().setCenter(
                new GeoPoint(24.8607, 67.0011)
        );
    }

    panel.addView(map,
            new LinearLayout.LayoutParams(
                    -1, dp(350)
            ));

    panel.addView(makeText(
            "Green: strong • Yellow: fair • Red: weak\n"
                    + "Markers show readings saved by your phone, "
                    + "not a live mobile tower coverage database.",
            13, false
    ));

    content.addView(
            makeScroll(panel),
            new LinearLayout.LayoutParams(-1, -1)
    );

    drawSavedReadings();
    updateMapInfo();
}

private void updateMapInfo() {
    if (mapInfoText == null) {
        return;
    }

    JSONArray readings = getSignalHistory();

    if (currentLocation == null) {
        mapInfoText.setText(
                "Waiting for GPS. Saved readings: "
                        + readings.length()
        );
    } else {
        mapInfoText.setText(String.format(
                Locale.US,
                "You are here: %.5f, %.5f\nSaved readings: %d",
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                readings.length()
        ));
    }
}

private void refreshMap() {
    if (map == null) {
        return;
    }

    map.getOverlays().clear();
    drawSavedReadings();
    updateMapInfo();
    map.invalidate();

    Toast.makeText(
            this, "Map refreshed", Toast.LENGTH_SHORT
    ).show();
}

private void drawSavedReadings() {
    if (map == null) {
        return;
    }

    JSONArray readings = getSignalHistory();

    for (int i = 0; i < readings.length(); i++) {
        JSONObject item = readings.optJSONObject(i);

        if (item == null) {
            continue;
        }

        double lat = item.optDouble("lat", Double.NaN);
        double lon = item.optDouble("lon", Double.NaN);

        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            continue;
        }

        int dbm = item.optInt("dbm", -120);
        GeoPoint point = new GeoPoint(lat, lon);

        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setTitle(signalQuality(dbm));
        marker.setSnippet(
                dbm + " dBm • " + item.optString("time", "")
        );
        marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
        );

        map.getOverlays().add(marker);

        Polygon circle = new Polygon(map);
        circle.setPoints(
                Polygon.pointsAsCircle(point, 8)
        );

        int color = dbm >= -85
                ? 0x6034D399
                : dbm >= -100
                ? 0x60FACC15
                : 0x60EF4444;

        circle.setFillColor(color);
        circle.setStrokeColor(color);
        circle.setStrokeWidth(2);

        map.getOverlays().add(circle);
    }

    if (currentLocation != null) {
        GeoPoint here = new GeoPoint(
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        );

        Marker currentMarker = new Marker(map);
        currentMarker.setPosition(here);
        currentMarker.setTitle("Current location");
        map.getOverlays().add(currentMarker);

        map.getController().setCenter(here);
    }

    map.invalidate();
}

private String signalQuality(int dbm) {
    if (dbm >= -85) {
        return "Strong signal";
    }

    if (dbm >= -100) {
        return "Fair signal";
    }

    return "Weak signal";
}

private void buildHistoryTab() {
    LinearLayout panel = makePanel();

    panel.addView(makeText(
            "YOUR SIGNAL HISTORY", 19, true
    ));

    historyText = makeText("", 13, false);
    panel.addView(historyText);

    panel.addView(makeButton(
            "REFRESH HISTORY",
            this::refreshHistory
    ));

    panel.addView(makeButton(
            "CLEAR ALL HISTORY",
            () -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear history?")
                    .setMessage(
                            "This will permanently delete saved signal "
                                    + "readings and speed tests."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (dialog, which) -> {
                        prefs().edit()
                                .remove("signals")
                                .remove("tests")
                                .apply();

                        refreshHistory();

                        Toast.makeText(
                                this,
                                "History cleared",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .show()
    ));

    content.addView(
            makeScroll(panel),
            new LinearLayout.LayoutParams(-1, -1)
    );

    refreshHistory();
}

private void refreshHistory() {
    if (historyText == null) {
        return;
    }

    StringBuilder result = new StringBuilder();

    JSONArray signals = getSignalHistory();

    result.append("SIGNAL READINGS: ")
            .append(signals.length())
            .append("\n\n");

    for (int i = signals.length() - 1; i >= 0; i--) {
        JSONObject item = signals.optJSONObject(i);

        if (item == null) {
            continue;
        }

        result.append(item.optString("time", "Unknown time"))
                .append("\n")
                .append(item.optInt("dbm", -120))
                .append(" dBm • ")
                .append(item.optString("quality", "Unknown"))
                .append("\n");

        if (item.has("lat") && item.has("lon")) {
            result.append(item.optDouble("lat"))
                    .append(", ")
                    .append(item.optDouble("lon"))
                    .append("\n");
        } else {
            result.append("GPS unavailable\n");
        }

        result.append("\n");
    }

    JSONArray tests = getSpeedHistory();

    result.append("\nSPEED TESTS: ")
            .append(tests.length())
            .append("\n\n");

    for (int i = tests.length() - 1; i >= 0; i--) {
        JSONObject item = tests.optJSONObject(i);

        if (item == null) {
            continue;
        }

        result.append(item.optString("time", ""))
                .append("\nDownload: ")
                .append(item.optString("down", "Failed"))
                .append("\nUpload: ")
                .append(item.optString("up", "Failed"))
                .append("\n\n");
    }

    if (signals.length() == 0 && tests.length() == 0) {
        result.append(
                "No history yet. Signal readings are saved "
                        + "automatically when GPS and signal data "
                        + "are available."
        );
    }

    historyText.setText(result.toString());
}

private JSONArray getSignalHistory() {
    try {
        return new JSONArray(
                prefs().getString("signals", "[]")
        );
    } catch (Exception e) {
        return new JSONArray();
    }
}

private JSONArray getSpeedHistory() {
    try {
        return new JSONArray(
                prefs().getString("tests", "[]")
        );
    } catch (Exception e) {
        return new JSONArray();
    }
}

private String currentTime() {
    return new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
    ).format(new Date());
}

private void saveSignalReading() {
    if (currentLocation == null) {
        Toast.makeText(
                this,
                "GPS not ready. Wait for location permission and GPS fix.",
                Toast.LENGTH_LONG
        ).show();

        return;
    }

    saveSignalToHistory(
            currentDbm,
            currentLocation.getLatitude(),
            currentLocation.getLongitude()
    );

    Toast.makeText(
            this,
            "Signal reading saved",
            Toast.LENGTH_SHORT
    ).show();

    refreshVisibleHistoryAndMap();
}

private void saveSignalToHistory(
        int dbm, double lat, double lon) {

    try {
        JSONArray old = getSignalHistory();
        JSONArray updated = new JSONArray();

        JSONObject item = new JSONObject();
        item.put("dbm", dbm);
        item.put("lat", lat);
        item.put("lon", lon);
        item.put("time", currentTime());
        item.put("quality", signalQuality(dbm));

        old.put(item);

        int start = Math.max(0, old.length() - 1000);

        for (int i = start; i < old.length(); i++) {
            updated.put(old.get(i));
        }

        prefs().edit()
                .putString("signals", updated.toString())
                .apply();

    } catch (Exception ignored) {
    }
}

private void refreshVisibleHistoryAndMap() {
    if (currentTab == 3) {
        refreshHistory();
    }

    if (currentTab == 2) {
        refreshMap();
    }

    updateMapInfo();
}

private void saveSpeedTest(
        double down, double up, String status) {

    try {
        JSONArray old = getSpeedHistory();
        JSONArray updated = new JSONArray();

        JSONObject item = new JSONObject();

        item.put(
                "down",
                down >= 0
                        ? String.format(
                        Locale.US, "%.2f Mbps", down)
                        : "Failed"
        );

        item.put(
                "up",
                up >= 0
                        ? String.format(
                        Locale.US, "%.2f Mbps", up)
                        : "Failed"
        );

        item.put("status", status);
        item.put("time", currentTime());

        old.put(item);

        int start = Math.max(0, old.length() - 500);

        for (int i = start; i < old.length(); i++) {
            updated.put(old.get(i));
        }

        prefs().edit()
                .putString("tests", updated.toString())
                .apply();

    } catch (Exception ignored) {
    }
}

private void requestAppPermissions() {
    ArrayList<String> missing = new ArrayList<>();

    if (ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
    ) != PackageManager.PERMISSION_GRANTED) {
        missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    if (ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) != PackageManager.PERMISSION_GRANTED) {
        missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    if (ActivityCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
    ) != PackageManager.PERMISSION_GRANTED) {
        missing.add(Manifest.permission.READ_PHONE_STATE);
    }

    if (!missing.isEmpty()) {
        ActivityCompat.requestPermissions(
                this,
                missing.toArray(new String[0]),
                PERMISSION_CODE
        );
    } else {
        startMonitoring();
    }
}

private void startMonitoring() {
    startLocationUpdates();
    startSignalListener();
}

private void startLocationUpdates() {
    boolean fine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED;

    boolean coarse = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED;

    if (!fine && !coarse) {
        return;
    }

    LocationRequest request =
            new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    10000
            )
                    .setMinUpdateIntervalMillis(5000)
                    .build();

    locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(
                @NonNull LocationResult result) {

            Location location = result.getLastLocation();

            if (location == null) {
                return;
            }

            currentLocation = location;

            updateLiveScreen();
            updateMapInfo();

            if (map != null && currentTab == 2) {
                drawCurrentMarkerOnly();
            }

            // Automatically save only after a meaningful
            // movement or after five minutes at the same place.
            maybeSaveAutomaticReading();
        }
    };

    LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
            );
}

private void drawCurrentMarkerOnly() {
    if (map == null || currentLocation == null) {
        return;
    }

    map.invalidate();
}

private void maybeSaveAutomaticReading() {
    if (currentLocation == null || historySaving) {
        return;
    }

    SharedPreferences p = prefs();

    long previousTime = p.getLong("auto_time", 0);

    double previousLat = Double.longBitsToDouble(
            p.getLong("auto_lat", Double.doubleToLongBits(0))
    );

    double previousLon = Double.longBitsToDouble(
            p.getLong("auto_lon", Double.doubleToLongBits(0))
    );

    long now = System.currentTimeMillis();

    float[] distance = new float[1];

    Location.distanceBetween(
            previousLat,
            previousLon,
            currentLocation.getLatitude(),
            currentLocation.getLongitude(),
            distance
    );

    boolean moved = previousTime == 0 || distance[0] >= 10;
    boolean elapsed = now - previousTime >= 300000;

    if (!moved && !elapsed) {
        return;
    }

    historySaving = true;

    saveSignalToHistory(
            currentDbm,
            currentLocation.getLatitude(),
            currentLocation.getLongitude()
    );

    p.edit()
            .putLong("auto_time", now)
            .putLong(
                    "auto_lat",
                    Double.doubleToLongBits(
                            currentLocation.getLatitude()
                    )
            )
            .putLong(
                    "auto_lon",
                    Double.doubleToLongBits(
                            currentLocation.getLongitude()
                    )
            )
            .apply();

    historySaving = false;

    if (currentTab == 3) {
        refreshHistory();
    }

    if (currentTab == 2) {
        refreshMap();
    }
}

@SuppressWarnings("deprecation")
private void startSignalListener() {
    if (ActivityCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
    ) != PackageManager.PERMISSION_GRANTED) {
        return;
    }

    try {
        telephony.listen(new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(
                    SignalStrength strength) {

                super.onSignalStrengthsChanged(strength);

                int dbm = -120;

                try {
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        for (CellSignalStrength cell :
                                strength.getCellSignalStrengths()) {

                            int candidate = cell.getDbm();

                            if (candidate < 0
                                    && candidate > -200) {
                                dbm = candidate;
                                break;
                            }
                        }
                    } else {
                        int asu = strength.getGsmSignalStrength();

                        if (asu >= 0 && asu <= 31) {
                            dbm = -113 + 2 * asu;
                        }
                    }
                } catch (Exception ignored) {
                }

                currentDbm = dbm;

                runOnUiThread(() -> {
                    updateLiveScreen();
                    maybeSaveAutomaticReading();
                });
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

    } catch (Exception ignored) {
    }
}

@Override
public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults) {

    super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
    );

    if (requestCode == PERMISSION_CODE) {
        startMonitoring();
        updateLiveScreen();
    }
}

@Override
protected void onResume() {
    super.onResume();

    if (map != null) {
        map.onResume();
    }
}

@Override
protected void onPause() {
    if (map != null) {
        map.onPause();
    }

    super.onPause();
}

@Override
protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);

    if (locationCallback != null) {
        LocationServices.getFusedLocationProviderClient(this)
                .removeLocationUpdates(locationCallback);
    }

    try {
        if (telephony != null) {
            telephony.listen(
                    null,
                    PhoneStateListener.LISTEN_NONE
            );
        }
    } catch (Exception ignored) {
    }

    super.onDestroy();
}

}