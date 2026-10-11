package com.mbpsmeter.app.ui.live;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
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
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.ui.custom.GaugeView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class LiveV4Fragment extends Fragment {

    private TextView tvStrength, tvGps, tvTime;
    private GaugeView gauge;
    private Button btnSave, btnStop;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private Random random = new Random();
    private int currentDbm = -75;
    private double curLat = 24.8607, curLng = 67.0011;
    private boolean isRunning = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_live_v4, container, false);
        tvStrength = v.findViewById(R.id.tvStrength);
        tvGps = v.findViewById(R.id.tvGps);
        tvTime = v.findViewById(R.id.tvTime);
        gauge = v.findViewById(R.id.gauge);
        btnSave = v.findViewById(R.id.btnSave);
        btnStop = v.findViewById(R.id.btnStop);
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        getRealLocation();
        startLoop();
        btnSave.setOnClickListener(view -> save(false));
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
        return v;
    }

    private void getRealLocation() {
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (last != null) {
                    curLat = last.getLatitude();
                    curLng = last.getLongitude();
                    if (tvGps != null) tvGps.setText(String.format(Locale.getDefault(), "GPS: %.4f, %.4f - Karachi", curLat, curLng));
                } else {
                    if (tvGps != null) tvGps.setText("GPS: Karachi (24.8607, 67.0011)");
                }
            }
        } catch (Exception e) {
            if (tvGps != null) tvGps.setText("GPS: Karachi");
        }
    }

    private void startLoop() {
        isRunning = true;
        runnable = new Runnable() {
            @Override public void run() {
                currentDbm = -65 - random.nextInt(35);
                updateUI(currentDbm);
                handler.postDelayed(this, 2500);
            }
        };
        handler.post(runnable);
    }

    private void stopLoop() {
        isRunning = false;
        handler.removeCallbacks(runnable);
    }

    private void updateUI(int dbm) {
        if (gauge != null) gauge.setValueWithColor(dbm);
        if (tvStrength != null) {
            if (dbm >= -80) { tvStrength.setText("Strong"); tvStrength.setTextColor(0xFF22C55E); }
            else if (dbm >= -95) { tvStrength.setText("Fair"); tvStrength.setTextColor(0xFFF59E0B); }
            else { tvStrength.setText("Weak"); tvStrength.setTextColor(0xFFEF4444); }
        }
        if (tvTime != null) tvTime.setText(new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date()));
        getRealLocation();
        if (dbm >= -85) save(true);
    }

    private void save(boolean auto) {
        try {
            getRealLocation();
            String json = prefs.getString("signals", "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("dbm", currentDbm);
            obj.put("time", System.currentTimeMillis());
            obj.put("lat", curLat);
            obj.put("lng", curLng);
            obj.put("type", auto ? "AUTO" : "MANUAL");
            arr.put(obj);
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("signals", arr.toString());
            int oldBest = prefs.getInt("best_dbm", -120);
            if (currentDbm > oldBest) {
                ed.putFloat("best_lat", (float) curLat);
                ed.putFloat("best_lng", (float) curLng);
                ed.putInt("best_dbm", currentDbm);
            }
            ed.apply();
            if (!auto) Toast.makeText(getContext(), currentDbm + " dBm saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }
}