package com.mbpsmeter.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    TextView current, best, arrow;
    Button find;
    int bestSignal = -120;
    TelephonyManager tm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        current = findViewById(R.id.current);
        best = findViewById(R.id.best);
        arrow = findViewById(R.id.arrow);
        find = findViewById(R.id.find);

        current.setText("Current: Waiting permission...");
        best.setText("Best: - dBm");
        arrow.setText("↑");

        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE}, 101);
            return;
        }
        startListening();

        find.setOnClickListener(v -> {
            bestSignal = -120;
            best.setText("Best: - dBm");
            arrow.setText("↑");
            arrow.setTextColor(0xFF38BDF8);
            current.setText("Searching...");
        });
    }

    void startListening() {
        try {
            tm.listen(new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    super.onSignalStrengthsChanged(signalStrength);
                    try {
                        int dbm = -90;
                        // Android 10+ safe method
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            if(signalStrength.getCellSignalStrengths().size() > 0) {
                                dbm = signalStrength.getCellSignalStrengths().get(0).getDbm();
                            }
                        } else {
                            // old safe fallback
                            String[] parts = signalStrength.toString().split(" ");
                            if (parts.length > 3) {
                                try {
                                    int level = Integer.parseInt(parts[1]);
                                    if(level!= 99) dbm = -113 + 2 * level;
                                } catch (Exception e) {}
                            }
                        }

                        int finalDbm = dbm;
                        runOnUiThread(() -> {
                            if(current == null) return;
                            current.setText("Current: " + finalDbm + " dBm");
                            if (finalDbm > bestSignal) {
                                bestSignal = finalDbm;
                                best.setText("Best: " + bestSignal + " dBm");
                                arrow.setText("↑ BEST");
                                arrow.setTextColor(0xFF22C55E);
                            } else if (finalDbm < bestSignal - 8) {
                                arrow.setText("↓ BACK");
                                arrow.setTextColor(0xFFEF4444);
                            } else {
                                arrow.setText("→ MOVE");
                                arrow.setTextColor(0xFF38BDF8);
                            }
                        });
                    } catch (Exception ignored) {}
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            current.setText("Current: Scanning...");
        } catch (Exception e) {
            current.setText("Error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            current.setText("Permission needed!");
        }
    }
}