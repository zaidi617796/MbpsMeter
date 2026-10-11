package com.mbpsmeter.app.ui.live;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.ui.custom.GaugeView;
import com.mbpsmeter.app.util.SignalReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * LIVE screen V5:
 * - ASAL signal (TelephonyManager), random sirf demo fallback
 * - Walk Test: har 10 meter par auto-record
 * - Drop alarm: -105 dBm se neeche vibrate + banner
 */
public class LiveV4Fragment extends Fragment {

    private TextView tvStrength, tvGps, tvTime, tvOperator, tvNetType, tvDropAlert, tvWalkCount;
    private GaugeView gauge;
    private Button btnSave, btnStop, btnWalk;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private final Random random = new Random();

    private int currentDbm = -85;
    private double curLat = 24.8607, curLng = 67.0011;
    private String curNet = "Unknown", curOp = "";
    private boolean isRunning = false;

    // walk test
    private boolean walkMode = false;
    private Location lastWalkLoc = null;
    private int walkCount = 0;

    // drop alarm
    private long lastAlertAt = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_live_v4, container, false);

        tvStrength = v.findViewById(R.id.tvStrength);
        tvGps = v.findViewById(R.id.tvGps);
        tvTime = v.findViewById(R.id.tvTime);
        tvOperator = v.findViewById(R.id.tvOperator);
        tvNetType = v.findViewById(R.id.tvNetType);
        tvDropAlert = v.findViewById(R.id.tvDropAlert);
        tvWalkCount = v.findViewById(R.id.tvWalkCount);
        gauge = v.findViewById(R.id.gauge);
        btnSave = v.findViewById(R.id.btnSave);
        btnStop = v.findViewById(R.id.btnStop);
        btnWalk = v.findViewById(R.id.btnWalk);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        btnSave.setOnClickListener(view -> save(false, "MANUAL"));
        btnStop.setOnClickListener(view -> {
            if (isRunning) {
                stopLoop();
                btnStop.setText("Start");
                Toast.makeText(getContext(), "Stopped", Toast.LENGTH_SHORT).show();
            } else {
                startLoop();
                btnStop.setText("Stop");
                Toast.makeText(getContext(), "Started", Toast.LENGTH_SHORT).show();
            }
        });
        btnWalk.setOnClickListener(view -> {