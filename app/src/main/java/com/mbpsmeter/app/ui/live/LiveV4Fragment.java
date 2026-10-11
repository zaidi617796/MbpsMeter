package com.mbpsmeter.app.ui.live;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.ui.custom.GaugeView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class LiveV4Fragment extends Fragment {

    private TextView tvStrength, tvGps, tvTime;
    private GaugeView gauge;
    private Button btnSave, btnStop;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private Random random = new Random();
    private int currentDbm = -75;
    private double curLat = 24.8607, curLng = 67.0011; // Karachi Default
    private boolean isRunning = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_live_v4, container, false);

        tvStrength = v.findViewById(R.id.tvStrength);
        tvGps = v.findViewById(R.id.tvGps);
        tvTime = v.findViewById(R.id.tvTime);
        gauge = v.findViewById(R.id.gauge);
        btnSave = v.findViewById(R.id.btnSave);
        btnStop = v.findViewById(R.id.btnStop);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

       