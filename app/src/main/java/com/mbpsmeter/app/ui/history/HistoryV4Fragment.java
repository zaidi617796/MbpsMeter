package com.mbpsmeter.app.ui.history;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.SignalReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * HISTORY V5: signal readings + speed tests aik list me,
 * proper Clear (confirm) + CSV Export.
 */
public class HistoryV4Fragment extends Fragment {

    private TextView tvCount;
    private RecyclerView recycler;
    private final ArrayList<Row> rows = new ArrayList<>();

    static class Row {
        long time;
        boolean isSpeed;
        int dbm;
        double lat, lng;
        String type, net;
        double down, up, ping, jitter;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history_v4, container, false);
        tvCount = view.findViewById(R.id.tvCount);
        recycler = view.findViewById(R.id.recyclerHistory);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        view.findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        view.findViewById(R.id.btnExport).setOnClickListener(v -> exportCsv());
        load();
        return view;
    }

    private void load() {
        rows.clear();
        int sigCount = 0, speedCount = 0;
        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    Row r = new Row();
                    r.isSpeed = false;
                    r.dbm = o.optInt("dbm", -120);
                    r.time = o.optLong("time", 0);
                    r.lat = o.optDouble("lat", 0);
                    r.lng = o.optDouble("lng", 0);
                    r.type = o.optString("type", "MANUAL");
                    r.net = o.optString("net", "");
                    rows.add(r);
                    sigCount++;
                } catch (Exception ignored) {}
            }
            JSONArray sp = new JSONArray(prefs.getString("speed_tests", "[]"));
            for (int i = 0; i < sp.length(); i++) {
                try {
                    JSONObject o = sp.getJSONObject(i);
                    Row r = new Row();
                    r.isSpeed = true;
                    r.time = o.optLong("time", 0);
                    r.down = o.optDouble("down", 0);
                    r.up = o.optDouble("up", 0);
                    r.ping = o.optDouble("ping", 0);
                    r.jitter = o.optDouble("jitter", 0);
                    rows.add(r);
                    speedCount++;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        Collections.sort(rows, (a, b) -> Long.compare(b.time, a.time));
        if (tvCount != null) tvCount.setText(sigCount + " readings • " + speedCount + " speed tests");

        recycler.setAdapter(new RecyclerView.Adapter<Holder>() {
            @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_history, p, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder h, int pos) { bind(h, rows.get(pos)); }
            @Override public int getItemCount() { return rows.size(); }
        });
    }

    private void bind(Holder h, Row r) {
        String date = new SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault()).format(new Date(r.time));
        if (!r.isSpeed) {
            h.badge.setText(r.dbm + " dBm");
            int color = r.dbm >= -80 ? 0xFF22C55E : (r.dbm >= -95 ? 0xFFF59E0B : 0xFFEF4444);
            h.badge.setBackgroundTintList(ColorStateList.valueOf(color));
            h.badge.setTextColor(0xFF070E2B);
            String label = SignalReader.label(r.dbm);
            h.title.setText(label + " Signal • " + r.type);
            String sub = String.format(Locale.getDefault(), "%.5f, %.5f", r.lat, r.lng);
            if (!r.net.isEmpty() && !"Unknown".equals(r.net)) sub += " • " + r.net;
            sub += "\n" + date;
            h.sub.setText(sub);
            h.extra.setText("");
        } else {
            h.badge.setText(String.format(Locale.US, "%.1f", r.down));
            h.badge.setBackgroundTintList(ColorStateList.valueOf(0xFF00E5FF));
            h.badge.setTextColor(0xFF070E2B);
            h.title.setText("Speed Test");
            h.sub.setText(date);
            h.extra.setText(String.format(Locale.US, "↓ %.1f Mbps\n↑ %.1f Mbps\n%d ms ping",
                    r.down, r.up, Math.round(r.ping)));
        }
    }

    private void confirmClear() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    SharedPreferences prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
                    prefs.edit()
                            .remove("signals")
                            .remove("speed_tests")
                            .remove("best_dbm")
                            .remove("best_lat")
                            .remove("best_lng")
                            .apply();
                    load();
                    Toast.makeText(getContext(), "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportCsv() {
        if (rows.isEmpty() || getContext() == null) {
            Toast.makeText(getContext(), "Koi data nahi hai", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File f = new File(requireContext().getExternalFilesDir(null), "mbpsmeter_export.csv");
            FileWriter fw = new FileWriter(f);
            fw.write("type,time,dbm,down_mbps,up_mbps,ping_ms,jitter_ms,lat,lng,network\n");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            for (Row r : rows) {
                if (!r.isSpeed) {
                    fw.write(String.format(Locale.US, "signal,%s,%d,,,,,%.6f,%.6f,%s\n",
                            sdf.format(new Date(r.time)), r.dbm, r.lat, r.lng, r.net));
                } else {
                    fw.write(String.format(Locale.US, "speed,%s,,%.2f,%.2f,%.0f,%.0f,,,\n",
                            sdf.format(new Date(r.time)), r.down, r.up, r.ping, r.jitter));
                }
            }
            fw.close();
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Export CSV"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView badge, title, sub, extra;
        Holder(View v) {
            super(v);
            badge = v.findViewById(R.id.badgeDbm);
            title = v.findViewById(R.id.tvTitle);
            sub = v.findViewById(R.id.tvSub);
            extra = v.findViewById(R.id.tvExtra);
        }
    }

    @Override public void onResume() { super.onResume(); load(); }
}