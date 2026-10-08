package com.mbpsmeter.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView current;
    private TextView best;
    private TextView arrow;
    private TextView history;

    private Button find;
    private Button navigate;
    private Button clearHistory;

    private MapView map;

    private int bestSignal = -120;
    private GeoPoint bestPoint = null;

    private GeoPoint currentPoint = null;
    private int currentDbm = -100;

    private FusedLocationProviderClient fusedClient;
    private TelephonyManager tm;

    private SharedPreferences preferences;

    private static final String PREFS_NAME = "MbpsMeterPrefs";
    private static final String HISTORY_KEY = "signal_history";
    private static final String BEST_DBM_KEY = "best_dbm";
    private static final String BEST_LAT_KEY = "best_lat";
    private static final String BEST_LON_KEY = "best_lon";

    private long lastSavedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        current = findViewById(R.id.current);
        best = findViewById(R.id.best);
        arrow = findViewById(R.id.arrow);
        history = findViewById(R.id.history);

        find = findViewById(R.id.find);
        navigate = findViewById(R.id.navigate);
        clearHistory = findViewById(R.id.clearHistory);

        map = findViewById(R.id.map);

        // OSM MAP
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        // Zoom close enough to see approximately 100m area
        map.getController().setZoom(19.5);

        fusedClient =
                LocationServices.getFusedLocationProviderClient(this);

        tm =
                (TelephonyManager)
                        getSystemService(TELEPHONY_SERVICE);

        preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        // Restore previous best signal
        loadSavedBest();

        // Restore previous signal history
        loadHistory();

        checkPermission();

        // FIND BETTER SIGNAL
        find.setOnClickListener(v -> {

            bestSignal = -120;
            bestPoint = null;

            best.setText("Best: - dBm");
            arrow.setText("SEARCHING...");

            redrawNearbyHistory();

        });

        // NAVIGATE TO BEST LOCATION
        navigate.setOnClickListener(v -> {

            if (bestPoint == null) {
                arrow.setText("No best location yet");
                return;
            }

            String uri =
                    "google.navigation:q="
                            + bestPoint.getLatitude()
                            + ","
                            + bestPoint.getLongitude();

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(uri)
                    );

            intent.setPackage(
                    "com.google.android.apps.maps"
            );

            try {

                startActivity(intent);

            } catch (Exception e) {

                String webUrl =
                        "https://www.google.com/maps/dir/?api=1"
                                + "&destination="
                                + bestPoint.getLatitude()
                                + ","
                                + bestPoint.getLongitude();

                startActivity(
                        new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(webUrl)
                        )
                );
            }
        });

        // CLEAR ALL HISTORY
        clearHistory.setOnClickListener(v -> {

            preferences.edit()
                    .remove(HISTORY_KEY)
                    .remove(BEST_DBM_KEY)
                    .remove(BEST_LAT_KEY)
                    .remove(BEST_LON_KEY)
                    .apply();

            bestSignal = -120;
            bestPoint = null;

            history.setText(
                    "No saved signal history."
            );

            best.setText(
                    "Best: - dBm"
            );

            arrow.setText(
                    "SEARCHING..."
            );

            map.getOverlays().clear();

            map.invalidate();
        });
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    private void checkPermission() {

        boolean locationGranted =
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        boolean phoneGranted =
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED;

        if (!locationGranted || !phoneGranted) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                    },
                    101
            );

        } else {

            startAll();
        }
    }

    private void startAll() {

        startLocation();
        startSignal();
    }

    // =========================================================
    // LOCATION
    // =========================================================

    private void startLocation() {

        if (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        LocationRequest request =
                LocationRequest.create();

        request.setInterval(2000);
        request.setFastestInterval(1000);
        request.setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY
        );

        fusedClient.requestLocationUpdates(
                request,
                new LocationCallback() {

                    @Override
                    public void onLocationResult(
                            LocationResult result
                    ) {

                        if (
                                result == null ||
                                result.getLastLocation() == null
                        ) {
                            return;
                        }

                        Location location =
                                result.getLastLocation();

                        currentPoint =
                                new GeoPoint(
                                        location.getLatitude(),
                                        location.getLongitude()
                                );

                        // Center map on current location
                        map.getController()
                                .animateTo(currentPoint);

                        // Show saved signals within 100m
                        redrawNearbyHistory();

                        // Save current signal reading
                        long now =
                                System.currentTimeMillis();

                        if (
                                now - lastSavedTime
                                        >= 10000
                        ) {

                            saveSignal(
                                    currentPoint,
                                    currentDbm
                            );

                            lastSavedTime = now;
                        }

                        // Add/update best signal
                        updateBestSignal(
                                currentPoint,
                                currentDbm
                        );
                    }
                },
                getMainLooper()
        );
    }

    // =========================================================
    // SIGNAL
    // =========================================================

    private void startSignal() {

        if (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        tm.listen(
                new PhoneStateListener() {

                    @Override
                    public void onSignalStrengthsChanged(
                            SignalStrength signalStrength
                    ) {

                        int dbm = -100;

                        try {

                            if (
                                    signalStrength
                                            .getCellSignalStrengths()
                                            .size() > 0
                            ) {

                                dbm =
                                        signalStrength
                                                .getCellSignalStrengths()
                                                .get(0)
                                                .getDbm();
                            }

                        } catch (Exception ignored) {
                        }

                        currentDbm = dbm;

                        String category =
                                getSignalCategory(dbm);

                        runOnUiThread(() -> {

                            current.setText(
                                    "Current: "
                                            + dbm
                                            + " dBm • "
                                            + category
                            );
                        });
                    }

                },
                PhoneStateListener
                        .LISTEN_SIGNAL_STRENGTHS
        );
    }

    // =========================================================
    // SIGNAL CATEGORY
    // =========================================================

    private String getSignalCategory(int dbm) {

        if (dbm >= -85) {
            return "BEST";
        }

        if (dbm >= -100) {
            return "GOOD";
        }

        return "WEAK";
    }

    // =========================================================
    // SAVE SIGNAL
    // =========================================================

    private void saveSignal(
            GeoPoint point,
            int dbm
    ) {

        if (point == null) {
            return;
        }

        try {

            JSONArray array =
                    new JSONArray(
                            preferences.getString(
                                    HISTORY_KEY,
                                    "[]"
                            )
                    );

            JSONObject item =
                    new JSONObject();

            String time =
                    new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm:ss a",
                            Locale.getDefault()
                    ).format(
                            new Date()
                    );

            item.put(
                    "dbm",
                    dbm
            );

            item.put(
                    "category",
                    getSignalCategory(dbm)
            );

            item.put(
                    "time",
                    time
            );

            item.put(
                    "latitude",
                    point.getLatitude()
            );

            item.put(
                    "longitude",
                    point.getLongitude()
            );

            array.put(item);

            // Keep maximum 200 readings
            while (array.length() > 200) {
                array.remove(0);
            }

            preferences.edit()
                    .putString(
                            HISTORY_KEY,
                            array.toString()
                    )
                    .apply();

            loadHistoryText();

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // LOAD HISTORY
    // =========================================================

    private void loadHistory() {

        loadHistoryText();

        // Best saved location
        if (
                preferences.contains(
                        BEST_DBM_KEY
                )
        ) {

            loadSavedBest();
        }
    }

    private void loadHistoryText() {

        try {

            JSONArray array =
                    new JSONArray(
                            preferences.getString(
                                    HISTORY_KEY,
                                    "[]"
                            )
                    );

            if (array.length() == 0) {

                history.setText(
                        "No saved signal history."
                );

                return;
            }

            StringBuilder result =
                    new StringBuilder();

            result.append(
                    "SAVED SIGNALS\n\n"
            );

            // Newest first
            for (
                    int i = array.length() - 1;
                    i >= 0;
                    i--
            ) {

                JSONObject item =
                        array.getJSONObject(i);

                int dbm =
                        item.getInt("dbm");

                String category =
                        item.getString(
                                "category"
                        );

                String time =
                        item.getString(
                                "time"
                        );

                result.append(
                        category
                                + "  "
                                + dbm
                                + " dBm\n"
                );

                result.append(
                        time
                                + "\n\n"
                );
            }

            history.setText(
                    result.toString()
            );

        } catch (Exception e) {

            history.setText(
                    "Unable to load signal history."
            );
        }
    }

    // =========================================================
    // DRAW SAVED SIGNALS WITHIN 100 METERS
    // =========================================================

    private void redrawNearbyHistory() {

        if (map == null) {
            return;
        }

        // Remove old circles
        map.getOverlays().clear();

        if (currentPoint == null) {

            map.invalidate();
            return;
        }

        try {

            JSONArray array =
                    new JSONArray(
                            preferences.getString(
                                    HISTORY_KEY,
                                    "[]"
                            )
                    );

            for (
                    int i = 0;
                    i < array.length();
                    i++
            ) {

                JSONObject item =
                        array.getJSONObject(i);

                double latitude =
                        item.getDouble(
                                "latitude"
                        );

                double longitude =
                        item.getDouble(
                                "longitude"
                        );

                int dbm =
                        item.getInt("dbm");

                GeoPoint savedPoint =
                        new GeoPoint(
                                latitude,
                                longitude
                        );

                float[] distance =
                        new float[1];

                Location.distanceBetween(
                        currentPoint.getLatitude(),
                        currentPoint.getLongitude(),
                        savedPoint.getLatitude(),
                        savedPoint.getLongitude(),
                        distance
                );

                // ONLY show readings within 100 meters
                if (distance[0] <= 100) {

                    addSignalCircle(
                            savedPoint,
                            dbm
                    );
                }
            }

            // Current location marker
            addSignalCircle(
                    currentPoint,
                    currentDbm
            );

            map.invalidate();

        } catch (Exception ignored) {

            map.invalidate();
        }
    }

    // =========================================================
    // DRAW SIGNAL CIRCLE
    // =========================================================

    private void addSignalCircle(
            GeoPoint point,
            int dbm
    ) {

        if (point == null) {
            return;
        }

        int fillColor;

        if (dbm >= -85) {

            // BEST
            fillColor = 0x8022C55E;

        } else if (dbm >= -100) {

            // GOOD
            fillColor = 0x80EAB308;

        } else {

            // WEAK
            fillColor = 0x80EF4444;
        }

        Polygon circle =
                new Polygon(map);

        circle.setPoints(
                Polygon.pointsAsCircle(
                        point,
                        5
                )
        );

        circle.setFillColor(
                fillColor
        );

        circle.setStrokeWidth(0);

        map.getOverlays().add(
                circle
        );
    }

    // =========================================================
    // BEST SIGNAL
    // =========================================================

    private void updateBestSignal(
            GeoPoint point,
            int dbm
    ) {

        if (point == null) {
            return;
        }

        if (dbm > bestSignal) {

            bestSignal = dbm;
            bestPoint = point;

            saveBest(
                    dbm,
                    point
            );

            String category =
                    getSignalCategory(dbm);

            runOnUiThread(() -> {

                best.setText(
                        "Best: "
                                + dbm
                                + " dBm • "
                                + category
                );

                arrow.setText(
                        "BEST SIGNAL FOUND"
                );
            });
        }
    }

    // =========================================================
    // SAVE BEST
    // =========================================================

    private void saveBest(
            int dbm,
            GeoPoint point
    ) {

        preferences.edit()
                .putInt(
                        BEST_DBM_KEY,
                        dbm
                )
                .putString(
                        BEST_LAT_KEY,
                        String.valueOf(
                                point.getLatitude()
                        )
                )
                .putString(
                        BEST_LON_KEY,
                        String.valueOf(
                                point.getLongitude()
                        )
                )
                .apply();
    }

    // =========================================================
    // LOAD BEST
    // =========================================================

    private void loadSavedBest() {

        if (
                !preferences.contains(
                        BEST_DBM_KEY
                )
        ) {
            return;
        }

        bestSignal =
                preferences.getInt(
                        BEST_DBM_KEY,
                        -120
                );

        String lat =
                preferences.getString(
                        BEST_LAT_KEY,
                        null
                );

        String lon =
                preferences.getString(
                        BEST_LON_KEY,
                        null
                );

        try {

            if (
                    lat != null &&
                    lon != null
            ) {

                bestPoint =
                        new GeoPoint(
                                Double.parseDouble(lat),
                                Double.parseDouble(lon)
                        );
            }

            best.setText(
                    "Best: "
                            + bestSignal
                            + " dBm • "
                            + getSignalCategory(
                                    bestSignal
                            )
            );

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // PERMISSION RESULT
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 101) {

            boolean locationGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED;

            boolean phoneGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION