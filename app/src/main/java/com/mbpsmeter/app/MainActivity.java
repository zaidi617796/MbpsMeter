package com.mbpsmeter.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.SignalStrength;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements SensorEventListener {

    private static final int REQUEST_PERMISSIONS = 1001;

    private MapView map;
    private FusedLocationProviderClient locationClient;

    private TelephonyManager telephonyManager;

    private TextView currentSignal;
    private TextView signalQuality;
    private TextView network;
    private TextView distanceText;
    private TextView stepsText;
    private TextView directionText;
    private TextView navigationStatus;
    private TextView downloadSpeed;
    private TextView uploadSpeed;

    private Button startButton;

    private Marker currentMarker;
    private Marker bestMarker;

    private Polyline navigationLine;

    private Location currentLocation;

    private boolean scanning = false;

    private int currentDbm = -120;
    private int bestDbm = -120;

    private double bestLatitude = 0;
    private double bestLongitude = 0;

    private SensorManager sensorManager;
    private Sensor stepSensor;

    private float startingSteps = -1;
    private float currentSteps = 0;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final List<SignalPoint> signalPoints =
            new ArrayList<>();

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {

            if (scanning && currentLocation != null) {

                saveSignalPoint();

                updateNavigation();

                updateSpeedDisplay();

            }

            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Configuration.getInstance()
                .setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        initializeViews();

        setupMap();

        setupSensors();

        locationClient =
                LocationServices.getFusedLocationProviderClient(this);

        telephonyManager =
                (TelephonyManager) getSystemService(
                        Context.TELEPHONY_SERVICE
                );

        startButton.setOnClickListener(v -> {

            if (!scanning) {

                scanning = true;

                startButton.setText("STOP SIGNAL SCAN");

                navigationStatus.setText(
                        "Scanning signal... move around"
                );

                handler.post(scanRunnable);

            } else {

                scanning = false;

                startButton.setText("START SIGNAL SCAN");

                navigationStatus.setText(
                        "Signal scan paused"
                );
            }
        });

        requestPermissionsIfNeeded();
    }

    private void initializeViews() {

        map = findViewById(R.id.map);

        currentSignal = findViewById(R.id.currentSignal);
        signalQuality = findViewById(R.id.signalQuality);
        network = findViewById(R.id.network);

        distanceText = findViewById(R.id.distanceText);
        stepsText = findViewById(R.id.stepsText);
        directionText = findViewById(R.id.directionText);

        navigationStatus =
                findViewById(R.id.navigationStatus);

        downloadSpeed =
                findViewById(R.id.downloadSpeed);

        uploadSpeed =
                findViewById(R.id.uploadSpeed);

        startButton =
                findViewById(R.id.startButton);
    }

    private void setupMap() {

        map.setTileSource(
                TileSourceFactory.MAPNIK
        );

        map.setMultiTouchControls(true);

        map.getController().setZoom(17.0);

        navigationLine = new Polyline();

        navigationLine.setWidth(6f);

        map.getOverlays().add(navigationLine);
    }

    private void setupSensors() {

        sensorManager =
                (SensorManager) getSystemService(
                        Context.SENSOR_SERVICE
                );

        stepSensor =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_STEP_COUNTER
                );

        if (stepSensor != null) {

            sensorManager.registerListener(
                    this,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
        }
    }

    private void requestPermissionsIfNeeded() {

        List<String> permissions =
                new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            permissions.add(
                    Manifest.permission.ACCESS_FINE_LOCATION
            );
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
        ) != PackageManager.PERMISSION_GRANTED) {

            permissions.add(
                    Manifest.permission.READ_PHONE_STATE
            );
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED) {

                permissions.add(
                        Manifest.permission.ACTIVITY_RECOGNITION
                );
            }
        }

        if (!permissions.isEmpty()) {

            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(
                            new String[0]
                    ),
                    REQUEST_PERMISSIONS
            );

        } else {

            startLocationUpdates();
            startSignalListener();
        }
    }

    private void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        2000
                )
                        .setMinUpdateIntervalMillis(1000)
                        .setMinUpdateDistanceMeters(1)
                        .build();

        locationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    private final LocationCallback locationCallback =
            new LocationCallback() {

                @Override
                public void onLocationResult(
                        @NonNull LocationResult result) {

                    Location location =
                            result.getLastLocation();

                    if (location == null) {
                        return;
                    }

                    currentLocation = location;

                    updateCurrentMarker(location);

                    updateNavigation();

                    updateSpeedDisplay();
                }
            };

    private void updateCurrentMarker(
            Location location
    ) {

        GeoPoint point =
                new GeoPoint(
                        location.getLatitude(),
                        location.getLongitude()
                );

        if (currentMarker == null) {

            currentMarker = new Marker(map);

            currentMarker.setTitle(
                    "Your location"
            );

            map.getOverlays()
                    .add(currentMarker);
        }

        currentMarker.setPosition(point);

        map.getController()
                .animateTo(point);

        map.invalidate();
    }

    private void startSignalListener() {

        try {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            telephonyManager.listen(
                    new android.telephony.PhoneStateListener() {

                        @Override
                        public void onSignalStrengthsChanged(
                                SignalStrength signalStrength
                        ) {

                            super.onSignalStrengthsChanged(
                                    signalStrength
                            );

                            readSignal(signalStrength);
                        }

                    },
                    android.telephony.PhoneStateListener
                            .LISTEN_SIGNAL_STRENGTHS
            );

        } catch (Exception e) {

            currentSignal.setText(
                    "Signal: unavailable"
            );
        }
    }

    private void readSignal(
            SignalStrength signalStrength
    ) {

        int dbm = -120;

        try {

            if (android.os.Build.VERSION.SDK_INT >= 29) {

                List<CellSignalStrength> strengths =
                        signalStrength
                                .getCellSignalStrengths();

                if (!strengths.isEmpty()) {

                    dbm =
                            strengths.get(0)
                                    .getDbm();
                }

            }

        } catch (Exception ignored) {
        }

        currentDbm = dbm;

        final int finalDbm = dbm;

        runOnUiThread(() -> {

            currentSignal.setText(
                    "Signal: "
                            + finalDbm
                            + " dBm"
            );

            signalQuality.setText(
                    getSignalQuality(finalDbm)
            );

            updateSpeedDisplay();
        });
    }

    private String getSignalQuality(int dbm) {

        if (dbm >= -80) {
            return "BEST";
        }

        if (dbm >= -95) {
            return "GOOD";
        }

        if (dbm >= -105) {
            return "FAIR";
        }

        return "WEAK";
    }

    private void saveSignalPoint() {

        if (currentLocation == null) {
            return;
        }

        SignalPoint point =
                new SignalPoint(
                        currentLocation.getLatitude(),
                        currentLocation.getLongitude(),
                        currentDbm
                );

        signalPoints.add(point);

        if (currentDbm > bestDbm) {

            bestDbm = currentDbm;

            bestLatitude =
                    currentLocation.getLatitude();

            bestLongitude =
                    currentLocation.getLongitude();

            showBestMarker();

            navigationStatus.setText(
                    "NEW BEST SIGNAL FOUND"
            );
        }

        drawSignalPoints();

        updateNavigation();
    }

    private void showBestMarker() {

        GeoPoint point =
                new GeoPoint(
                        bestLatitude,
                        bestLongitude
                );

        if (bestMarker == null) {

            bestMarker = new Marker(map);

            bestMarker.setTitle(
                    "BEST SIGNAL: "
                            + bestDbm
                            + " dBm"
            );

            map.getOverlays()
                    .add(bestMarker);

        } else {

            bestMarker.setTitle(
                    "BEST SIGNAL: "
                            + bestDbm
                            + " dBm"
            );
        }

        bestMarker.setPosition(point);

        map.invalidate();
    }

    private void drawSignalPoints() {

        /*
         * Remove old signal markers except
         * current and best.
         *
         * For V2 we keep the map lightweight.
         */

        map.invalidate();
    }

    private void updateNavigation() {

        if (currentLocation == null) {
            return;
        }

        if (bestLatitude == 0 &&
                bestLongitude == 0) {

            distanceText.setText(
                    "Best signal: scanning..."
            );

            stepsText.setText(
                    "Steps remaining: --"
            );

            return;
        }

        float[] result =
                new float[3];

        Location.distanceBetween(
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                bestLatitude,
                bestLongitude,
                result
        );

        float distance = result[0];

        float bearing = result[1];

        int estimatedSteps =
                Math.max(
                        0,
                        Math.round(distance / 0.75f)
                );

        distanceText.setText(
                String.format(
                        Locale.US,
                        "Best signal: %.0f m",
                        distance
                )
        );

        stepsText.setText(
                "Steps remaining: "
                        + estimatedSteps
        );

        directionText.setText(
                getDirectionArrow(bearing)
        );

        navigationStatus.setText(
                "Best: "
                        + bestDbm
                        + " dBm"
        );

        drawNavigationLine();
    }

    private String getDirectionArrow(
            float bearing
    ) {

        float normalized =
                (bearing + 360) % 360;

        if (normalized < 22.5 ||
                normalized >= 337.5) {

            return "↑";
        }

        if (normalized < 67.5) {
            return "↗";
        }

        if (normalized < 112.5) {
            return "→";
        }

        if (normalized < 157.5) {
            return "↘";
        }

        if (normalized < 202.5) {
            return "↓";
        }

        if (normalized < 247.5) {
            return "↙";
        }

        if (normalized < 292.5) {
            return "←";
        }

        return "↖";
    }

    private void drawNavigationLine() {

        if (currentLocation == null ||
                bestLatitude == 0) {
            return;
        }

        navigationLine
                .getPoints()
                .clear();

        navigationLine
                .getPoints()
                .add(
                        new GeoPoint(
                                currentLocation
                                        .getLatitude(),
                                currentLocation
                                        .getLongitude()
                        )
                );

        navigationLine
                .getPoints()
                .add(
                        new GeoPoint(
                                bestLatitude,
                                bestLongitude
                        )
                );

        map.invalidate();
    }

    private void updateSpeedDisplay() {

        /*
         * Placeholder live network estimate.
         *
         * The speed-test engine from V1 can be
         * connected here without changing the
         * map/navigation system.
         */

        if (downloadSpeed != null) {

            downloadSpeed.setText(
                    "-- Mbps"
            );
        }

        if (uploadSpeed != null) {

            uploadSpeed.setText(
                    "-- Mbps"
            );
        }
    }

    @Override
    public void onSensorChanged(
            SensorEvent event
    ) {

        if (event.sensor.getType()
                == Sensor.TYPE_STEP_COUNTER) {

            float totalSteps =
                    event.values[0];

            if (startingSteps < 0) {

                startingSteps =
                        totalSteps;
            }

            currentSteps =
                    totalSteps
                            - startingSteps;
        }
    }

    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode ==
                REQUEST_PERMISSIONS) {

            startLocationUpdates();

            startSignalListener();
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (map != null) {
            map.onResume();
        }

        if (sensorManager != null &&
                stepSensor != null) {

            sensorManager.registerListener(
                    this,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
        }
    }

    @Override
    protected void onPause() {

        if (map != null) {
            map.onPause();
        }

        if (sensorManager != null) {

            sensorManager.unregisterListener(
                    this
            );
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacks(
                scanRunnable
        );

        if (locationClient != null) {

            locationClient.removeLocationUpdates(
                    locationCallback
            );
        }

        if (telephonyManager != null) {

            telephonyManager.listen(
                    null,
                    android.telephony.PhoneStateListener
                            .LISTEN_NONE
            );
        }

        super.onDestroy();
    }

    private static class SignalPoint {

        double latitude;
        double longitude;
        int dbm;

        SignalPoint(
                double latitude,
                double longitude,
                int dbm
        ) {

            this.latitude = latitude;
            this.longitude = longitude;
            this.dbm = dbm;
        }
    }
}