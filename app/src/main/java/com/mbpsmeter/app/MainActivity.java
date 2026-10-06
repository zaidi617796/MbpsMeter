package com.mbpsmeter.app;

import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    TextView current, best, arrow;
    Button find;
    int bestSignal = -120;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        current = findViewById(R.id.current);
        best = findViewById(R.id.best);
        arrow = findViewById(R.id.arrow);
        find = findViewById(R.id.find);

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        tm.listen(new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                try {
                    String[] parts = signalStrength.toString().split(" ");
                    int dbm = Integer.parseInt(parts[3]);
                    if (dbm == 99) dbm = -110;
                    else dbm = -113 + 2 * dbm;

                    current.setText("Current: " + dbm + " dBm");

                    if (dbm > bestSignal) {
                        bestSignal = dbm;
                        best.setText("Best: " + bestSignal + " dBm");
                        arrow.setText("↑ YOU ARE AT BEST");
                        arrow.setTextColor(0xFF22C55E);
                    } else if (dbm < bestSignal - 5) {
                        arrow.setText("↓ GO BACK");
                        arrow.setTextColor(0xFFEF4444);
                    } else {
                        arrow.setText("→ MOVE SLOWLY");
                        arrow.setTextColor(0xFF38BDF8);
                    }
                } catch (Exception e) {
                    current.setText("Reading signal...");
                }
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        find.setOnClickListener(v -> {
            bestSignal = -120;
            best.setText("Best: - dBm");
            arrow.setText("↑ Searching...");
        });
    }
}