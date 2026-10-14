package com.mbpsmeter.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Supabase cloud backup/restore (PostgREST, koi extra library nahi).
 * Settings: prefs "sb_url" (https://xyz.supabase.co) aur "sb_key" (anon key).
 * Tables: readings, speed_tests, drops - har row me device_id.
 * Backup = is device ka cloud copy replace. Restore = cloud copy local me wapas.
 */
public class SupabaseSync {

    // ================= SUPABASE SETUP =================
    // Apna Project URL aur anon key yahan quotes ke andar paste karo.
    // Supabase dashboard -> Project Settings (gear icon) -> API se copy karo.
    // Ye key app me embed karne ke liye hi bani hai (anon/public key).
    private static final String SB_URL = "https://sogugyourgaacustjmjw.supabase.co";
    private static final String SB_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNvZ3VneW91cmdhYWN1c3RqbWp3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4MTYyNjUsImV4cCI6MjEwNzM5MjI2NX0.9i--Lreqv2Pa5BUTo5AhEHja-a3uf0uJfuwawwuKFf4";
    /** Sirf is se strong (ya equal) readings cloud par upload hongi. */
    private static final int STRONG_DBM = -70;
    // ==================================================

    public interface Cb {
        void onResult(boolean ok, String msg);
    }

    /** Prefs me user value ho to wahi, warna upar wali hardcoded value */
    private static String effUrl(SharedPreferences p) {
        String u = p.getString("sb_url", "").trim();
        if (u.isEmpty()) u = SB_URL.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String effKey(SharedPreferences p) {
        String k = p.getString("sb_key", "").trim();
        if (k.isEmpty()) k = SB_KEY.trim();
        return k;
    }

    public static boolean hasConfig(SharedPreferences p) {
        String u = effUrl(p), k = effKey(p);
        return !u.isEmpty() && !u.equals("https://xyz.supabase.co")
                && !k.isEmpty() && !k.equals("PASTE_YOUR_ANON_KEY_HERE");
    }

    public static String deviceId(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null ? "unknown" : id;
    }

    private static String base(SharedPreferences p) {
        return effUrl(p) + "/rest/v1/";
    }

    private static HttpURLConnection conn(SharedPreferences p, String method,
                                          String table, String query) throws Exception {
        URL url = new URL(base(p) + table + (query == null ? "" : query));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        String key = effKey(p);
        c.setRequestProperty("apikey", key);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        return c;
    }

    private static String readAll(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        String body = "";
        try {
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A");
                body = sc.hasNext() ? sc.next() : "";
                sc.close();
            }
        } catch (Exception ignored) {}
        if (code >= 400) {
            throw new Exception("HTTP " + code + (body.isEmpty() ? "" : ": "
                    + body.substring(0, Math.min(100, body.length()))));
        }
        return body;
    }

    private static void post(Handler h, Cb cb, boolean ok, String msg) {
        h.post(() -> cb.onResult(ok, msg));
    }

    // ---------- backup ----------

    public static void backup(Context ctx, Cb cb) {
        SharedPreferences p = ctx.getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String dev = deviceId(ctx);
                backupTable(p, dev, "readings", mapReadings(p, dev));
                backupTable(p, dev, "speed_tests", mapSpeed(p, dev));
                backupTable(p, dev, "drops", mapDrops(p, dev));
                post(h, cb, true, "");
            } catch (Exception e) {
                post(h, cb, false, e.getMessage() == null ? "error" : e.getMessage());
            }
        }).start();
    }

    private static void backupTable(SharedPreferences p, String dev,
                                    String table, JSONArray rows) throws Exception {
        HttpURLConnection del = conn(p, "DELETE", table, "?device_id=eq." + dev);
        readAll(del);
        del.disconnect();
        for (int i = 0; i < rows.length(); i += 200) {
            JSONArray chunk = new JSONArray();
            for (int j = i; j < Math.min(i + 200, rows.length()); j++) {
                chunk.put(rows.getJSONObject(j));
            }
            HttpURLConnection ins = conn(p, "POST", table, "");
            ins.setRequestProperty("Prefer", "return=minimal");
            ins.setDoOutput(true);
            try (OutputStream os = ins.getOutputStream()) {
                os.write(chunk.toString().getBytes(StandardCharsets.UTF_8));
            }
            readAll(ins);
            ins.disconnect();
        }
    }

    private static JSONArray mapReadings(SharedPreferences p, String dev) throws Exception {
        JSONArray src = new JSONArray(p.getString("signals", "[]"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.getJSONObject(i);
            int dbm = o.optInt("dbm", -120);
            // Sirf STRONG signal wali readings cloud par jayengi (user ki requirement)
            if (dbm < STRONG_DBM) continue;
            JSONObject n = new JSONObject();
            n.put("device_id", dev);
            n.put("dbm", dbm);
            n.put("time", o.optLong("time", 0));
            n.put("lat", o.optDouble("lat", 0));
            n.put("lng", o.optDouble("lng", 0));
            n.put("type", o.optString("type", ""));
            n.put("room", o.optString("room", ""));
            n.put("corner", o.optInt("corner", 0));
            n.put("net", o.optString("net", ""));
            n.put("op", o.optString("op", ""));
            out.put(n);
        }
        return out;
    }

    private static JSONArray mapSpeed(SharedPreferences p, String dev) throws Exception {
        JSONArray src = new JSONArray(p.getString("speed_tests", "[]"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.getJSONObject(i);
            JSONObject n = new JSONObject();
            n.put("device_id", dev);
            n.put("time", o.optLong("time", 0));
            n.put("down_mbps", o.optDouble("down", 0));
            n.put("up_mbps", o.optDouble("up", 0));
            n.put("ping_ms", o.optDouble("ping", 0));
            n.put("jitter_ms", o.optDouble("jitter", 0));
            out.put(n);
        }
        return out;
    }

    private static JSONArray mapDrops(SharedPreferences p, String dev) throws Exception {
        JSONArray src = new JSONArray(p.getString("drops", "[]"));
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.getJSONObject(i);
            JSONObject n = new JSONObject();
            n.put("device_id", dev);
            n.put("start_ts", o.optLong("start", 0));
            n.put("end_ts", o.optLong("end", 0));
            n.put("min_dbm", o.optInt("minDbm", -120));
            n.put("lat", o.optDouble("lat", 0));
            n.put("lng", o.optDouble("lng", 0));
            out.put(n);
        }
        return out;
    }

    // ---------- restore ----------

    public static void restore(Context ctx, Cb cb) {
        SharedPreferences p = ctx.getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String dev = deviceId(ctx);
                JSONArray readings = fetch(p, dev, "readings");
                JSONArray speeds = fetch(p, dev, "speed_tests");
                JSONArray drops = fetch(p, dev, "drops");

                SharedPreferences.Editor ed = p.edit();
                JSONArray localSignals = unmapReadings(readings);
                ed.putString("signals", localSignals.toString());
                ed.putString("speed_tests", unmapSpeed(speeds).toString());
                ed.putString("drops", unmapDrops(drops).toString());
                // best spot dobara calculate
                int best = -999;
                double blat = 0, blng = 0;
                for (int i = 0; i < localSignals.length(); i++) {
                    JSONObject o = localSignals.getJSONObject(i);
                    int d = o.optInt("dbm", -999);
                    if (d > best) {
                        best = d;
                        blat = o.optDouble("lat", 0);
                        blng = o.optDouble("lng", 0);
                    }
                }
                if (best > -999) {
                    ed.putInt("best_dbm", best);
                    ed.putFloat("best_lat", (float) blat);
                    ed.putFloat("best_lng", (float) blng);
                }
                ed.apply();
                post(h, cb, true, "");
            } catch (Exception e) {
                post(h, cb, false, e.getMessage() == null ? "error" : e.getMessage());
            }
        }).start();
    }

    private static JSONArray fetch(SharedPreferences p, String dev, String table) throws Exception {
        HttpURLConnection g = conn(p, "GET", table,
                "?device_id=eq." + dev + "&select=*&order=time.asc");
        String body = readAll(g);
        g.disconnect();
        return new JSONArray(body);
    }

    private static JSONArray unmapReadings(JSONArray src) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.getJSONObject(i);
            JSONObject n = new JSONObject();
            n.put("dbm", o.optInt("dbm", -120));
            n.put("time", o.optLong("time", 0));
            n.put("lat", o.optDouble("lat", 0));
            n.put("lng", o.optDouble("lng", 0));
            n.put("type", o.optString("type", ""));
            n.put("room", o.optString("room", ""));
            n.put("corner", o.optInt("corner", 0));
            n.put("net", o.optString("net", ""));
            n.put("op", o.optString("op", ""));
            out.put(n);
        }
        return out;
    }

    private static JSONArray unmapSpeed(JSONArray src) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.getJSONObject(i);
            JSONObject n = new JSONObject();
            n.put("time", o.optLong("time", 0));
            n.put("down", o.optDouble("down_mbps", 0));
            n.put("up", o.optDouble("up_mbps", 0));
            n.put("ping", o.optDouble("ping_ms", 0));
            n.put("jitter", o.optDouble("jitter_ms", 0));
            out.put(n);
        }
        return out;
    }

    private static JSONArray unmapDrops(JSONArray src) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.getJSONObject(i);
            JSONObject n = new JSONObject();
            n.put("start", o.optLong("start_ts", 0));
            n.put("end", o.optLong("end_ts", 0));
            n.put("minDbm", o.optInt("min_dbm", -120));
            n.put("lat", o.optDouble("lat", 0));
            n.put("lng", o.optDouble("lng", 0));
            out.put(n);
        }
        return out;
    }
}
