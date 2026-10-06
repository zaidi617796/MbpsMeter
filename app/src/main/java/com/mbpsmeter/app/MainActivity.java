package com.mbpsmeter.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.telephony.*;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient fusedLocation;
    private Location currentLocation;
    private int currentDbm = -110;
    private List<Spot> spots = new ArrayList<>();

    private TextView tvCurrent, tvBest, tvArrow;
    private Button btnFind;

    static class Spot {
        double lat, lng, mbps;
        int dbm;
        Spot(double lat, double lng, int dbm, double mbps) {
            this.lat = lat; this.lng = lng; this.dbm = dbm; this.mbps = mbps;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCurrent = findViewById(R.id.tvCurrent);
        tvBest = findViewById(R.id.tvBest);
        tvArrow = findViewById(R.id.tvArrow);
        btnFind = findViewById(R.id.btnFind);

        fusedLocation = LocationServices.getFusedLocationProviderClient(this);

        startSignalListener();
        startLocationUpdates();

        btnFind.setOnClickListener(v -> doSpeedTest());
    }

    private void startSignalListener() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            tm.listen(new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    try {
                        List<CellInfo> infos = tm.getAllCellInfo();
                        if (infos != null && !infos.isEmpty() && infos.get(0) instanceof CellInfoLte) {
                            currentDbm = ((CellInfoLte) infos.get(0)).getCellSignalStrength().getDbm();
                        }
                        updateUI();
                    } catch (Exception ignored) {}
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
    }

    private void startLocationUpdates() {
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocation.requestLocationUpdates(req, new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult result) {
                    currentLocation = result.getLastLocation();
                    updateUI();
                }
            }, getMainLooper());
        }
    }

    private void updateUI() {
        if (currentLocation != null) {
            tvCurrent.setText("You: " + currentDbm + " dBm | Lat: " + String.format("%.5f", currentLocation.getLatitude()));
        }
    }

    private void doSpeedTest() {
        tvBest.setText("Testing... 5MB downloading...");
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                byte[] data = new java.net.URL("https://speed.cloudflare.com/__down?bytes=5000000").openStream().readAllBytes();
                double timeSec = (System.currentTimeMillis() - start) / 1000.0;
                double mbps = (data.length * 8 / 1_000_000.0) / timeSec;

                runOnUiThread(() -> {
                    if (currentLocation != null) {
                        spots.add(new Spot(currentLocation.getLatitude(), currentLocation.getLongitude(), currentDbm, mbps));
                        tvBest.setText("Saved: " + String.format("%.2f Mbps @ %d dBm", mbps, currentDbm));
                        findBestSpot();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvBest.setText("Test failed: " + e.getMessage()));
            }
        }).start();
    }

    private void findBestSpot() {
        if (currentLocation == null || spots.size() < 2) {
            tvArrow.setText("Thora chalo aur 2-3 jagah test karo");
            return;
        }
        Spot best = spots.get(0);
        for (Spot s : spots) if (s.mbps > best.mbps) best = s;

        Location bestLoc = new Location("");
        bestLoc.setLatitude(best.lat);
        bestLoc.setLongitude(best.lng);

        float distance = currentLocation.distanceTo(bestLoc);
        float bearing = currentLocation.bearingTo(bestLoc);

        String dir;
        if (bearing >= -45 && bearing <= 45) dir = "↑ North";
        else if (bearing > 45 && bearing <= 135) dir = "→ East";
        else if (bearing > 135 || bearing < -135) dir = "↓ South";
        else dir = "← West";

        tvArrow.setText(dir + "\n" + (int)distance + "m\nExpected: " + String.format("%.1f Mbps", best.mbps));
        tvBest.setText(tvBest.getText() + "\nBest is " + (int)distance + "m away");
    }
}