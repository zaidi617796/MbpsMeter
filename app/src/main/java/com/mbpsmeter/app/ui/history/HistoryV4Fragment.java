package com.mbpsmeter.app.ui.history;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mbpsmeter.app.R;

import org.json.JSONArray;

public class HistoryV4Fragment extends Fragment {

    private TextView tvCount;
    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history_v4, container, false);
        tvCount = view.findViewById(R.id.tvCount);
        recycler = view.findViewById(R.id.recyclerHistory);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        
        loadHistory();
        return view;
    }

    private void loadHistory() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        String json = prefs.getString("signals", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            tvCount.setText("Total Readings: " + arr.length());
            // TODO: set adapter
        } catch (Exception e) {
            tvCount.setText("Total Readings: 0");
        }
    }
}