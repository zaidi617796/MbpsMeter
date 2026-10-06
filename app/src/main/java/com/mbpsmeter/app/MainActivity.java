package com.mbpsmeter.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

public class MainActivity extends AppCompatActivity {

    TextView current, best, arrow;
    Button find, navigate;
    MapView map;
    int bestSignal = -120;
    GeoPoint bestPoint = null;
    GeoPoint currentPoint = null;
    int currentDbm = -100;
    FusedLocationProviderClient fusedClient;
    TelephonyManager tm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        current = findViewById(R.id.current);
        best = findViewById(R.id.best);
        arrow = findViewById(R.id.arrow);
        find = findViewById(R.id.find);
        navigate = findViewById(R.id.navigate);
        map = findViewById(R.id.map);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(18.0);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        checkPermission();

        find.setOnClickListener(v -> {
            bestSignal = -120;
            bestPoint = null;
            map.getOverlays().clear();
            map.invalidate();
            best.setText("Best: - dBm");
            arrow.setText("SEARCHING...");
        });

        navigate.setOnClickListener(v -> {
            if(bestPoint!= null) {
                String uri = "google.navigation:q=" + bestPoint.getLatitude() + "," + bestPoint.getLongitude();
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                i.setPackage("com.google.android.apps.maps");
                startActivity(i);
            }
        });
    }

    void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE}, 101);
        } else startAll();
    }

    void startAll() { startLocation(); startSignal(); }

    void startLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) return;
        LocationRequest req = LocationRequest.create();
        req.setInterval(2000);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        fusedClient.requestLocationUpdates(req, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult r) {
                if(r== null || r.getLastLocation()== null) return;
                currentPoint = new GeoPoint(r.getLastLocation().getLatitude(), r.getLastLocation().getLongitude());
                map.getController().animateTo(currentPoint);
                addZone(currentPoint, currentDbm);
            }
        }, getMainLooper());
    }

    void addZone(GeoPoint p, int dbm) {
        if(p== null) return;
        int color = dbm > -85? 0x8022C55E : dbm > -100? 0x80EAB308 : 0x80EF4444;
        Polygon circle = new Polygon(map);
        circle.setPoints(Polygon.pointsAsCircle(p, 5));
        circle.setFillColor(color);
        circle.setStrokeWidth(0);
        map.getOverlays().add(circle);
        map.invalidate();
        if(dbm > bestSignal) {
            bestSignal = dbm;
            bestPoint = p;
            int finalBest = bestSignal;
            runOnUiThread(() -> {
                best.setText("Best: " + finalBest + " dBm");
                arrow.setText("BEST HERE");
            });
        }
    }

    void startSignal() {
        tm.listen(new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength s) {
                int dbm = -100;
                try {
                    if(s.getCellSignalStrengths().size()>0)
                        dbm = s.getCellSignalStrengths().get(0).getDbm();
                } catch (Exception e) {}
                currentDbm = dbm;
                int finalDbm = dbm; // FIX: lambda ke liye final banana zaroori hai
                runOnUiThread(() -> current.setText("Current: " + finalDbm + " dBm"));
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] g) {
        super.onRequestPermissionsResult(c,p,g);
        if(g.length>0 && g[0]== PackageManager.PERMISSION_GRANTED) startAll();
    }
}