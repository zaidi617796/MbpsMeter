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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * SPEED TEST V6: Ping + Jitter + live progress + History save + time pattern.
 * Time pattern: saved tests se nikalta hai din ka best/worst hour (congestion).
 */
public class SpeedV4Fragment extends Fragment {

    private TextView tvMainSpeed, tvDownload, tvUpload, tvPing, tvJitter, tvStatus, tvPattern;
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
        tvPattern = v.findViewById(R.id.tvPattern);
        btnStart = v.findViewById(R.id.btnStartSpeed);
        btnStart.setOnClickListener(x -> runTest());
        showPattern();
        return v;
    }

    private void ui(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }

    private void setText(TextView tv, String s) {
        ui(() -> { if (tv != null) tv.setText(s); });
    }

    /** Saved tests se best/worst hour nikalein */
    private void showPattern() {
        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString("speed_tests", "[]"));
            if (arr.length() < 3) {
                if (tvPattern != null) tvPattern.setText(getString(R.string.speed_pattern_none));
                return;
            }
            double[] sum = new double[24];
            int[] cnt = new int[24];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(o.optLong("time", 0));
                int h = cal.get(Calendar.HOUR_OF_DAY);
                sum[h] += o.optDouble("down", 0);
                cnt[h]++;
            }
            int bestH = -1, worstH = -1;
            double bestV = -1, worstV = Double.MAX_VALUE;
            for (int h = 0; h < 24; h++) {
                if (cnt[h] == 0) continue;
                double avg = sum[h] / cnt[h];
                if (avg > bestV) { bestV = avg; bestH = h; }
                if (avg < worstV) { worstV = avg; worstH = h; }
            }
            if (bestH >= 0 && tvPattern != null) {
                String s = getString(R.string.speed_pattern_best) + ": " + hourStr(bestH)
                        + String.format(Locale.US, " (%.1f Mbps avg)", bestV);
                if (worstH >= 0 && worstH != bestH) {
                    s += "\n" + getString(R.string.speed_pattern_worst) + ": " + hourStr(worstH)
                            + String.format(Locale.US, " (%.1f Mbps avg)", worstV);
                }
                s += "\n" + arr.length() + " " + getString(R.string.speed_tests_suffix);
                final String fs = s;
                ui(() -> tvPattern.setText(fs));
            }
        } catch (Exception ignored) {}
    }

    private String hourStr(int h) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String ap = h < 12 ? "AM" : "PM";
        return h12 + " " + ap;
    }

    void runTest() {
        if (running) return;
        running = true;
        ui(() -> {
            btnStart.setEnabled(false);
            btnStart.setAlpha(0.4f);
            tvMainSpeed.setText("--");
            tvDownload.setText("--");
            tvUpload.setText("--");
            tvPing.setText("--");
            tvJitter.setText("--");
        });
        setText(tvStatus, getString(R.string.measuring_ping));

        new Thread(() -> {
            double pingMs = -1, jitterMs = -1, down = -1, up = -1;

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
                    tvPing.setText(String.format(Locale.US, "%.0f %s", p, getString(R.string.ms)));
                    tvJitter.setText(String.format(Locale.US, "%.0f %s", j, getString(R.string.ms)));
                });
            } catch (Exception ignored) {}

            setText(tvStatus, getString(R.string.testing_down));
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
                    if (now - lastUi > 200_000_000L) {
                        lastUi = now;
                        double sec = (now - start) / 1e9;
                        if (sec > 0.2) {
                            double live = bytes * 8.0 / sec / 1e6;
                            setText(tvMainSpeed, String.format(Locale.US, "%.1f", live));
                            setText(tvDownload, String.format(Locale.US, "%.1f %s", live, getString(R.string.mbps)));
                        }
                    }
                }
                in.close();
                con.disconnect();
                double sec = (System.nanoTime() - start) / 1e9;
                if (sec > 0) down = bytes * 8.0 / sec / 1e6;
            } catch (Exception ignored) {}

            setText(tvStatus, getString(R.string.testing_up));
            try {
                byte[] payload = new byte[1000000];
                java.util.Arrays.fill(payload, (byte) 65);
                HttpURLConnection upCon = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
                upCon.setRequestMethod("POST");
                upCon.setDoOutput(true);
                upCon.setConnectTimeout(15000);
                upCon.setReadTimeout(30000);
                upCon.setFixedLengthStreamingMode(payload.length);
                long upStart = System.nanoTime();
                OutputStream os = upCon.getOutputStream();
                // Chunks me likho taake upload ke dauran bhi live speed nazar aaye
                int chunk = 32768;
                long upBytes = 0, upLastUi = upStart;
                for (int off = 0; off < payload.length; off += chunk) {
                    int len = Math.min(chunk, payload.length - off);
                    os.write(payload, off, len);
                    upBytes += len;
                    long now = System.nanoTime();
                    if (now - upLastUi > 200_000_000L) {
                        upLastUi = now;
                        double sec = (now - upStart) / 1e9;
                        if (sec > 0.2) {
                            double liveUp = upBytes * 8.0 / sec / 1e6;
                            setText(tvMainSpeed, String.format(Locale.US, "%.1f", liveUp));
                            setText(tvUpload, String.format(Locale.US, "%.1f %s", liveUp, getString(R.string.mbps)));
                        }
                    }
                }
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
                String mbps = getString(R.string.mbps);
                tvMainSpeed.setText(fd >= 0 ? String.format(Locale.US, "%.1f", fd) : "--");
                tvDownload.setText(fd >= 0 ? String.format(Locale.US, "%.2f %s", fd, mbps) : "--");
                tvUpload.setText(fu >= 0 ? String.format(Locale.US, "%.2f %s", fu, mbps) : "--");
                if (fd < 0 && fu < 0) {
                    tvStatus.setText(R.string.speed_failed);
                } else {
                    tvStatus.setText(R.string.speed_done);
                    saveResult(fd, fu, fp, fj);
                    showPattern();
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
            while (arr.length() > 100) arr.remove(0);
            prefs.edit().putString("speed_tests", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
