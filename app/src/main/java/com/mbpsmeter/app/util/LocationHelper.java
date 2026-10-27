package com.mbpsmeter.app.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

/**
 * LocationHelper — actively GPS/Network se location maangta hai.
 * getLastKnownLocation aksar null hota hai, is liye requestLocationUpdates zaroori hai.
 */
public class LocationHelper {

    public interface Listener {
        void onLocation(Location loc);
    }

    private static Location lastLocation;
    private static LocationManager lm;
    private static LocationListener internalListener;
    private static Listener callback;
    private static int refCount = 0;

    /** Location updates shuru karo. Har start() ke liye stop() zaroori hai. */
    public static synchronized void start(Context ctx) {
        try {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (lm == null) {
                lm = (LocationManager) ctx.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            }
            if (refCount == 0 && lm != null) {
                // Pehle last-known try karo — teeno providers me se sab se accurate wali
                try {
                    Location best = null;
                    String[] provs = { LocationManager.GPS_PROVIDER,
                            LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER };
                    for (String pr : provs) {
                        try {
                            Location l = lm.getLastKnownLocation(pr);
                            if (l == null) continue;
                            if (best == null) { best = l; continue; }
                            float accB = best.hasAccuracy() ? best.getAccuracy() : Float.MAX_VALUE;
                            float accL = l.hasAccuracy() ? l.getAccuracy() : Float.MAX_VALUE;
                            if (accL < accB) best = l;
                        } catch (Exception ignored) {}
                    }
                    if (best != null) lastLocation = best;
                } catch (Exception ignored) {}

                internalListener = new LocationListener() {
                    @Override public void onLocationChanged(Location location) {
                        if (location != null) {
                            // Coarse network fix achhi GPS fix ko overwrite na kare —
                            // yahi "location thori galat" wali ghalti ki jar thi.
                            if (!isBetterFix(location, lastLocation)) return;
                            lastLocation = location;
                            Listener cb;
                            synchronized (LocationHelper.class) { cb = callback; }
                            if (cb != null) {
                                try { cb.onLocation(location); } catch (Exception ignored) {}
                            }
                        }
                    }
                    @Override public void onStatusChanged(String p, int s, Bundle e) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                };
                try {
                    // GPS: 2 sec / 5 meter. Network: 5 sec / 10 meter.
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, internalListener);
                } catch (Exception ignored) {}
                try {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, internalListener);
                } catch (Exception ignored) {}
            }
            refCount++;
        } catch (Exception ignored) {}
    }

    /** Location updates band karo. */
    public static synchronized void stop() {
        try {
            if (refCount > 0) refCount--;
            if (refCount == 0 && lm != null && internalListener != null) {
                try { lm.removeUpdates(internalListener); } catch (Exception ignored) {}
                internalListener = null;
            }
        } catch (Exception ignored) {}
    }

    /** Aakhri maloom location (null ho sakti hai). */
    public static synchronized Location getLast() {
        return lastLocation;
    }

    /** Nayi fix purani se behtar hai? Zyada accurate, ya khaas tor par zyada taza. */
    private static boolean isBetterFix(Location fix, Location last) {
        if (last == null) return true;
        float accFix = fix.hasAccuracy() ? fix.getAccuracy() : Float.MAX_VALUE;
        float accLast = last.hasAccuracy() ? last.getAccuracy() : Float.MAX_VALUE;
        long dt = fix.getTime() - last.getTime();
        if (accFix <= accLast) return true;              // zyada ya barabar accurate
        if (dt > 30000 && accFix < accLast + 50f) return true; // purani fix boht purani ho gayi
        return false;
    }

    public static synchronized void setListener(Listener l) {
        callback = l;
    }

    public static synchronized void clearListener() {
        callback = null;
    }
}
