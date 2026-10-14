package com.mbpsmeter.app.ui.live;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.ui.custom.GaugeView;
import com.mbpsmeter.app.ui.guide.GuideFragment;
import com.mbpsmeter.app.ui.sim.DualSimFragment;
import com.mbpsmeter.app.util.SignalReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * LIVE V6:
 * - Asal signal + tower info (Cell ID, band)
 * - Walk Test (har 10m auto-record), drop alarm, drop diary auto-log
 * - Compare SIMs, Urdu/English toggle
 */
public class LiveV4Fragment extends Fragment {

    private TextView tvStrength, tvGps, tvTime, tvOperator, tvNetType, tvCellInfo, tvDropAlert, tvWalkCount;
    private GaugeView gauge;
    private Button btnSave, btnStop, btnWalk, btnSim, btnLang, btnGuide;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    private int currentDbm = -85;
    private boolean noSignal = false;
    private double curLat = 24.8607, curLng = 67.0011;
    private String curNet = "Unknown", curOp = "", curCell = "", curBand = "";
    private boolean isRunning = false;

    private boolean walkMode = false;
    private Location lastWalkLoc = null;
    private int walkCount = 0;

    private long lastAlertAt = 0;

    // drop diary episode tracking
    private boolean inDrop = false;
    private long dropStart = 0;
    private int dropMin = -120;
    private double dropLat = 0, dropLng = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_live_v4, container, false);

        tvStrength = v.findViewById(R.id.tvStrength);
        tvGps = v.findViewById(R.id.tvGps);
        tvTime = v.findViewById(R.id.tvTime);
        tvOperator = v.findViewById(R.id.tvOperator);
        tvNetType = v.findViewById(R.id.tvNetType);
        tvCellInfo = v.findViewById(R.id.tvCellInfo);
        tvDropAlert = v.findViewById(R.id.tvDropAlert);
        tvWalkCount = v.findViewById(R.id.tvWalkCount);
        gauge = v.findViewById(R.id.gauge);
        btnSave = v.findViewById(R.id.btnSave);
        btnStop = v.findViewById(R.id.btnStop);
        btnWalk = v.findViewById(R.id.btnWalk);
        btnSim = v.findViewById(R.id.btnSim);
        btnLang = v.findViewById(R.id.btnLang);
        btnGuide = v.findViewById(R.id.btnGuide);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        updateLangBtn();

        btnSave.setOnClickListener(view -> save(false, "MANUAL", ""));
        btnStop.setOnClickListener(view -> {
            if (isRunning) {
                stopLoop();
                btnStop.setText(R.string.start);
                Toast.makeText(getContext(), R.string.stopped, Toast.LENGTH_SHORT).show();
            } else {
                startLoop();
                btnStop.setText(R.string.stop);
                Toast.makeText(getContext(), R.string.started, Toast.LENGTH_SHORT).show();
            }
        });
        btnWalk.setOnClickListener(view -> {
            walkMode = !walkMode;
            lastWalkLoc = null;
            if (walkMode) {
                btnWalk.setText(R.string.walk_stop);
                Toast.makeText(getContext(), R.string.walk_on_toast, Toast.LENGTH_LONG).show();
            } else {
                btnWalk.setText(R.string.walk_start);
                Toast.makeText(getContext(), getString(R.string.walk_off_toast) + " - " + walkCount, Toast.LENGTH_SHORT).show();
            }
        });
        btnSim.setOnClickListener(view -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new DualSimFragment())
                        .addToBackStack("sim")
                        .commit();
            }
        });
        btnLang.setOnClickListener(view -> toggleLocale());
        btnGuide.setOnClickListener(view -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new GuideFragment())
                        .addToBackStack("guide")
                        .commit();
            }
        });

        updateLocation();
        startLoop();
        return v;
    }

    // ---------- locale ----------

    private void updateLangBtn() {
        boolean isUr = false;
        LocaleListCompat cur = AppCompatDelegate.getApplicationLocales();
        for (int i = 0; i < cur.size(); i++) {
            if ("ur".equals(cur.get(i).getLanguage())) { isUr = true; break; }
        }
        if (btnLang != null) btnLang.setText(isUr ? "EN" : "اردو");
    }

    private void toggleLocale() {
        boolean isUr = false;
        LocaleListCompat cur = AppCompatDelegate.getApplicationLocales();
        for (int i = 0; i < cur.size(); i++) {
            if ("ur".equals(cur.get(i).getLanguage())) { isUr = true; break; }
        }
        if (isUr) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ur"));
        }
    }

    // ---------- loop ----------

    private void startLoop() {
        isRunning = true;
        runnable = new Runnable() {
            @Override public void run() {
                try {
                    tick();
                } catch (Exception e) {
                    // Signal reading me koi bhi error aaye to app crash nahi hogi,
                    // loop agle tick par dobara try karega.
                    e.printStackTrace();
                }
                if (isRunning) handler.postDelayed(this, 2500);
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
            curCell = r.cellId;
            curBand = r.band;
            noSignal = false;
        } else {
            // Koi asal data nahi - fake numbers nahi, saaf "--" dikhao
            noSignal = true;
            curNet = getString(R.string.no_signal_data);
            curOp = "";
            curCell = "";
            curBand = "";
        }
        updateLocation();
        updateUI();

        long now = System.currentTimeMillis();

        // Walk Test: 10 meter par auto record
        if (walkMode) {
            Location loc = currentLocation();
            if (loc != null && (lastWalkLoc == null || loc.distanceTo(lastWalkLoc) >= 10f)) {
                save(false, "WALK", "");
                lastWalkLoc = loc;
                walkCount++;
                if (tvWalkCount != null) tvWalkCount.setText(walkCount + " " + getString(R.string.pts));
            }
        }

        // Drop diary + alarm
        if (currentDbm <= -105) {
            if (!inDrop) {
                inDrop = true;
                dropStart = now;
                dropMin = currentDbm;
                dropLat = curLat;
                dropLng = curLng;
            } else {
                dropMin = Math.min(dropMin, currentDbm);
            }
            if (now - lastAlertAt > 30000) {
                lastAlertAt = now;
                if (tvDropAlert != null) tvDropAlert.setVisibility(View.VISIBLE);
                vibrate();
            }
        } else {
            if (inDrop && currentDbm > -100) {
                saveDrop(dropStart, now, dropMin, dropLat, dropLng);
                inDrop = false;
            }
            if (tvDropAlert != null) tvDropAlert.setVisibility(View.GONE);
        }
    }

    private void saveDrop(long start, long end, int minDbm, double lat, double lng) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("drops", "[]"));
            JSONObject o = new JSONObject();
            o.put("start", start);
            o.put("end", end);
            o.put("minDbm", minDbm);
            o.put("lat", lat);
            o.put("lng", lng);
            arr.put(o);
            while (arr.length() > 200) arr.remove(0);
            prefs.edit().putString("drops", arr.toString()).apply();
        } catch (Exception ignored) {}
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
        if (gauge != null) {
            if (noSignal) gauge.setValueWithColor(-120);
            else gauge.setValueWithColor(currentDbm);
        }

        if (tvStrength != null) {
            if (noSignal) {
                tvStrength.setText("--");
                tvStrength.setTextColor(0xFF8A9ABF);
                tvStrength.setBackgroundTintList(ColorStateList.valueOf(0xFF1A2340));
            } else {
                tvStrength.setText(SignalReader.label(currentDbm));
                if (currentDbm >= -80) {
                    tvStrength.setTextColor(0xFF22C55E);
                    tvStrength.setBackgroundTintList(ColorStateList.valueOf(0xFF162C23));
                } else if (currentDbm >= -95) {
                    tvStrength.setTextColor(0xFFF59E0B);
                    tvStrength.setBackgroundTintList(ColorStateList.valueOf(0xFF2E2410));
                } else {
                    tvStrength.setTextColor(0xFFEF4444);
                    tvStrength.setBackgroundTintList(ColorStateList.valueOf(0xFF331418));
                }
            }
        }
        if (tvOperator != null) tvOperator.setText(curOp.isEmpty() ? getString(R.string.unknown_operator) : curOp);
        if (tvNetType != null) tvNetType.setText(curNet);
        if (tvCellInfo != null) {
            if (!curCell.isEmpty() || !curBand.isEmpty()) {
                String s = getString(R.string.tower_info) + ": " + curCell;
                if (!curBand.isEmpty()) s += " | " + curBand;
                tvCellInfo.setText(s);
            } else {
                tvCellInfo.setText("");
            }
        }
        if (tvGps != null) tvGps.setText(String.format(Locale.getDefault(), "GPS: %.5f, %.5f", curLat, curLng));
        if (tvTime != null) tvTime.setText(new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date()));
    }

    // ---------- location ----------

    private void updateLocation() {
        try {
            // Pehle active helper se (fresh GPS)
            android.location.Location h = com.mbpsmeter.app.util.LocationHelper.getLast();
            if (h != null) {
                curLat = h.getLatitude();
                curLng = h.getLongitude();
                return;
            }
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
            android.location.Location h = com.mbpsmeter.app.util.LocationHelper.getLast();
            if (h != null) return h;
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (Exception ignored) { return null; }
    }

    // ---------- save ----------

    private void save(boolean silent, String type, String room) {
        if (noSignal) {
            if (!silent && getContext() != null) {
                Toast.makeText(getContext(), R.string.no_signal_data, Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            updateLocation();
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("dbm", currentDbm);
            obj.put("time", System.currentTimeMillis());
            obj.put("lat", curLat);
            obj.put("lng", curLng);
            obj.put("type", type);
            if (!room.isEmpty()) obj.put("room", room);
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
            if (!silent) Toast.makeText(getContext(), currentDbm + " dBm " + getString(R.string.room_recorded), Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onResume() {
        super.onResume();
        try { com.mbpsmeter.app.util.LocationHelper.start(requireContext()); } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        try { com.mbpsmeter.app.util.LocationHelper.stop(); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }
}
