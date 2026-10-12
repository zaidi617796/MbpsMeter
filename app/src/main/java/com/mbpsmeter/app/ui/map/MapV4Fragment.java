package com.mbpsmeter.app.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.mbpsmeter.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * MAP V8: Google Maps SDK - colored pins + best spot + Open in Maps.
 * API key res/values/google_maps_api.xml me (google_maps_key) dalein.
 */
public class MapV4Fragment extends Fragment implements OnMapReadyCallback {

    private TextView tvBestSignal, tvBestLocation, tvBestDistance, tvKeyNotice;
    private SharedPreferences prefs;
    private GoogleMap gmap;

    private float bestLat = 0, bestLng = 0;
    private int bestDbm = -120;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map_v4, container, false);
        tvBestSignal = v.findViewById(R.id.tvBestSignal);
        tvBestLocation = v.findViewById(R.id.tvBestLocation);
        tvBestDistance = v.findViewById(R.id.tvBestDistance);
        tvKeyNotice = v.findViewById(R.id.tvMapKeyNotice);
        v.findViewById(R.id.btnNavigateBest).setOnClickListener(x -> centerBest());
        v.findViewById(R.id.btnOpenMap).setOnClickListener(x -> openInMaps());
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        String key = getString(R.string.google_maps_key);
        if (key == null || key.isEmpty() || "YOUR_API_KEY_HERE".equals(key)) {
            tvKeyNotice.setVisibility(View.VISIBLE);
        } else {
            tvKeyNotice.setVisibility(View.GONE);
        }

        SupportMapFragment mf = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.googleMap);
        if (mf != null) mf.getMapAsync(this);

        updateBestCard();
        return v;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gmap = googleMap;
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                gmap.setMyLocationEnabled(true);
            }
        } catch (Exception ignored) {}
        gmap.getUiSettings().setZoomControlsEnabled(false);
        gmap.getUiSettings().setMyLocationButtonEnabled(true);
        loadAll();
    }

    private float hueFor(int dbm) {
        if (dbm >= -80) return BitmapDescriptorFactory.HUE_GREEN;
        if (dbm >= -95) return BitmapDescriptorFactory.HUE_YELLOW;
        return BitmapDescriptorFactory.HUE_RED;
    }

    private void loadAll() {
        if (gmap == null) return;
        try {
            gmap.clear();
            String json = prefs.getString("signals", "[]");
            JSONArray arr = new JSONArray(json);
            bestLat = prefs.getFloat("best_lat", 0);
            bestLng = prefs.getFloat("best_lng", 0);
            bestDbm = prefs.getInt("best_dbm", -120);

            LatLng center = new LatLng(24.8607, 67.0011);
            if (arr.length() > 0) {
                JSONObject last = arr.getJSONObject(arr.length() - 1);
                center = new LatLng(last.optDouble("lat", 24.8607), last.optDouble("lng", 67.0011));
            } else if (bestLat != 0) {
                center = new LatLng(bestLat, bestLng);
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int dbm = o.optInt("dbm", -80);
                LatLng p = new LatLng(o.optDouble("lat", 24.8607), o.optDouble("lng", 67.0011));
                String title = dbm + " dBm | " + o.optString("type", "MANUAL");
                if (!o.optString("room", "").isEmpty()) title = o.optString("room", "") + " | " + title;
                gmap.addMarker(new MarkerOptions()
                        .position(p)
                        .title(title)
                        .icon(BitmapDescriptorFactory.defaultMarker(hueFor(dbm))));
            }

            if (bestLat != 0 && bestDbm > -120) {
                gmap.addMarker(new MarkerOptions()
                        .position(new LatLng(bestLat, bestLng))
                        .title(getString(R.string.best) + ": " + bestDbm + " dBm")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            }

            gmap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15f));
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
        if (gmap != null) {
            gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(bestLat, bestLng), 17f));
        }
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

    @Override
    public void onResume() {
        super.onResume();
        if (gmap != null) loadAll();
    }
}
