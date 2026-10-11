package com.mbpsmeter.app.ui.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class LiveV4Fragment extends Fragment {

    private TextView tvDbm, tvStrength, tvGps, tvTime;
    private Button btnSave;
    private SharedPreferences prefs;
    private boolean isAutoSaving = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable signalRunnable;
    private Random random = new Random();
    private int currentDbm = -85;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_v4, container, false);

        tvDbm = view.findViewById(R.id.tvDbm);
        tvStrength = view.findViewById(R.id.tvStrength);
        tvGps = view.findViewById(R.id.tvGps);
        tvTime = view.findViewById(R.id.tvTime);
        btnSave = view.findViewById(R.id.btnSave);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        btnSave.setOnClickListener(v -> saveReadingManual());

        // Start auto signal simulation / reading
        startSignalUpdates();

        return view;
    }

    private void startSignalUpdates() {
        signalRunnable = new Runnable() {
            @Override
            public void run() {
                // Simulate real dbm variation - replace with real TelephonyManager logic later
                currentDbm = -70 - random.nextInt(40); // -70 to -110
                updateUI(currentDbm);
                handler.postDelayed(this, 3000); // update every 3 sec
            }
        };
        handler.post(signalRunnable);
    }

    private void updateUI(int dbm) {
        tvDbm.setText(dbm + " dBm");
        
        String level;
        int color;

        if (dbm >= -80) {
            level = "STRONG SIGNAL";
            color = getResources().getColor(R.color.signal_strong);
            tvStrength.setText("GOOD SIGNAL");
        } else if (dbm >= -95) {
            level = "FAIR SIGNAL";
            color = getResources().getColor(R.color.signal_moderate);
            tvStrength.setText("FAIR SIGNAL");
        } else {
            level = "WEAK SIGNAL";
            color = getResources().getColor(R.color.signal_weak);
            tvStrength.setText("WEAK SIGNAL");
        }
        tvStrength.setTextColor(color);
        
        String time = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date());
        tvTime.setText(time);
        tvGps.setText("GPS: 33.6844, 73.0479"); // replace with real location later

        // AUTO SAVE FOR FAIR & GOOD ONLY
        if (dbm >= -95 && !isAutoSaving) {
            isAutoSaving = true;
            saveReadingAuto(dbm, level);
        }
    }

    private void saveReadingManual() {
        saveToPrefs(currentDbm, "MANUAL");
        Toast.makeText(getContext(), "Saved: " + currentDbm + " dBm", Toast.LENGTH_SHORT).show();
    }

    private void saveReadingAuto(int dbm, String level) {
        saveToPrefs(dbm, level);
        if(getContext() != null){
            Toast.makeText(getContext(), "Auto Saved: " + level + " (" + dbm + " dBm)", Toast.LENGTH_SHORT).show();
        }
        // Allow next auto save after 10 seconds to avoid spamming
        handler.postDelayed(() -> isAutoSaving = false, 10000);
    }

    private void saveToPrefs(int dbm, String level) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("last_dbm", dbm);
        editor.putString("last_level", level);
        editor.putLong("last_time", System.currentTimeMillis());
        // Also save best location if good
        if(dbm >= -80){
            editor.putFloat("best_lat", 33.6844f);
            editor.putFloat("best_lng", 73.0479f);
            editor.putInt("best_dbm", dbm);
        }
        editor.apply();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && signalRunnable != null) {
            handler.removeCallbacks(signalRunnable);
        }
    }
}