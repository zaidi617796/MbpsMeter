package com.mbpsmeter.app.util;

import android.content.SharedPreferences;

import com.mbpsmeter.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * SignalAdvisor: saved readings se tafseeli rehnumai.
 * - behtreen spot ki taraf direction (bearing, fasla, compass simt)
 * - har kamre me corner-wise stats (sab se acha corner kaun sa)
 * - outdoor trend: gradient se maloom karein signal kis taraf behtar ho raha hai
 *
 * Pure logic - koi Android UI dependency nahi (SharedPreferences/org.json ke siwa).
 */
public class SignalAdvisor {

    // corner ids: 0=center, 1=top-left, 2=top-right, 3=bottom-left, 4=bottom-right
    public static int cornerRes(int c) {
        switch (c) {
            case 1: return R.string.corner_tl;
            case 2: return R.string.corner_tr;
            case 3: return R.string.corner_bl;
            case 4: return R.string.corner_br;
            default: return R.string.corner_center;
        }
    }

    // compass index: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW
    public static int dirRes(int idx) {
        switch (idx) {
            case 1: return R.string.dir_ne;
            case 2: return R.string.dir_e;
            case 3: return R.string.dir_se;
            case 4: return R.string.dir_s;
            case 5: return R.string.dir_sw;
            case 6: return R.string.dir_w;
            case 7: return R.string.dir_nw;
            default: return R.string.dir_n;
        }
    }

    /** bearing (0-360) ko 8 compass simton me se aik me badlein */
    public static int compassIndex(double bearingDeg) {
        double b = ((bearingDeg % 360) + 360) % 360;
        return (int) Math.round(b / 45.0) % 8;
    }

    /** point 1 se point 2 ki simt (degrees, 0=North, clockwise) */
    public static double bearingTo(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1r = Math.toRadians(lat1);
        double lat2r = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(lat2r);
        double x = Math.cos(lat1r) * Math.sin(lat2r)
                - Math.sin(lat1r) * Math.cos(lat2r) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    /** do points ke darmiyan fasla (meters, haversine) */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    // ---------- behtreen spot ----------

    public static class BestSpot {
        public double lat;
        public double lng;
        public int dbm;
    }

    /** ab tak ki behtreen reading (Live/History se best_lat/best_lng/best_dbm) */
    public static BestSpot bestSpot(SharedPreferences prefs) {
        if (!prefs.contains("best_dbm")) return null;
        BestSpot s = new BestSpot();
        s.lat = prefs.getFloat("best_lat", 0);
        s.lng = prefs.getFloat("best_lng", 0);
        s.dbm = prefs.getInt("best_dbm", -120);
        if (s.lat == 0 && s.lng == 0) return null;
        return s;
    }

    // ---------- room corner stats ----------

    public static class CornerStat {
        public String room;
        public final int[] sum = new int[5];
        public final int[] count = new int[5];

        public int total() {
            int t = 0;
            for (int c : count) t += c;
            return t;
        }

        public int bestCorner() {
            int best = -1, bestAvg = -999;
            for (int i = 0; i < 5; i++) {
                if (count[i] > 0) {
                    int a = sum[i] / count[i];
                    if (a > bestAvg) { bestAvg = a; best = i; }
                }
            }
            return best;
        }

        public int avgOf(int i) {
            return count[i] == 0 ? -999 : sum[i] / count[i];
        }
    }

    /** har kamre ke har corner ka average dBm */
    public static List<CornerStat> roomCornerStats(SharedPreferences prefs, List<String> rooms) {
        List<CornerStat> out = new ArrayList<>();
        for (String r : rooms) {
            CornerStat cs = new CornerStat();
            cs.room = r;
            out.add(cs);
        }
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String room = o.optString("room", "");
                if (room.isEmpty()) continue;
                int corner = o.optInt("corner", 0);
                if (corner < 0 || corner > 4) corner = 0;
                for (CornerStat cs : out) {
                    if (cs.room.equals(room)) {
                        cs.sum[corner] += o.optInt("dbm", -120);
                        cs.count[corner]++;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ---------- outdoor trend ----------

    public static class Trend {
        /** simt (degrees) jidhar signal behtar ho raha hai */
        public double bearing;
        /** 0..1 - kitna bharosa hai */
        public double r2;
        public int n;
    }

    /**
     * Outdoor readings (ROOM wali nikaal kar) par linear regression:
     * dbm = a*north_m + b*east_m + c. Gradient (a,b) behtri ki simt batata hai.
     * Kam az kam 6 points aur 15m ka phailao chahiye, warna null.
     */
    public static Trend outdoorTrend(SharedPreferences prefs) {
        ArrayList<double[]> pts = new ArrayList<>(); // lat, lng, dbm
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if ("ROOM".equals(o.optString("type", ""))) continue;
                double lat = o.optDouble("lat", 0);
                double lng = o.optDouble("lng", 0);
                if (lat == 0 && lng == 0) continue;
                pts.add(new double[]{lat, lng, o.optInt("dbm", -120)});
            }
        } catch (Exception ignored) {}
        if (pts.size() < 6) return null;

        double maxD = 0;
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                maxD = Math.max(maxD, distanceMeters(
                        pts.get(i)[0], pts.get(i)[1], pts.get(j)[0], pts.get(j)[1]));
            }
        }
        if (maxD < 15) return null;

        double mLat = 0, mLng = 0;
        for (double[] p : pts) { mLat += p[0]; mLng += p[1]; }
        mLat /= pts.size();
        mLng /= pts.size();
        double cosLat = Math.cos(Math.toRadians(mLat));

        int n = pts.size();
        double s1 = 0, s2 = 0, sy = 0;
        double s11 = 0, s22 = 0, s12 = 0, s1y = 0, s2y = 0, syy = 0;
        for (double[] p : pts) {
            double x1 = (p[0] - mLat) * 111320.0;
            double x2 = (p[1] - mLng) * 111320.0 * cosLat;
            double y = p[2];
            s1 += x1; s2 += x2; sy += y;
            s11 += x1 * x1; s22 += x2 * x2; s12 += x1 * x2;
            s1y += x1 * y; s2y += x2 * y; syy += y * y;
        }
        double c11 = s11 - s1 * s1 / n;
        double c22 = s22 - s2 * s2 / n;
        double c12 = s12 - s1 * s2 / n;
        double c1y = s1y - s1 * sy / n;
        double c2y = s2y - s2 * sy / n;
        double cyy = syy - sy * sy / n;
        double denom = c11 * c22 - c12 * c12;
        if (Math.abs(denom) < 1e-9 || cyy < 1e-9) return null;

        double a = (c22 * c1y - c12 * c2y) / denom; // dBm per meter (north)
        double b = (c11 * c2y - c12 * c1y) / denom; // dBm per meter (east)
        if (Math.hypot(a, b) < 1e-9) return null;

        double ssRes = cyy - a * c1y - b * c2y;
        double r2 = 1 - ssRes / cyy;
        if (r2 < 0) r2 = 0;
        if (r2 > 1) r2 = 1;

        Trend t = new Trend();
        t.bearing = (Math.toDegrees(Math.atan2(b, a)) + 360.0) % 360.0;
        t.r2 = r2;
        t.n = n;
        return t;
    }
}
