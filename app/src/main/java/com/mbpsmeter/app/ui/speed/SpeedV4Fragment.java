package com.mbpsmeter.app.ui.speed;

import android.content.Context;
import android.content.Intent;
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

    private TextView tvMainSpeed, tvDownload, tvUpload, tvPing, tvJitter, tvStatus, tvPattern, tvWorks;
    private Button btnStart, btnShareSpeed;
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";
    private volatile boolean running = false;

    // Speed engine v6.3: parallel streams + muddat wala hisaab.
    // Pehle 1.5s (connection banna + slow-start ramp) ginne se bahar rakhe jate hain —
    // sirf woh bytes gine jate hain jo is window me asal me wire se guzre.
    private static final int DL_THREADS = 4;
    private static final int UL_THREADS = 3;
    private static final long TEST_MS = 9000;
    private static final long RAMP_MS = 1500;
    private final java.util.concurrent.atomic.AtomicLong dlBytes = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong ulBytes = new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean dlStop = false, ulStop = false;
    private double lastDown = -1, lastUp = -1, lastPing = -1;

    // Background thread ke liye pehle se (UI thread par) liye gaye strings.
    // Thread ke andar getString() MAT lagao: user tab badal de to fragment
    // detach ho jata hai aur requireContext() "not attached to a context"
    // crash karta hai — v1.0 ka Last Error yehi tha.
    private String sMbps = "Mbps", sMs = "ms", sTestingDown = "", sTestingUp = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_speed_v4, c, false);
        // Page hamesha bilkul top se khule — title kata hua na aaye
        if (v instanceof android.widget.ScrollView) {
            final android.widget.ScrollView sv = (android.widget.ScrollView) v;
            sv.post(() -> sv.scrollTo(0, 0));
        }
        tvMainSpeed = v.findViewById(R.id.tvMainSpeed);
        tvDownload = v.findViewById(R.id.tvDownload);
        tvUpload = v.findViewById(R.id.tvUpload);
        tvPing = v.findViewById(R.id.tvPing);
        tvJitter = v.findViewById(R.id.tvJitter);
        tvStatus = v.findViewById(R.id.tvStatus);
        tvPattern = v.findViewById(R.id.tvPattern);
        tvWorks = v.findViewById(R.id.tvWorks);
        btnStart = v.findViewById(R.id.btnStartSpeed);
        btnStart.setOnClickListener(x -> runTest());
        btnShareSpeed = v.findViewById(R.id.btnShareSpeed);
        if (btnShareSpeed != null) btnShareSpeed.setOnClickListener(x -> shareResult());
        showPattern();
        return v;
    }

    /** Meteor-style: is speed par kaunsi apps chalengi */
    private void showWorksOnSpeed(double down, double up) {
        if (tvWorks == null || !isAdded()) return;
        StringBuilder sb = new StringBuilder(getString(R.string.works_title));
        sb.append("\n").append(worksRow("YouTube 4K", down >= 20));
        sb.append(worksRow("YouTube HD (1080p)", down >= 5));
        sb.append(worksRow("YouTube (720p)", down >= 2.5));
        sb.append(worksRow("WhatsApp video call", down >= 1.5 && up >= 1));
        sb.append(worksRow("WhatsApp voice / browsing", down >= 0.5));
        final String t = sb.toString();
        ui(() -> {
            tvWorks.setText(t);
            tvWorks.setVisibility(View.VISIBLE);
            if (btnShareSpeed != null) btnShareSpeed.setVisibility(View.VISIBLE);
        });
    }

    private String worksRow(String app, boolean ok) {
        return "\n" + (ok ? "✅ " : "❌ ") + app;
    }

    private void shareResult() {
        if (getContext() == null || lastDown < 0) return;
        String text = String.format(Locale.US, getString(R.string.share_text),
                lastDown, Math.max(0, lastUp), Math.max(0, lastPing));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_result)));
        } catch (Exception ignored) {}
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
            if (tvWorks != null) tvWorks.setVisibility(View.GONE);
            if (btnShareSpeed != null) btnShareSpeed.setVisibility(View.GONE);
        });
        setText(tvStatus, getString(R.string.measuring_ping));
        // Strings abhi (attached, UI thread par) le lo — thread inhin ko use karega
        sMbps = getString(R.string.mbps);
        sMs = getString(R.string.ms);
        sTestingDown = getString(R.string.testing_down);
        sTestingUp = getString(R.string.testing_up);

        new Thread(() -> {
            double pingMs = -1, jitterMs = -1, down = -1, up = -1;

            try {
                // 5 samples: sab se slow wala (aksar pehle connection/TLS ki wajah se hota hai)
                // bahar, ping = sab se tez sample, jitter = lagatar samples ka ausat farq.
                // Purana tareeqa (3 samples ka max-min) ek slow sample se 600+ ms jitter bana deta tha.
                java.util.ArrayList<Double> samples = new java.util.ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    long t0 = System.nanoTime();
                    HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=1000").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", UA);
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[4096];
                    while (in.read(buf) != -1) {}
                    in.close();
                    c.disconnect();
                    samples.add((System.nanoTime() - t0) / 1e6);
                }
                if (!samples.isEmpty()) {
                    int worst = 0;
                    for (int i = 1; i < samples.size(); i++) {
                        if (samples.get(i) > samples.get(worst)) worst = i;
                    }
                    if (samples.size() > 2) samples.remove(worst);
                    double min = Double.MAX_VALUE;
                    for (double s : samples) if (s < min) min = s;
                    pingMs = min;
                    if (samples.size() > 1) {
                        double sum = 0;
                        for (int i = 1; i < samples.size(); i++) {
                            sum += Math.abs(samples.get(i) - samples.get(i - 1));
                        }
                        jitterMs = sum / (samples.size() - 1);
                    } else {
                        jitterMs = 0;
                    }
                }
                double p = pingMs, j = jitterMs;
                ui(() -> {
                    if (!isAdded()) return;
                    tvPing.setText(String.format(Locale.US, "%.0f %s", p, sMs));
                    tvJitter.setText(String.format(Locale.US, "%.0f %s", j, sMs));
                });
            } catch (Exception ignored) {}

            setText(tvStatus, sTestingDown);
            down = measureDownload();

            setText(tvStatus, sTestingUp);
            up = measureUpload();

            double fd = down, fu = up, fp = pingMs, fj = jitterMs;
            ui(() -> {
                running = false;
                if (!isAdded()) return;
                btnStart.setEnabled(true);
                btnStart.setAlpha(1f);
                String mbps = sMbps;
                tvMainSpeed.setText(fd >= 0 ? String.format(Locale.US, "%.1f", fd) : "--");
                tvDownload.setText(fd >= 0 ? String.format(Locale.US, "%.2f %s", fd, mbps) : "--");
                tvUpload.setText(fu >= 0 ? String.format(Locale.US, "%.2f %s", fu, mbps) : "--");
                if (fd < 0 && fu < 0) {
                    tvStatus.setText(R.string.speed_failed);
                } else {
                    tvStatus.setText(fu < 0 ? R.string.speed_up_failed : R.string.speed_done);
                    lastDown = fd;
                    lastUp = fu;
                    lastPing = fp;
                    saveResult(fd, fu, fp, fj);
                    showPattern();
                    showWorksOnSpeed(fd, fu);
                }
            });
        }).start();
    }

    /**
     * Download speed: 4 parallel streams, 9 second ki muddat.
     * Purana tareeqa ek hi file ko shuru se aakhir tak naapta tha — connection
     * banne aur slow-start ramp ka waqt bhi shamil hota tha, is liye number
     * asal speed se hamesha alag (aksar kam) aata tha. Ab ramp ke baad wali
     * window ke asal bytes gine jate hain — yehi tareeqa baray speed-test
     * apps use karte hain.
     */
    private double measureDownload() {
        dlBytes.set(0);
        dlStop = false;
        Thread[] ts = new Thread[DL_THREADS];
        for (int i = 0; i < DL_THREADS; i++) {
            ts[i] = new Thread(() -> {
                byte[] buf = new byte[32768];
                while (!dlStop) {
                    HttpURLConnection con = null;
                    try {
                        con = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=50000000").openConnection();
                        con.setConnectTimeout(10000);
                        con.setReadTimeout(15000);
                        con.setRequestProperty("User-Agent", UA);
                        InputStream in = con.getInputStream();
                        int n;
                        while (!dlStop && (n = in.read(buf)) != -1) {
                            dlBytes.addAndGet(n);
                        }
                        try { in.close(); } catch (Exception ignored) {}
                    } catch (Exception e) {
                        if (!dlStop) {
                            try { Thread.sleep(250); } catch (Exception ignored) {}
                        }
                    } finally {
                        if (con != null) con.disconnect();
                    }
                }
            });
            ts[i].start();
        }
        double result = runWindow(dlBytes, true);
        dlStop = true;
        for (Thread t : ts) {
            try { t.join(1500); } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Upload speed: 3 parallel streams jo poori muddat data bhejtay rehte hain.
     * Ramp (pehle 1.5s) guzarne ke baad socket buffer bhar jata hai, phir
     * write() sirf asal wire speed par aage barhta hai — is liye window me
     * gine gaye bytes asal bheje gaye bytes hote hain. (v6.2 me sirf likhne
     * ka waqt ginne se buffer wala jhoota 310 Mbps ban gaya tha.)
     */
    private double measureUpload() {
        String[] upUrls = {
                "https://speed.cloudflare.com/__up",
                "https://httpbin.org/post"
        };
        for (String upUrl : upUrls) {
            ulBytes.set(0);
            ulStop = false;
            Thread[] ts = new Thread[UL_THREADS];
            for (int i = 0; i < UL_THREADS; i++) {
                ts[i] = new Thread(() -> {
                    byte[] chunk = new byte[32768];
                    java.util.Arrays.fill(chunk, (byte) 65);
                    while (!ulStop) {
                        HttpURLConnection con = null;
                        try {
                            con = (HttpURLConnection) new URL(upUrl).openConnection();
                            con.setRequestMethod("POST");
                            con.setDoOutput(true);
                            con.setConnectTimeout(10000);
                            con.setReadTimeout(15000);
                            con.setRequestProperty("User-Agent", UA);
                            con.setRequestProperty("Content-Type", "application/octet-stream");
                            con.setFixedLengthStreamingMode(20_000_000);
                            OutputStream os = con.getOutputStream();
                            long sent = 0;
                            while (!ulStop && sent < 20_000_000L) {
                                int len = (int) Math.min(chunk.length, 20_000_000L - sent);
                                os.write(chunk, 0, len);
                                sent += len;
                                ulBytes.addAndGet(len);
                            }
                            os.flush();
                            try { os.close(); } catch (Exception ignored) {}
                        } catch (Exception e) {
                            if (!ulStop) {
                                try { Thread.sleep(250); } catch (Exception ignored) {}
                            }
                        } finally {
                            if (con != null) con.disconnect();
                        }
                    }
                });
                ts[i].start();
            }
            double result = runWindow(ulBytes, false);
            ulStop = true;
            for (Thread t : ts) {
                try { t.join(1500); } catch (Exception ignored) {}
            }
            if (result > 0) return result;
        }
        return -1;
    }

    /**
     * Test ki muddat (TEST_MS) chalao; ramp (RAMP_MS) ke baad wali window me
     * counter jitna barha, usi se speed (Mbps) banao. Data bilkul na aaye to -1.
     * Saath saath live number bhi UI par dikhate raho.
     */
    private double runWindow(java.util.concurrent.atomic.AtomicLong counter, boolean isDownload) {
        // Naye phase ke shuru me purana number saaf — warna download ki aakhri
        // reading upload ke shuru me chipki rehti hai (flux ki ek wajah ye bhi thi)
        setText(tvMainSpeed, "--");
        long t0 = System.nanoTime();
        long rampBytes = -1;
        // Live number ke liye rolling window: har 150ms par (waqt, kul bytes) ka
        // sample rakho aur speed pichhle ~2.5 second ke samples se nikalo.
        // Purana tareeqa har 300ms ke chhote tukre ki speed dikhata tha — data
        // ruk-ruk kar aane par number 0 aur asal speed ke darmiyan uchhalta tha
        // (Zahid ki shikayat: "0 se shuffle ho rahi thi 13 ke darmiyan").
        // v1.0.3: window 1.5s -> 2.5s, smoothing mazboot, UI update 300ms par —
        // number ab glide karega, dance nahi. Final result ka hisaab unchanged.
        java.util.ArrayDeque<long[]> hist = new java.util.ArrayDeque<>();
        double shown = -1;
        long lastPushMs = -1;
        while (true) {
            long el = (System.nanoTime() - t0) / 1_000_000L;
            if (el >= TEST_MS) break;
            if (el >= RAMP_MS && rampBytes < 0) {
                rampBytes = counter.get();
                hist.clear();
                shown = -1;
                lastPushMs = -1;
            }
            if (el > RAMP_MS) {
                long nowMs = (System.nanoTime() - t0) / 1_000_000L;
                hist.addLast(new long[]{nowMs, counter.get()});
                while (hist.size() > 2 && nowMs - hist.peekFirst()[0] > 2500) hist.pollFirst();
                if (hist.size() >= 2) {
                    long[] first = hist.peekFirst();
                    long[] last = hist.peekLast();
                    double sec = (last[0] - first[0]) / 1000.0;
                    if (sec >= 0.8) {
                        double live = (last[1] - first[1]) * 8.0 / sec / 1e6;
                        // Mazboot smoothing: number aahista glide kare, jhatke se nahi
                        shown = shown < 0 ? live : (0.80 * shown + 0.20 * live);
                        if (lastPushMs < 0 || nowMs - lastPushMs >= 300) {
                            lastPushMs = nowMs;
                            final String main = String.format(Locale.US, "%.1f", shown);
                            setText(tvMainSpeed, main);
                            if (isDownload) {
                                setText(tvDownload, String.format(Locale.US, "%.1f %s", shown, sMbps));
                            } else {
                                setText(tvUpload, String.format(Locale.US, "%.1f %s", shown, sMbps));
                            }
                        }
                    }
                }
            }
            try { Thread.sleep(150); } catch (Exception e) { break; }
        }
        long totalMs = (System.nanoTime() - t0) / 1_000_000L;
        if (rampBytes < 0) rampBytes = counter.get();
        long windowBytes = counter.get() - rampBytes;
        double windowSec = (totalMs - RAMP_MS) / 1000.0;
        if (windowBytes <= 0 || windowSec <= 0) return -1;
        return windowBytes * 8.0 / windowSec / 1e6;
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
