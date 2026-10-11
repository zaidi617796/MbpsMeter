package com.mbpsmeter.app.ui.map;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;

public class MapV4Fragment extends Fragment {

    private TextView tvBestSignal, tvBestLocation;
    private Button btnNavigateBest, btnOpenMap;
    private SharedPreferences prefs;
    private float bestLat = 0f;
    private float bestLng = 0f;
    private int bestDbm = -100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map_v4, container, false);

        tvBestSignal = view.findViewById(R.id.tvBestSignal);
        tvBestLocation = view.findViewById(R.id.tvBestLocation);
        btnNavigateBest = view.findViewById(R.id.btnNavigateBest);
        btnOpenMap = view.findViewById(R.id.btnOpenMap);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        loadBestLocation();

        btnNavigateBest.setOnClickListener(v -> navigateToBest());
        btnOpenMap.setOnClickListener(v -> openMapView());

        return view;
    }

    private void loadBestLocation() {
        bestLat = prefs.getFloat("best_lat", 0f);
        bestLng = prefs.getFloat("best_lng", 0f);
        bestDbm = prefs.getInt("best_dbm", -120);

        if (bestLat == 0) {
            tvBestSignal.setText("No best location saved yet");
            tvBestLocation.setText("Move to find GOOD signal to auto-save");
            btnNavigateBest.setEnabled(false);
            btnNavigateBest.setAlpha(0.5f);
        } else {
            tvBestSignal.setText("Best: " + bestDbm + " dBm");
            tvBestLocation.setText("Lat: " + bestLat + ", Lng: " + bestLng);
            btnNavigateBest.setEnabled(true);
            btnNavigateBest.setAlpha(1f);
        }
    }

    private void navigateToBest() {
        if (bestLat == 0 && bestLng == 0) {
            Toast.makeText(getContext(), "No best location saved yet", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Try Google Maps navigation
            String uri = "google.navigation:q=" + bestLat + "," + bestLng + "&mode=d";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback to browser maps
                String browserUri = "https://www.google.com/maps/dir/?api=1&destination=" + bestLat + "," + bestLng;
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUri));
                startActivity(browserIntent);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Maps app not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openMapView() {
        if (bestLat == 0) {
            Toast.makeText(getContext(), "No location to show", Toast.LENGTH_SHORT).show();
            return;
        }
        String uri = "geo:" + bestLat + "," + bestLng + "?q=" + bestLat + "," + bestLng + "(Best Signal " + bestDbm + "dBm)";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception e) {
            String browserUri = "https://www.google.com/maps/search/?api=1&query=" + bestLat + "," + bestLng;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(browserUri)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBestLocation(); // refresh when coming back
    }
}