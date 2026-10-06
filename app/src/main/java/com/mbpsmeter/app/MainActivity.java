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

        current.setText("Current: Waiting...");
        best.setText("Best: - dBm");
        arrow.setText("↑ FIND BETTER");

        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE}, 1);
            current.setText("Please allow permissions");
            return;
        }

        startListening();

        find.setOnClickListener(v -> {
            bestSignal = -120;
            best.setText("Best: - dBm");
            arrow.setText("↑ Searching...");
            arrow.setTextColor(0xFF38BDF8);
        });
    }

    void startListening() {
        try {
            tm.listen(new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    try {
                        int dbm = -90;
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            dbm = signalStrength.getCellSignalStrengths().get(0).getDbm();
                        } else {
                            // safe old method
                            String s = signalStrength.toString();
                            String[] p = s.split(" ");
                            if (p.length > 3) {
                                int v = Integer.parseInt(p[1]);
                                if (v!= 99) dbm = -113 + 2 * v;
                            }
                        }

                        int finalDbm = dbm;
                        runOnUiThread(() -> {
                            current.setText("Current: " + finalDbm + " dBm");
                            if (finalDbm > bestSignal) {
                                bestSignal = finalDbm;
                                best.setText("Best: " + bestSignal + " dBm");
                                arrow.setText("↑ YOU ARE AT BEST");
                                arrow.setTextColor(0xFF22C55E);
                            } else if (finalDbm < bestSignal - 7) {
                                arrow.setText("↓ GO BACK");
                                arrow.setTextColor(0xFFEF4444);
                            } else {
                                arrow.setText("→ MOVE SLOWLY");
                                arrow.setTextColor(0xFF38BDF8);
                            }
                        });
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        } catch (Exception e) {
            current.setText("Error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }
}