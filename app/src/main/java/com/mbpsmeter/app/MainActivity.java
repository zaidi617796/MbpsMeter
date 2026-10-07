package com.mbpsmeter.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.tabs.TabLayout;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 101;

    // UI
    private TextView current;
    private TextView best;
    private TextView status;
    private TextView distance;
    private TextView steps;
    private TextView direction;
    private TextView network;

    private View livePanel;
    private View findPanel;
    private View mapPanel;

    private View mapCard;
    private MapView map;
    private TabLayout tabs;

    // Location
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;

    private GeoPoint currentPoint;
    private GeoPoint bestPoint;

    // Signal
    private TelephonyManager tm;
    private int currentDbm = -120;
    private int bestSignal = -120;

    // Scanning
    private boolean findingBest = false;

    // Map
    private Marker currentMarker;
    private Marker bestMarker;

    private final List<Polygon> signalZones =
            new ArrayList<>();

    private Polyline directionLine;

    // Handler
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable navigationUpdater =
            new Runnable() {

                @Override
                public void run() {

                    updateNavigation();

                    handler.postDelayed(
                            this,
                            1000
                    );
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        /*
         * IMPORTANT:
         * osmdroid configuration must be initialized
         * before creating MapView.
         */
        Configuration.getInstance()
                .load(
                        this,
                        getSharedPreferences(
                                "osmdroid",
                                MODE_PRIVATE
                        )
                );

        Configuration.getInstance()
                .setUserAgentValue(
                        getPackageName()
                );

        setContentView(
                R.layout.activity_main
        );

        initializeViews();

        setupMap();

        setupTabs();

        fusedClient =
                LocationServices
                        .getFusedLocationProviderClient(
                                this
                        );

        tm =
                (TelephonyManager)
                        getSystemService(
                                Context.TELEPHONY_SERVICE
                        );

        checkPermissions();

        handler.post(
                navigationUpdater
        );
    }

    // =========================================================
    // INITIALIZE UI
    // =========================================================

    private void initializeViews() {

        current =
                findViewById(R.id.current);

        best =
                findViewById(R.id.best);

        status =
                findViewById(R.id.status);

        distance =
                findViewById(R.id.distance);

        steps =
                findViewById(R.id.steps);

        direction =
                findViewById(R.id.direction);

        network =
                findViewById(R.id.network);

        livePanel =
                findViewById(R.id.tabLive);

        findPanel =
                findViewById(R.id.tabFind);

        mapPanel =
                findViewById(R.id.tabMap);

        mapCard =
                findViewById(R.id.mapCard);

        map =
                findViewById(R.id.map);

        tabs =
                findViewById(R.id.tabs);
    }

    // =========================================================
    // MAP
    // =========================================================

    private void setupMap() {

        /*
         * OpenStreetMap Mapnik
         */
        map.setTileSource(
                TileSourceFactory.MAPNIK
        );

        map.setUseDataConnection(
                true
        );

        map.setMultiTouchControls(
                true
        );

        map.getController()
                .setZoom(17.0);

        /*
         * Default center.
         * Karachi is only a temporary fallback.
         * It will automatically move to the user's
         * actual GPS location after permission.
         */
        map.getController()
                .setCenter(
                        new GeoPoint(
                                24.8607,
                                67.0011
                        )
                );

        /*
         * Direction line
         */
        directionLine =
                new Polyline();

        directionLine.setWidth(
                7f
        );

        directionLine.setColor(
                0xFF22C55E
        );

        map.getOverlays()
                .add(directionLine);

        /*
         * Tap small map to expand.
         */
        mapCard.setOnClickListener(
                v -> expandMap()
        );
    }

    // =========================================================
    // TABS
    // =========================================================

    private void setupTabs() {

        /*
         * Initially LIVE tab
         */
        showTab(0);

        tabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {

                    @Override
                    public void onTabSelected(
                            TabLayout.Tab tab
                    ) {

                        showTab(
                                tab.getPosition()
                        );
                    }

                    @Override
                    public void onTabUnselected(
                            TabLayout.Tab tab
                    ) {
                    }

                    @Override
                    public void onTabReselected(
                            TabLayout.Tab tab
                    ) {

                        /*
                         * MAP tab ko dobara press karne par
                         * large map.
                         */
                        if (tab.getPosition() == 2) {
                            expandMap();
                        }
                    }
                }
        );
    }

    private void showTab(
            int position
    ) {

        livePanel.setVisibility(
                View.GONE
        );

        findPanel.setVisibility(
                View.GONE
        );

        mapPanel.setVisibility(
                View.GONE
        );

        /*
         * Small map is hidden in LIVE.
         */
        mapCard.setVisibility(
                View.GONE
        );

        if (position == 0) {

            livePanel.setVisibility(
                    View.VISIBLE
            );

            findingBest = false;

        } else if (position == 1) {

            findPanel.setVisibility(
                    View.VISIBLE
            );

            mapCard.setVisibility(
                    View.VISIBLE
            );

            findingBest = true;

            status.setText(
                    "SEARCHING FOR BEST SIGNAL..."
            );

        } else if (position == 2) {

            mapPanel.setVisibility(
                    View.VISIBLE
            );

            mapCard.setVisibility(
                    View.VISIBLE
            );

            findingBest = true;

            expandMap();
        }
    }

    // =========================================================
    // EXPAND MAP
    // =========================================================

    private void expandMap() {

        ViewGroup.LayoutParams params =
                mapCard.getLayoutParams();

        params.height =
                ViewGroup.LayoutParams.MATCH_PARENT;

        params.width =
                ViewGroup.LayoutParams.MATCH_PARENT;

        mapCard.setLayoutParams(
                params
        );

        mapCard.setOnClickListener(
                v -> shrinkMap()
        );

        map.getController()
                .setZoom(18.0);

        map.invalidate();
    }

    private void shrinkMap() {

        ViewGroup.LayoutParams params =
                mapCard.getLayoutParams();

        params.height =
                dpToPx(230);

        params.width =
                ViewGroup.LayoutParams.MATCH_PARENT;

        mapCard.setLayoutParams(
                params
        );

        mapCard.setOnClickListener(
                v -> expandMap()
        );

        map.invalidate();
    }

    private int dpToPx(
            int dp
    ) {

        return Math.round(
                dp *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    private void checkPermissions() {

        List<String> permissions =
                new ArrayList<>();

        if (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
        ) {

            permissions.add(
                    Manifest.permission.ACCESS_FINE_LOCATION
            );
        }

        if (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_STATE
                )
                != PackageManager.PERMISSION_GRANTED
        ) {

            permissions.add(
                    Manifest.permission.READ_PHONE_STATE
            );
        }

        if (
                android.os.Build.VERSION.SDK_INT >= 29
        ) {

            if (
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACTIVITY_RECOGNITION
                    )
                    != PackageManager.PERMISSION_GRANTED
            ) {

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

            startAll();
        }
    }

    // =========================================================
    // START ALL
    // =========================================================

    private void startAll() {

        startLocation();

        startSignal();

        startStepInformation();
    }

    // =========================================================
    // LOCATION
    // =========================================================

    private void startLocation() {

        if (
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        LocationRequest request =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        2000
                )
                        .setMinUpdateIntervalMillis(
                                1000
                        )
                        .setMinUpdateDistanceMeters(
                                1
                        )
                        .build();

        locationCallback =
                new LocationCallback() {

                    @Override
                    public void onLocationResult(
                            @NonNull LocationResult result
                    ) {

                        if (
                                result.getLastLocation()
                                        == null
                        ) {
                            return;
                        }

                        android.location.Location location =
                                result.getLastLocation();

                        currentPoint =
                                new GeoPoint(
                                        location.getLatitude(),
                                        location.getLongitude()
                                );

                        updateCurrentMarker();

                        /*
                         * Move map to current location
                         * only when user has a location.
                         */
                        if (
                                map.getZoomLevelDouble()
                                        < 5
                        ) {

                            map.getController()
                                    .setZoom(
                                            18.0
                                    );
                        }

                        /*
                         * During FIND BEST, save a signal zone.
                         */
                        if (findingBest) {

                            addSignalZone(
                                    currentPoint,
                                    currentDbm
                            );
                        }

                        updateNavigation();
                    }
                };

        fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    // =========================================================
    // CURRENT LOCATION MARKER
    // =========================================================

    private void updateCurrentMarker() {

        if (currentPoint == null) {
            return;
        }

        if (currentMarker == null) {

            currentMarker =
                    new Marker(map);

            currentMarker.setTitle(
                    "YOUR LOCATION"
            );

            map.getOverlays()
                    .add(currentMarker);
        }

        currentMarker.setPosition(
                currentPoint
        );

        map.invalidate();
    }

    // =========================================================
    // SIGNAL ZONE
    // =========================================================

    private void addSignalZone(
            GeoPoint point,
            int dbm
    ) {

        if (point == null) {
            return;
        }

        /*
         * Avoid adding thousands of circles.
         */
        if (signalZones.size() > 80) {

            Polygon old =
                    signalZones.remove(0);

            map.getOverlays()
                    .remove(old);
        }

        int color;

        if (dbm >= -85) {

            // BEST
            color =
                    0x7034D399;

        } else if (dbm >= -100) {

            // GOOD
            color =
                    0x70FACC15;

        } else {

            // WEAK
            color =
                    0x70EF4444;
        }

        Polygon circle =
                new Polygon(map);

        circle.setPoints(
                Polygon.pointsAsCircle(
                        point,
                        7
                )
        );

        circle.setFillColor(
                color
        );

        circle.setStrokeWidth(
                1
        );

        circle.setStrokeColor(
                color
        );

        map.getOverlays()
                .add(circle);

        signalZones.add(
                circle
        );

        /*
         * Find the strongest signal.
         */
        if (dbm > bestSignal) {

            bestSignal = dbm;

            bestPoint =
                    new GeoPoint(
                            point.getLatitude(),
                            point.getLongitude()
                    );

            updateBestMarker();
        }

        map.invalidate();
    }

    // =========================================================
    // BEST SIGNAL MARKER
    // =========================================================

    private void updateBestMarker() {

        if (bestPoint == null) {
            return;
        }

        if (bestMarker == null) {

            bestMarker =
                    new Marker(map);

            bestMarker.setTitle(
                    "BEST SIGNAL"
            );

            map.getOverlays()
                    .add(bestMarker);
        }

        bestMarker.setPosition(
                bestPoint
        );

        bestMarker.setTitle(
                "BEST SIGNAL: "
                        + bestSignal
                        + " dBm"
        );

        best.setText(
                "Best: "
                        + bestSignal
                        + " dBm"
        );

        status.setText(
                "BEST SIGNAL FOUND"
        );

        map.invalidate();
    }

    // =========================================================
    // SIGNAL LISTENER
    // =========================================================

    private void startSignal() {

        try {

            if (
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_PHONE_STATE
                    )
                    != PackageManager.PERMISSION_GRANTED
            ) {
                return;
            }

            tm.listen(
                    new PhoneStateListener() {

                        @Override
                        public void onSignalStrengthsChanged(
                                SignalStrength signalStrength
                        ) {

                            super.onSignalStrengthsChanged(
                                    signalStrength
                            );

                            int dbm =
                                    -120;

                            try {

                                if (
                                        android.os.Build.VERSION.SDK_INT
                                                >= 29
                                ) {

                                    if (
                                            signalStrength
                                                    .getCellSignalStrengths()
                                                    .size()
                                                    > 0
                                    ) {

                                        dbm =
                                                signalStrength
                                                        .getCellSignalStrengths()
                                                        .get(0)
                                                        .getDbm();
                                    }
                                }

                            } catch (Exception ignored) {
                            }

                            currentDbm =
                                    dbm;

                            int finalDbm =
                                    dbm;

                            runOnUiThread(
                                    () -> {

                                        current.setText(
                                                "Current: "
                                                        + finalDbm
                                                        + " dBm"
                                        );

                                        updateSignalQuality(
                                                finalDbm
                                        );
                                    }
                            );
                        }
                    },
                    PhoneStateListener
                            .LISTEN_SIGNAL_STRENGTHS
            );

        } catch (Exception e) {

            current.setText(
                    "Current: unavailable"
            );
        }
    }

    // =========================================================
    // SIGNAL QUALITY
    // =========================================================

    private void updateSignalQuality(
            int dbm
    ) {

        if (dbm >= -85) {

            network.setText(
                    "SIGNAL: BEST"
            );

        } else if (dbm >= -100) {

            network.setText(
                    "SIGNAL: GOOD"
            );

        } else if (dbm >= -110) {

            network.setText(
                    "SIGNAL: FAIR"
            );

        } else {

            network.setText(
                    "SIGNAL: WEAK"
            );
        }
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private void updateNavigation() {

        if (
                currentPoint == null
                        || bestPoint == null
        ) {

            distance.setText(
                    "Distance: searching..."
            );

            steps.setText(
                    "Steps: --"
            );

            direction.setText(
                    "↑"
            );

            return;
        }

        float[] results =
                new float[3];

        android.location.Location.distanceBetween(
                currentPoint.getLatitude(),
                currentPoint.getLongitude(),
                bestPoint.getLatitude(),
                bestPoint.getLongitude(),
                results
        );

        float meters =
                results[0];

        float bearing =
                results[1];

        /*
         * Average walking step.
         */
        int estimatedSteps =
                Math.max(
                        0,
                        Math.round(
                                meters / 0.75f
                        )
                );

        if (meters <= 10) {

            distance.setText(
                    "🎯 BEST SIGNAL AREA"
            );

            steps.setText(
                    "You are here"
            );

            direction.setText(
                    "✓"
            );

            status.setText(
                    "BEST SIGNAL AREA REACHED"
            );

        } else {

            distance.setText(
                    String.format(
                            Locale.US,
                            "%.0f meters",
                            meters
                    )
            );

            steps.setText(
                    "≈ "
                            + estimatedSteps
                            + " steps"
            );

            direction.setText(
                    getDirectionArrow(
                            bearing
                    )
            );

            status.setText(
                    "MOVE TOWARDS BEST SIGNAL"
            );
        }

        drawDirectionLine();
    }

    // =========================================================
    // DIRECTION ARROW
    // =========================================================

    private String getDirectionArrow(
            float bearing
    ) {

        float angle =
                (bearing + 360)
                        % 360;

        if (
                angle < 22.5
                        || angle >= 337.5
        ) {
            return "↑";
        }

        if (angle < 67.5) {
            return "↗";
        }

        if (angle < 112.5) {
            return "→";
        }

        if (angle < 157.5) {
            return "↘";
        }

        if (angle < 202.5) {
            return "↓";
        }

        if (angle < 247.5) {
            return "↙";
        }

        if (angle < 292.5) {
            return "←";
        }

        return "↖";
    }

    // =========================================================
    // DRAW NAVIGATION LINE
    // =========================================================

    private void drawDirectionLine() {

        if (
                currentPoint == null
                        || bestPoint == null
        ) {
            return;
        }

        List<GeoPoint> points =
                new ArrayList<>();

        points.add(
                currentPoint
        );

        points.add(
                bestPoint
        );

        directionLine.setPoints(
                points
        );

        map.invalidate();
    }

    // =========================================================
    // STEP INFORMATION
    // =========================================================

    private void startStepInformation() {

        /*
         * Actual phone step sensor will be used
         * in the next navigation layer.
         *
         * Distance-based estimated steps are already
         * displayed above.
         */
    }

    // =========================================================
    // PERMISSION RESULT
    // =========================================================

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

        if (
                requestCode
                        == REQUEST_PERMISSIONS
        ) {

            boolean locationGranted =
                    ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    )
                            == PackageManager.PERMISSION_GRANTED;

            if (locationGranted) {

                startAll();
            }
        }
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

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

        handler.removeCallbacks(
                navigationUpdater
        );

        if (
                fusedClient != null
                        && locationCallback != null
        ) {

            fusedClient.removeLocationUpdates(
                    locationCallback
            );
        }

        if (tm != null) {

            tm.listen(
                    null,
                    PhoneStateListener
                            .LISTEN_NONE
            );
        }

        super.onDestroy();
    }
}