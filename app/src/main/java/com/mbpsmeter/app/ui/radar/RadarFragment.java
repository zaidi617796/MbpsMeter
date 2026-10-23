package com.mbpsmeter.app.ui.radar;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.LocationHelper;
import com.mbpsmeter.app.util.SignalReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

/**
 * SIGNAL RADAR:
 * Metal-detector style best-spot finder. Beep + buzz speed up as the signal
 * gets stronger; a warmer/colder trend shows whether you are walking the
 * right way. The best spot can be saved into the same "signals" store the
 * Live tab uses (type=RADAR), so it shows up on Map and History.
 * No fake numbers: when there is no real reading, the screen shows "--"
 * and the radar stays silent.
 */
public class RadarFragment extends Fragment {

    private TextView tvDbm, tvLabel, tvOp, tvTrend, tvBest;
    private ProgressBar meter;
    private Button btnToggle, btnSave, btnSound, btnVib;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable, beepRunnable;

    private boolean running = false;
    private boolean soundOn = true;
    private boolean vibOn = true;

    private int curDbm = -120;
    private boolean noSignal = true;
    private String curNet = "", curOp = "", curCell = "", curBand = "";
    private double curLat = 24.8607, curLng = 67.0011;

    private final ArrayDeque<Integer> window = new ArrayDeque<>();
    private int smoothDbm = -120;
    private int prevSmooth = Integer.MIN_VALUE;

    private int bestDbm = -120;

