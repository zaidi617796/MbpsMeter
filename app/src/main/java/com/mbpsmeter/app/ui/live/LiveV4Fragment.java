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
            walkMode = !walkMode;
            lastWalkLoc = null;
            if (walkMode) {
                btnWalk.setText("Stop Walk Test");
                Toast.makeText(getContext(), "Walk Test ON - har 10m par auto record", Toast.LENGTH_LONG).show();
            } else {
                btnWalk.setText("Start Walk Test");
                Toast.makeText(getContext(), "Walk Test OFF - " + walkCount + " readings", Toast.LENGTH_SHORT).show();
            }
        });

        updateLocation();
        startLoop();
        return v;
    }

    // ---------- loop ----------

    private void startLoop() {
        isRunning = true;
        runnable = new Runnable() {
            @Override public void run() {
                tick();
                handler.postDelayed(this, 2500);
            }
        };
        handler.post(runnable);
    }

    private void stopLoop() {
        isRunning = false;
        handler.removeCallbacks(runnable);
    }

    private void tick() {
        SignalReader.Result r = SignalReader.read(requireContext());
        if (r.real) {
            currentDbm = r.dbm;
            curNet = r.networkType;
            curOp = r.operator;
        } else {
            // Demo fallback (permission/data na mile to) - label me saaf nazar aayega
            currentDbm = -65 - random.nextInt(35);
            curNet = "Demo";
            curOp = "Demo mode";
        }
        updateLocation();
        updateUI();

        // Walk Test: 10 meter chalne par auto record
        if (walkMode) {
            Location now = currentLocation();
            if (now != null) {
                if (lastWalkLoc == null || now.distanceTo(lastWalkLoc) >= 10f) {
                    save(false, "WALK");
                    lastWalkLoc = now;
                    walkCount++;
                    if (tvWalkCount != null) tvWalkCount.setText(walkCount + " pts");
                }
            }
        }

        // Drop alarm
        if (currentDbm <= -105 && System.currentTimeMillis() - lastAlertAt > 30000) {
            lastAlertAt = System.currentTimeMillis();
            if (tvDropAlert != null) tvDropAlert.setVisibility(View.VISIBLE);
            vibrate();
        } else if (currentDbm > -100 && tvDropAlert != null) {
            tvDropAlert.setVisibility(View.GONE);
        }
    }

    private void vibrate() {
        try {
            Vibrator vib = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(400);
            }
        } catch (Exception ignored) {}
    }

    // ---------- UI ----------

    private void updateUI() {
        if (gauge != null) gauge.setValueWithColor(currentDbm);

        if (tvStrength != null) {
            String label = SignalReader.label(currentDbm);
            tvStrength.setText(label);
            if (currentDbm >= -80) {
                tvStrength.setTextColor(0xFF22C55E);
                tvStrength.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF162C23));
            } else if (currentDbm >= -95) {
                tvStrength.setTextColor(0xFFF59E0B);
                tvStrength.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E2410));
            } else {
                tvStrength.setTextColor(0xFFEF4444);
                tvStrength.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF331418));
            }
        }
        if (tvOperator != null) tvOperator.setText(curOp.isEmpty() ? "Unknown operator" : curOp);
        if (tvNetType != null) tvNetType.setText(curNet);
        if (tvGps != null) {
            tvGps.setText(String.format(Locale.getDefault(), "GPS: %.5f, %.5f", curLat, curLng));
        }
        if (tvTime != null) {
            tvTime.setText(new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date()));
        }
    }

    // ---------- location ----------

    private void updateLocation() {
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (last != null) {
                    curLat = last.getLatitude();
                    curLng = last.getLongitude();
                }
            }
        } catch (Exception ignored) {}
    }

    private Location currentLocation() {
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (Exception ignored) { return null; }
    }

    // ---------- save ----------

    private void save(boolean silent, String type) {
        try {
            updateLocation();
            String json = prefs.getString("signals", "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("dbm", currentDbm);
            obj.put("time", System.currentTimeMillis());
            obj.put("lat", curLat);
            obj.put("lng", curLng);
            obj.put("type", type);
            obj.put("net", curNet);
            obj.put("op", curOp);
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
            if (!silent) Toast.makeText(getContext(), currentDbm + " dBm saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }
}