package com.mbpsmeter.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TowerDb: user ki APNI readings se bana hua tower database.
 *
 * Har unique Cell ID ko ek "tower" mana jata hai. Tower ki location un GPS
 * points ka average (centroid) hai jahan ye cell pakda gaya tha — ye khambe
 * ki exact location NAHI, ek imaandar andaza hai, lekin signal dhoondne ke
 * liye kaafi hai.
 *
 * Koi internet, API key ya teesri company ki database nahi chahiye.
 */
public class TowerDb {

    public static class Tower {
        public String key;      // op|cell — taake do operators ka same cellId na takraye
        public String cellId;
        public String op;
        public String band;
        public double lat, lng; // andazan location (centroid)
        public int count;       // kitni readings is cell par hui
        public int avgDbm;
        public int bestDbm;
        public double bestLat, bestLng; // sab se strong reading kahan mili
    }

    /** Saved "signals" se towers nikalo, behtareen average signal pehle. */
    public static List<Tower> load(Context ctx) {
        List<Tower> out = new ArrayList<>();
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            Map<String, Tower> map = new LinkedHashMap<>();
            Map<String, double[]> acc = new LinkedHashMap<>(); // key -> [sumLat, sumLng, n, sumDbm]
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    String cell = o.optString("cell", "");
                    if (cell.isEmpty()) continue;
                    double lat = o.optDouble("lat", Double.NaN);
                    double lng = o.optDouble("lng", Double.NaN);
                    if (Double.isNaN(lat) || Double.isNaN(lng)) continue;
                    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) continue;
                    if (lat == 0 && lng == 0) continue;
                    int dbm = o.optInt("dbm", -120);
                    String op = o.optString("op", "");
                    String band = o.optString("band", "");
                    String key = op + "|" + cell;
                    Tower t = map.get(key);
                    if (t == null) {
                        t = new Tower();
                        t.key = key;
                        t.cellId = cell;
                        t.op = op;
                        t.band = band;
                        t.bestDbm = -999;
                        map.put(key, t);
                        acc.put(key, new double[]{0, 0, 0, 0});
                    }
                    double[] a = acc.get(key);
                    a[0] += lat; a[1] += lng; a[2] += 1; a[3] += dbm;
                    t.count++;
                    if (dbm > t.bestDbm) {
                        t.bestDbm = dbm;
                        t.bestLat = lat;
                        t.bestLng = lng;
                    }
                    if (t.band.isEmpty() && !band.isEmpty()) t.band = band;
                } catch (Exception ignored) {}
            }
            for (Map.Entry<String, Tower> e : map.entrySet()) {
                Tower t = e.getValue();
                double[] a = acc.get(e.getKey());
                if (a != null && a[2] > 0) {
                    t.lat = a[0] / a[2];
                    t.lng = a[1] / a[2];
                    t.avgDbm = (int) Math.round(a[3] / a[2]);
                    out.add(t);
                }
            }
        } catch (Exception ignored) {}
        Collections.sort(out, new Comparator<Tower>() {
            @Override public int compare(Tower a, Tower b) {
                return Integer.compare(b.avgDbm, a.avgDbm);
            }
        });
        return out;
    }
}
