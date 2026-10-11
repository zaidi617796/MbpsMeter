package com.mbpsmeter.app.ui.speed;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * SPEED TEST V5 (SwiftNet style):
 * Ping + Jitter + live download progress + result History me save.
 */
public class SpeedV4Fragment extends Fragment {

    private TextView tvMainSpeed, tvDownload, tvUpload, tvPing, tvJitter, tvStatus;
    private Button btnStart;
    private volatile boolean running = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_speed_v4, c, false);
        tvMainSpeed = v.findViewById(R.id.tvMainSpeed);
        tvDownload = v.findViewById(R.id.tvDownload);
        tvUpload = v.findViewById(R.id.tvUpload);
        tvPing = v.findViewById(R.id.tvPing);
        tvJitter = v.findViewById(R.id.tvJitter);
        tvStatus = v.findViewById(R.id.tvStatus);
        btnStart = v.findViewById(R.id.btnStartSpeed);
        btnStart.setOnClickListener(x -> runTest());
        return v;
    }

    private void ui(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }

    private void setText(TextView tv, String s) {
        ui(() -> { if (tv != null) tv.setText(s); });
    }

    void runTest() {
        if (running) return;
        running = true;
        ui(() -> {
            btnStart.setEnabled(false);
            btnStart.setAlpha(0.4f);
            tvMainSpeed.setText("--");
            tvDownload.setText("-- Mbps");
            tvUpload.setText("-- Mbps");
            tvPing.setText("-- ms");
            tvJitter.setText("-- ms");
        });
        setText(tvStatus, "Measuring ping…");

        new Thread(() -> {
            double pingMs = -1, jitterMs = -1, down = -1, up = -1;

            // ---- Ping: 3 chhoti requests, min lo ----
            try {
                double min = Double.MAX_VALUE, max = 0;
                for (int i = 0; i < 3; i++) {
                    long t0 = System.nanoTime();
                    HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=1000").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[4096];
                    while (in.read(buf) != -1) {}
                    in.close();
                    c.disconnect();
                    double ms = (System.nanoTime() - t0) / 1e6;
                    if (ms < min) min = ms;
                    if (ms > max) max = ms;
                }
                pingMs = min;
                jitterMs = max - min;
                double p = pingMs, j = jitterMs;
                ui(() -> {
                    tvPing.setText(String.format(Locale.US, "%.0f ms", p));
                    tvJitter.setText(String.format(Locale.US, "%.0f ms", j));
                });
            } catch (Exception ignored) {}

            // ---- Download: 10 MB, live progress ----
            setText(tvStatus, "Testing download…");
            try {
                long start = System.nanoTime();
                long bytes = 0, lastUi = start;
                HttpURLConnection con = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=10000000").openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(30000);
                InputStream in = con.getInputStream();
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bytes += n;
                    long now = System.nanoTime();
                    if (now - lastUi > 500_000_000L) {
                        lastUi = now;
                        double sec = (now - start) / 1e9;
                        if (sec > 0.5) {
                            double live = bytes * 8.0 / sec / 1e6;
                            setText(tvMainSpeed, String.format(Locale.US, "%.1f", live));
                            setText(tvDownload, String.format(Locale.US, "%.1f Mbps", live));
                        }
                    }
                }
                in.close();
                con.disconnect();
                double sec = (System.nanoTime() - start) / 1e9;
                if (sec > 0) down = bytes * 8.0 / sec / 1e6;
            } catch (Exception ignored) {}

            // ---- Upload: 500 KB ----
            setText(tvStatus, "Testing upload…");
            try {
                byte[] payload = new byte[500000];
                java.util.Arrays.fill(payload, (byte) 65);
                HttpURLConnection upCon = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
                upCon.setRequestMethod("POST");
                upCon.setDoOutput(true);
                upCon.setConnectTimeout(15000);
                upCon.setReadTimeout(30000);
                upCon.setFixedLengthStreamingMode(payload.length);
                long upStart = System.nanoTime();
                OutputStream os = upCon.getOutputStream();
                os.write(payload);
                os.flush();
                os.close();
                InputStream r = upCon.getInputStream();
                while (r.read(new byte[4096]) != -1) {}
                r.close();
                double upSec = (System.nanoTime() - upStart) / 1e9;
                if (upSec > 0) up = payload.length * 8.0 / upSec / 1e6;
                upCon.disconnect();
            } catch (Exception ignored) {}

            double fd = down, fu = up, fp = pingMs, fj = jitterMs;
            ui(() -> {
                running = false;
                btnStart.setEnabled(true);
                btnStart.setAlpha(1f);
                tvMainSpeed.setText(fd >= 0 ? String.format(Locale.US, "%.1f", fd) : "--");
                tvDownload.setText(fd >= 0 ? String.format(Locale.US, "%.2f Mbps", fd) : "Failed");
                tvUpload.setText(fu >= 0 ? String.format(Locale.US, "%.2f Mbps", fu) : "Failed");
                if (fd < 0 && fu < 0) {
                    tvStatus.setText("Failed — internet check karein");
                } else {
                    tvStatus.setText("Complete — History me save ho gaya");
                    saveResult(fd, fu, fp, fj);
                }
            });
        }).start();
    }

    private void saveResult(double down, double up, double ping, double jitter) {
        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString("speed_tests", "[]"));
            JSONObject o = new JSONObject();
            o.put("time", System.currentTimeMillis());
            o.put("down", Math.max(0, down));
            o.put("up", Math.max(0, up));
            o.put("ping", Math.max(0, ping));
            o.put("jitter", Math.max(0, jitter));
            arr.put(o);
            // aakhri 100 rakhein
            while (arr.length() > 100) arr.remove(0);
            prefs.edit().putString("speed_tests", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
