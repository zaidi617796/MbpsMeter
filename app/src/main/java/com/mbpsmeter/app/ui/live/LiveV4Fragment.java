package com.mbpsmeter.app.ui.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;

public class LiveV4Fragment extends Fragment {

    private TextView tvDbm, tvStrength, tvGps;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_v4, container, false);

        tvDbm = view.findViewById(R.id.tvDbm);
        tvStrength = view.findViewById(R.id.tvStrength);
        tvGps = view.findViewById(R.id.tvGps);

        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        view.findViewById(R.id.btnSave).setOnClickListener(v -> saveReading());

        updateSignal();

        return view;
    }

    private void updateSignal() {
        try {
            TelephonyManager tm = (TelephonyManager) requireContext().getSystemService(Context.TELEPHONY_SERVICE);
            // Dummy for now - real dbm reading logic
            int dbm = -81;
            tvDbm.setText(dbm + " dBm");
            if (dbm > -80) {
                tvStrength.setText("STRONG SIGNAL");
                tvStrength.setTextColor(getResources().getColor(R.color.signal_strong));
            } else if (dbm > -95) {
                tvStrength.setText("MODERATE SIGNAL");
                tvStrength.setTextColor(getResources().getColor(R.color.signal_moderate));
            } else {
                tvStrength.setText("WEAK SIGNAL");
                tvStrength.setTextColor(getResources().getColor(R.color.signal_weak));
            }
        } catch (Exception e) {
            tvDbm.setText("-81 dBm");
        }
    }

    private void saveReading() {
        Toast.makeText(getContext(), "Reading Saved", Toast.LENGTH_SHORT).show();
    }
}