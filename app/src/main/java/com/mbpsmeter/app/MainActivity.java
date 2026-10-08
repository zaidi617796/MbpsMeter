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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;
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
import android.widget.TextView;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 101;
    private TextView current, best, status, distance, steps, direction, network, arrow;
    private View livePanel, findPanel, mapPanel;
    private CardView mapCard;
    private MapView map;
    private TabLayout tabs;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private GeoPoint currentPoint, bestPoint;
    private TelephonyManager tm;
    private int currentDbm = -120, bestSignal = -120;
    private boolean findingBest = false;
    private Marker currentMarker, bestMarker;
    private final List<Polygon> signalZones = new ArrayList<>();
    private Polyline directionLine;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);
        current = findViewById(R.id.current);
        best = findViewById(R.id.best);
        status = findViewById(R.id.status);
        distance = findViewById(R.id.distance);
        steps = findViewById(R.id.steps);
        direction = findViewById(R.id.direction);
        network = findViewById(R.id.network);
        arrow = findViewById(R.id.arrow);
        livePanel = findViewById(R.id.tabLive);
        findPanel = findViewById(R.id.tabFind);
        mapPanel = findViewById(R.id.tabMap);
        mapCard = findViewById(R.id.mapCard);
        map = findViewById(R.id.map);
        tabs = findViewById(R.id.tabs);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setUseDataConnection(true);
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);
        map.getController().setCenter(new GeoPoint(24.8607, 67.0011));
        directionLine = new Polyline(); directionLine.setWidth(7f); directionLine.setColor(0xFF22C55E);
        map.getOverlays().add(directionLine);
        mapCard.setOnClickListener(v -> expandMap());

        showTab(0);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { if (tab.getPosition() == 2) expandMap(); }
        });

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        checkPermissions();
        handler.postDelayed(new Runnable() { @Override public void run() { updateNavigation(); handler.postDelayed(this, 1000); } }, 1000);
    }

    private void showTab(int position) {
        livePanel.setVisibility(View.GONE); findPanel.setVisibility(View.GONE); mapPanel.setVisibility(View.GONE);
        if (position == 0) { livePanel.setVisibility(View.VISIBLE); findingBest = false; }
        else if (position == 1) { findPanel.setVisibility(View.VISIBLE); findingBest = true; if(status!=null) status.setText("SEARCHING FOR BEST SIGNAL..."); }
        else if (position == 2) { mapPanel.setVisibility(View.VISIBLE); findingBest = true; expandMap(); }
    }
    private void expandMap() {
        ViewGroup.LayoutParams params = mapCard.getLayoutParams(); params.height = ViewGroup.LayoutParams.MATCH_PARENT; mapCard.setLayoutParams(params);
        mapCard.setOnClickListener(v -> shrinkMap()); map.getController().setZoom(18.0); map.invalidate();
    }
    private void shrinkMap() {
        ViewGroup.LayoutParams params = mapCard.getLayoutParams(); params.height = Math.round(230 * getResources().getDisplayMetrics().density);
        mapCard.setLayoutParams(params); mapCard.setOnClickListener(v -> expandMap()); map.invalidate();
    }
    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_PHONE_STATE);
        if (!perms.isEmpty()) ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), REQUEST_PERMISSIONS);
        else startAll();
    }
    private void startAll() { startLocation(); startSignal(); }
    private void startLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) return;
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).setMinUpdateIntervalMillis(1000).setMinUpdateDistanceMeters(1).build();
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() == null) return;
                android.location.Location loc = result.getLastLocation();
                currentPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                if (currentMarker == null) { currentMarker = new Marker(map); currentMarker.setTitle("YOUR LOCATION"); map.getOverlays().add(currentMarker); }
                currentMarker.setPosition(currentPoint);
                if (findingBest) addSignalZone(currentPoint, currentDbm);
                updateNavigation(); map.invalidate();
            }
        };
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }
    private void addSignalZone(GeoPoint point, int dbm) {
        if (point == null) return;
        if (signalZones.size() > 80) { Polygon old = signalZones.remove(0); map.getOverlays().remove(old); }
        int color = dbm >= -85? 0x7034D399 : dbm >= -100? 0x70FACC15 : 0x70EF4444;
        Polygon circle = new Polygon(map); circle.setPoints(Polygon.pointsAsCircle(point, 7)); circle.setFillColor(color); circle.setStrokeWidth(1); circle.setStrokeColor(color);
        map.getOverlays().add(circle); signalZones.add(circle);
        if (dbm > bestSignal) { bestSignal = dbm; bestPoint = new GeoPoint(point.getLatitude(), point.getLongitude()); if (bestMarker == null) { bestMarker = new Marker(map); map.getOverlays().add(bestMarker); } bestMarker.setPosition(bestPoint); bestMarker.setTitle("BEST: " + bestSignal + " dBm"); if(best!=null) best.setText("Best: " + bestSignal + " dBm"); if(status!=null) status.setText("BEST SIGNAL FOUND"); }
        map.invalidate();
    }
    private void startSignal() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED) return;
            tm.listen(new PhoneStateListener() {
                @Override public void onSignalStrengthsChanged(SignalStrength s) {
                    int dbm = -120;
                    try { if (s.getCellSignalStrengths().size() > 0) dbm = s.getCellSignalStrengths().get(0).getDbm(); } catch (Exception ignored) {}
                    currentDbm = dbm; int finalDbm = dbm;
                    runOnUiThread(() -> { if(current!=null) current.setText("Current: " + finalDbm + " dBm"); if(network!=null){ if(finalDbm >= -85) network.setText("SIGNAL: BEST"); else if(finalDbm >= -100) network.setText("SIGNAL: GOOD"); else network.setText("SIGNAL: WEAK"); } });
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        } catch (Exception e) { current.setText("Current: unavailable"); }
    }
    private void updateNavigation() {
        if (currentPoint == null || bestPoint == null) { if(distance!=null) distance.setText("Distance: searching..."); if(steps!=null) steps.setText("Steps: --"); return; }
        float[] results = new float[3];
        android.location.Location.distanceBetween(currentPoint.getLatitude(), currentPoint.getLongitude(), bestPoint.getLatitude(), bestPoint.getLongitude(), results);
        float meters = results[0];
        if (meters <= 10) { distance.setText("🎯 BEST SIGNAL AREA"); steps.setText("You are here"); direction.setText("✓"); }
        else { distance.setText(String.format(Locale.US, "%.0f meters", meters)); steps.setText("≈ " + Math.round(meters/0.75f) + " steps"); }
        List<GeoPoint> pts = new ArrayList<>(); pts.add(currentPoint); pts.add(bestPoint); directionLine.setPoints(pts); map.invalidate();
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQUEST_PERMISSIONS) { if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startAll(); } }
    @Override protected void onResume() { super.onResume(); if (map!= null) map.onResume(); }
    @Override protected void onPause() { if (map!= null) map.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (fusedClient!= null && locationCallback!= null) fusedClient.removeLocationUpdates(locationCallback); if (tm!= null) tm.listen(null, PhoneStateListener.LISTEN_NONE); super.onDestroy(); }
}