    private ToneGenerator tones;
    private Vibrator vibrator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_radar, container, false);

        tvDbm = v.findViewById(R.id.tvRadarDbm);
        tvLabel = v.findViewById(R.id.tvRadarLabel);
        tvOp = v.findViewById(R.id.tvRadarOp);
        tvTrend = v.findViewById(R.id.tvRadarTrend);
        tvBest = v.findViewById(R.id.tvRadarBest);
        meter = v.findViewById(R.id.radarMeter);
        btnToggle = v.findViewById(R.id.btnRadarToggle);
        btnSave = v.findViewById(R.id.btnRadarSave);
        btnSound = v.findViewById(R.id.btnRadarSound);
        btnVib = v.findViewById(R.id.btnRadarVib);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        try {
            vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {}
        try {
            tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
        } catch (Exception ignored) {}

        btnToggle.setOnClickListener(view -> {
            if (running) {
                stopRadar();
                btnToggle.setText(R.string.start);
            } else {
                startRadar();
                btnToggle.setText(R.string.stop);
            }
        });
        btnSave.setOnClickListener(view -> saveSpot());
        btnSound.setOnClickListener(view -> {
            soundOn = !soundOn;
            updateToggleTexts();
        });
        btnVib.setOnClickListener(view -> {
            vibOn = !vibOn;
            updateToggleTexts();
        });

        updateToggleTexts();
        btnToggle.setText(R.string.stop);
        startRadar();
        return v;
    }

    // ---------- radar loops ----------

    private void startRadar() {
        if (running) return;
        running = true;
        tickRunnable = new Runnable() {
            @Override public void run() {
                try { tick(); } catch (Exception e) { e.printStackTrace(); }
                if (running) handler.postDelayed(this, 1000);
            }
        };
        handler.post(tickRunnable);
        beepRunnable = new Runnable() {
            @Override public void run() {
                if (!running) return;
                try { pulse(); } catch (Exception ignored) {}
                long delay = noSignal ? 600 : intervalFor(smoothDbm);
                handler.postDelayed(this, delay);
            }
        };
        handler.post(beepRunnable);
    }

    private void stopRadar() {
        running = false;
        if (tickRunnable != null) handler.removeCallbacks(tickRunnable);
        if (beepRunnable != null) handler.removeCallbacks(beepRunnable);
    }

    /** Beep interval: -115 dBm = 1500ms (slow) .. -50 dBm = 150ms (racing) */
    private long intervalFor(int dbm) {
        double frac = (dbm + 115) / 65.0;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        return (long) (1500 - frac * 1350);
    }

    private int percentFor(int dbm) {
        double frac = (dbm + 115) / 65.0;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        return (int) (frac * 100);
    }

    private void pulse() {
        if (noSignal) return;
        if (soundOn && tones != null) {
            try { tones.startTone(ToneGenerator.TONE_PROP_BEEP, 80); } catch (Exception ignored) {}
        }
        if (vibOn && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(45);
                }
            } catch (Exception ignored) {}
        }
    }

    // ---------- reading ----------

    private void tick() {
        SignalReader.Result r = SignalReader.read(requireContext());
        if (r.real) {
            curDbm = r.dbm;
            curNet = r.networkType;
            curOp = r.operator;
            curCell = r.cellId;
            curBand = r.band;
            noSignal = false;
        } else {
            noSignal = true;
            curNet = "";
            curOp = "";
            curCell = "";
            curBand = "";
        }
        updateLocation();

        if (!noSignal) {
            window.addLast(curDbm);
            while (window.size() > 3) window.removeFirst();
            int sum = 0;
            for (int x : window) sum += x;
            prevSmooth = (smoothDbm == -120 && window.size() == 1) ? Integer.MIN_VALUE : smoothDbm;
            smoothDbm = sum / window.size();
            if (curDbm > bestDbm) bestDbm = curDbm;
        } else {
            window.clear();
            smoothDbm = -120;
            prevSmooth = Integer.MIN_VALUE;
        }
        updateUI();
    }

    // ---------- UI ----------

    private int colorFor(int dbm) {
        if (dbm >= -80) return 0xFF22C55E;
        if (dbm >= -95) return 0xFFF59E0B;
        return 0xFFEF4444;
    }

    private void updateUI() {
        if (tvDbm == null) return;
        if (noSignal) {
            tvDbm.setText("--");
            tvDbm.setTextColor(0xFF8A9ABF);
            if (tvLabel != null) {
                tvLabel.setText(R.string.no_signal_data);
                tvLabel.setTextColor(0xFF8A9ABF);
            }
            if (meter != null) {
                meter.setProgress(0);
                meter.setProgressTintList(ColorStateList.valueOf(0xFF8A9ABF));
            }
            if (tvTrend != null) {
                tvTrend.setText("—");
                tvTrend.setTextColor(0xFF8A9ABF);
            }
        } else {
            int c = colorFor(smoothDbm);
            tvDbm.setText(String.valueOf(smoothDbm));
            tvDbm.setTextColor(c);
            if (tvLabel != null) {
                tvLabel.setText(SignalReader.label(smoothDbm) + " • " + curDbm + " dBm");
                tvLabel.setTextColor(c);
            }
            if (meter != null) {
                meter.setProgress(percentFor(smoothDbm));
                meter.setProgressTintList(ColorStateList.valueOf(c));
            }
            if (tvTrend != null) {
                if (prevSmooth == Integer.MIN_VALUE) {
                    tvTrend.setText(R.string.radar_steady);
                    tvTrend.setTextColor(0xFF8A9ABF);
                } else {
                    int delta = smoothDbm - prevSmooth;
                    if (delta >= 2) {
                        tvTrend.setText(R.string.radar_warmer);
                        tvTrend.setTextColor(0xFF22C55E);
                    } else if (delta <= -2) {
                        tvTrend.setText(R.string.radar_colder);
                        tvTrend.setTextColor(0xFFEF4444);
                    } else {
                        tvTrend.setText(R.string.radar_steady);
                        tvTrend.setTextColor(0xFF8A9ABF);
                    }
                }
            }
        }
        if (tvOp != null) {
            String op = curOp.isEmpty() ? getString(R.string.unknown_operator) : curOp;
            String extra = curNet.isEmpty() ? "" : " • " + curNet;
            tvOp.setText(op + extra);
        }
        if (tvBest != null) {
            if (bestDbm > -120) {
                tvBest.setText(getString(R.string.radar_best, bestDbm));
            } else {
                tvBest.setText(R.string.radar_best_none);
            }
        }
    }

    private void updateToggleTexts() {
        if (btnSound != null) btnSound.setText(soundOn ? R.string.radar_sound_on : R.string.radar_sound_off);
        if (btnVib != null) btnVib.setText(vibOn ? R.string.radar_vib_on : R.string.radar_vib_off);
    }

    // ---------- location (Live tab wala hi tareeqa) ----------

    private void updateLocation() {
        try {
            Location h = LocationHelper.getLast();
            if (h != null) {
                curLat = h.getLatitude();
                curLng = h.getLongitude();
                return;
            }
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (lm != null && ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (last != null) {
                    curLat = last.getLatitude();
                    curLng = last.getLongitude();
                }
            }
        } catch (Exception ignored) {}
    }

    // ---------- save (Live.save jesa hi format, type=RADAR) ----------

    private void saveSpot() {
        if (noSignal) {
            if (getContext() != null) {
                Toast.makeText(getContext(), R.string.no_signal_data, Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            updateLocation();
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("dbm", curDbm);
            obj.put("time", System.currentTimeMillis());
            obj.put("lat", curLat);
            obj.put("lng", curLng);
            obj.put("type", "RADAR");
            obj.put("net", curNet);
            obj.put("op", curOp);
            if (!curCell.isEmpty()) obj.put("cell", curCell);
            if (!curBand.isEmpty()) obj.put("band", curBand);
            arr.put(obj);

            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("signals", arr.toString());
            int oldBest = prefs.getInt("best_dbm", -120);
            if (curDbm > oldBest) {
                ed.putFloat("best_lat", (float) curLat);
                ed.putFloat("best_lng", (float) curLng);
                ed.putInt("best_dbm", curDbm);
            }
            ed.apply();
            if (getContext() != null) {
                Toast.makeText(getContext(), curDbm + " dBm " + getString(R.string.room_recorded),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------- lifecycle ----------

    @Override
    public void onResume() {
        super.onResume();
        try { LocationHelper.start(requireContext()); } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        try { LocationHelper.stop(); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRadar();
        if (tones != null) {
            try { tones.release(); } catch (Exception ignored) {}
            tones = null;
        }
    }
}
