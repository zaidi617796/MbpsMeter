package com.mbpsmeter.app.ui.map;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.R;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapV4Fragment extends Fragment {
    private TextView tvBestSignal, tvBestLocation;
    private SharedPreferences prefs;
    private MapView osmMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map_v4, container, false);
        tvBestSignal = v.findViewById(R.id.tvBestSignal);
        tvBestLocation = v.findViewById(R.id.tvBestLocation);
        v.findViewById(R.id.btnNavigateBest).setOnClickListener(x -> centerBest());
        v.findViewById(R.id.btnOpenMap).setOnClickListener(x -> centerBest());
        osmMap = v.findViewById(R.id.osmMap);
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.setBuiltInZoomControls(false);
        loadAll();
        return v;
    }

    private void loadAll() {
        try {
            osmMap.getOverlays().clear();
            String json = prefs.getString("signals", "[]");
            JSONArray arr = new JSONArray(json);
            float bestLat = prefs.getFloat("best_lat", 24.8607f);
            float bestLng = prefs.getFloat("best_lng", 67.0011f);
            int bestDbm = prefs.getInt("best_dbm", -120);
            GeoPoint center = new GeoPoint(24.8607, 67.0011);
            if (arr.length() > 0) {
                JSONObject last = arr.getJSONObject(arr.length() - 1);
                center = new GeoPoint(last.optDouble("lat", 24.8607), last.optDouble("lng", 67.0011));
            } else if (bestLat != 0) {
                center = new GeoPoint(bestLat, bestLng);
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                GeoPoint p = new GeoPoint(o.optDouble("lat", 24.8607), o.optDouble("lng", 67.0011));
                Marker m = new Marker(osmMap);
                m.setPosition(p);
                m.setTitle(o.optInt("dbm", -80) + " dBm");
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                osmMap.getOverlays().add(m);
            }
            osmMap.getController().setZoom(14.0);
            osmMap.getController().setCenter(center);
            osmMap.invalidate();
            tvBestSignal.setText("Best: " + bestDbm + " dBm | Total: " + arr.length());
            tvBestLocation.setText("Lat: " + bestLat + ", Lng: " + bestLng + " (Karachi)");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void centerBest() {
        float lat = prefs.getFloat("best_lat", 24.8607f);
        float lng = prefs.getFloat("best_lng", 67.0011f);
        osmMap.getController().animateTo(new GeoPoint(lat, lng));
        osmMap.getController().setZoom(17.0);
    }

    @Override public void onResume() { super.onResume(); if (osmMap != null) { osmMap.onResume(); loadAll(); } }
    @Override public void onPause() { super.onPause(); if (osmMap != null) osmMap.onPause(); }
}