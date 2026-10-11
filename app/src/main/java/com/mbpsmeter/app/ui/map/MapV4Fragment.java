package com.mbpsmeter.app.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

/**
 * MAP V6: colored pins + best spot distance/direction + Open in Maps.
 */
public class MapV4Fragment extends Fragment {

    private TextView tvBestSignal, tvBestLocation, tvBestDistance;
    private SharedPreferences prefs;
    private MapView osmMap;

    private float bestLat = 0, bestLng = 0;
    private int bestDbm = -120;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map_v4, container, false);
        tvBestSignal = v.findViewById(R.id.tvBestSignal);
        tvBestLocation = v.findViewById(R.id.tvBestLocation);
        tvBestDistance = v.findViewById(R.id.tvBestDistance);
        v.findViewById(R.id.btnNavigateBest).setOnClickListener(x -> centerBest());
        v.findViewById(R.id.btnOpenMap).setOnClickListener(x -> openInMaps());
        osmMap = v.findViewById(R.id.osmMap);
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.setBuiltInZoomControls(false);
        loadAll();
        return v;
    }

    private int colorFor(int dbm) {
        if (dbm >= -80) return Color.parseColor("#22C55E");
        if (dbm >= -95) return Color.parseColor("#F59E0B");
        return Color.parseColor("#EF4444");
    }

    private BitmapDrawable dotIcon(int color) {
        int s = 72;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.WHITE);
        c.drawCircle(s / 2f, s / 2f, 30f, outer);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(color);
        c.drawCircle(s / 2f, s / 2f, 22f, inner);
        return new BitmapDrawable(getResources(), bmp);
    }

    private void loadAll() {
        try {
            osmMap.getOverlays().clear();
            String json = prefs.getString("signals", "[]");
            JSONArray arr = new JSONArray(json);
            bestLat = prefs.getFloat("best_lat", 0);
            bestLng = prefs.getFloat("best_lng", 0);
            bestDbm = prefs.getInt("best_dbm", -120);

            GeoPoint center = new GeoPoint(24.8607, 67.0011);
            if (arr.length() > 0) {
                JSONObject last = arr.getJSONObject(arr.length() - 1);
                center = new GeoPoint(last.optDouble("lat", 24.8607), last.optDouble("lng", 67.0011));
            } else if (bestLat != 0) {
                center = new GeoPoint(bestLat, bestLng);
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int dbm = o.optInt("dbm", -80);
                GeoPoint p = new GeoPoint(o.optDouble("lat", 24.8607), o.optDouble("lng", 67.0011));
                Marker m = new Marker(osmMap);
                m.setPosition(p);
                String title = dbm + " dBm | " + o.optString("type", "MANUAL");
                if (!o.optString("room", "").isEmpty()) title = o.optString("room", "") + " | " + title;
                m.setTitle(title);
                m.setIcon(dotIcon(colorFor(dbm)));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                osmMap.getOverlays().add(m);
            }

            if (bestLat != 0 && bestDbm > -120) {
                Marker b = new Marker(osmMap);
                b.setPosition(new GeoPoint(bestLat, bestLng));
                b.setTitle(getString(R.string.best) + ": " + bestDbm + " dBm");
                b.setIcon(dotIcon(Color.parseColor("#00E5FF")));
                b.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                osmMap.getOverlays().add(b);
            }

            osmMap.getController().setZoom(15.0);
            osmMap.getController().setCenter(center);
            osmMap.invalidate();

            updateBestCard();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateBestCard() {
        if (bestDbm <= -120 || bestLat == 0) {
            tvBestSignal.setText(getString(R.string.best) + ": -- dBm");
            tvBestDistance.setText(R.string.no_reading_yet);
            tvBestLocation.setText("");
            return;
        }
        tvBestSignal.setText(getString(R.string.best) + ": " + bestDbm + " dBm");
        tvBestLocation.setText(String.format(Locale.getDefault(), "%.5f, %.5f", bestLat, bestLng));

        Location me = myLocation();
        if (me != null) {
            Location target = new Location("best");
            target.setLatitude(bestLat);
            target.setLongitude(bestLng);
            float dist = me.distanceTo(target);
            String dir = compass(me.bearingTo(target));
            String distStr = dist < 1000
                    ? String.format(Locale.getDefault(), "%.0f m", dist)
                    : String.format(Locale.getDefault(), "%.1f km", dist / 1000);
            tvBestDistance.setText(String.format(getString(R.string.best_spot_go), distStr, dir));
        } else {
            tvBestDistance.setText(R.string.best_spot_found);
        }
    }

    private String compass(float bearing) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = Math.round(((bearing + 360) % 360) / 45f) % 8;
        return dirs[idx];
    }

    private Location myLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (Exception ignored) { return null; }
    }

    private void centerBest() {
        if (bestLat == 0) {
            Toast.makeText(getContext(), R.string.first_record, Toast.LENGTH_SHORT).show();
            return;
        }
        osmMap.getController().animateTo(new GeoPoint(bestLat, bestLng));
        osmMap.getController().setZoom(17.0);
    }

    private void openInMaps() {
        if (bestLat == 0) {
            Toast.makeText(getContext(), R.string.first_record, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse("geo:" + bestLat + "," + bestLng + "?q=" + bestLat + "," + bestLng + "(best)");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.no_maps_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onResume() { super.onResume(); if (osmMap != null) { osmMap.onResume(); loadAll(); } }
    @Override public void onPause() { super.onPause(); if (osmMap != null) osmMap.onPause(); }
}
