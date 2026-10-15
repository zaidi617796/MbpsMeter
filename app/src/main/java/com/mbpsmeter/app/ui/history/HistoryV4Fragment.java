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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.mbpsmeter.app.util.SupabaseSync;

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
 * HISTORY V6: Readings + Speed tests + Drop Diary (toggle).
 * Clear (confirm) + CSV export.
 */
public class HistoryV4Fragment extends Fragment {

    private TextView tvCount;
    private RecyclerView recycler;
    private Button btnTabReadings, btnTabDrops;
    private final ArrayList<Row> rows = new ArrayList<>();
    private boolean showDrops = false;

    static class Row {
        long time;
        int kind; // 0=signal, 1=speed, 2=drop
        int dbm;
        double lat, lng;
        String type, net, room;
        double down, up, ping, jitter;
        long end;
        int minDbm;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history_v4, container, false);
        tvCount = view.findViewById(R.id.tvCount);
        recycler = view.findViewById(R.id.recyclerHistory);
        btnTabReadings = view.findViewById(R.id.btnTabReadings);
        btnTabDrops = view.findViewById(R.id.btnTabDrops);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        view.findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        view.findViewById(R.id.btnExport).setOnClickListener(v -> exportCsv());
        view.findViewById(R.id.btnCloudBackup).setOnClickListener(v -> cloudBackup());
        view.findViewById(R.id.btnCloudRestore).setOnClickListener(v -> confirmRestore());
        btnTabReadings.setOnClickListener(v -> { showDrops = false; styleTabs(); load(); });
        btnTabDrops.setOnClickListener(v -> { showDrops = true; styleTabs(); load(); });
        styleTabs();
        load();
        return view;
    }

    private void styleTabs() {
        if (showDrops) {
            btnTabDrops.setBackgroundResource(R.drawable.bg_pill_mint);
            btnTabDrops.setTextColor(0xFF0F172A);
            btnTabReadings.setBackgroundResource(R.drawable.bg_pill_dark);
            btnTabReadings.setTextColor(0xFFFFFFFF);
        } else {
            btnTabReadings.setBackgroundResource(R.drawable.bg_pill_mint);
            btnTabReadings.setTextColor(0xFF0F172A);
            btnTabDrops.setBackgroundResource(R.drawable.bg_pill_dark);
            btnTabDrops.setTextColor(0xFFFFFFFF);
        }
    }

    private void load() {
        rows.clear();
        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
            if (!showDrops) {
                JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        JSONObject o = arr.getJSONObject(i);
                        Row r = new Row();
                        r.kind = 0;
                        r.dbm = o.optInt("dbm", -120);
                        r.time = o.optLong("time", 0);
                        r.lat = o.optDouble("lat", 0);
                        r.lng = o.optDouble("lng", 0);
                        r.type = o.optString("type", "MANUAL");
                        r.net = o.optString("net", "");
                        r.room = o.optString("room", "");
                        rows.add(r);
                    } catch (Exception ignored) {}
                }
                JSONArray sp = new JSONArray(prefs.getString("speed_tests", "[]"));
                for (int i = 0; i < sp.length(); i++) {
                    try {
                        JSONObject o = sp.getJSONObject(i);
                        Row r = new Row();
                        r.kind = 1;
                        r.time = o.optLong("time", 0);
                        r.down = o.optDouble("down", 0);
                        r.up = o.optDouble("up", 0);
                        r.ping = o.optDouble("ping", 0);
                        r.jitter = o.optDouble("jitter", 0);
                        rows.add(r);
                    } catch (Exception ignored) {}
                }
            } else {
                JSONArray dr = new JSONArray(prefs.getString("drops", "[]"));
                for (int i = 0; i < dr.length(); i++) {
                    try {
                        JSONObject o = dr.getJSONObject(i);
                        Row r = new Row();
                        r.kind = 2;
                        r.time = o.optLong("start", 0);
                        r.end = o.optLong("end", 0);
                        r.minDbm = o.optInt("minDbm", -120);
                        r.lat = o.optDouble("lat", 0);
                        r.lng = o.optDouble("lng", 0);
                        rows.add(r);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        Collections.sort(rows, (a, b) -> Long.compare(b.time, a.time));
        if (tvCount != null) {
            if (showDrops) {
                tvCount.setText(rows.size() + " " + getString(R.string.tab_drops));
            } else {
                int sig = 0, spd = 0;
                for (Row r : rows) { if (r.kind == 0) sig++; else spd++; }
                tvCount.setText(sig + " readings | " + spd + " " + getString(R.string.speed_tests_suffix));
            }
        }

        recycler.setAdapter(new RecyclerView.Adapter<Holder>() {
            @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_history, p, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder h, int pos) { bind(h, rows.get(pos)); }
            @Override public int getItemCount() { return rows.size(); }
        });
    }

    private void bind(Holder h, Row r) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault());
        if (r.kind == 0) {
            h.badge.setText(r.dbm + " dBm");
            int color = r.dbm >= -80 ? 0xFF22C55E : (r.dbm >= -95 ? 0xFFF59E0B : 0xFFEF4444);
            h.badge.setBackgroundTintList(ColorStateList.valueOf(color));
            h.badge.setTextColor(0xFF070E2B);
            String title = SignalReader.label(r.dbm) + " Signal | " + r.type;
            if (!r.room.isEmpty()) title = r.room + " | " + title;
            h.title.setText(title);
            String sub = String.format(Locale.getDefault(), "%.5f, %.5f", r.lat, r.lng);
            if (!r.net.isEmpty() && !"Unknown".equals(r.net) && !"Demo mode".equals(r.net) && !"ڈیمو موڈ".equals(r.net)) sub += " | " + r.net;
            sub += "\n" + sdf.format(new Date(r.time));
            h.sub.setText(sub);
            h.extra.setText("");
        } else if (r.kind == 1) {
            h.badge.setText(String.format(Locale.US, "%.1f", r.down));
            h.badge.setBackgroundTintList(ColorStateList.valueOf(0xFF00E5FF));
            h.badge.setTextColor(0xFF070E2B);
            h.title.setText("Speed Test");
            h.sub.setText(sdf.format(new Date(r.time)));
            h.extra.setText(String.format(Locale.US, "%.1f Mbps down\n%.1f Mbps up\n%.0f ms ping", r.down, r.up, r.ping));
        } else {
            h.badge.setText(r.minDbm + " dBm");
            h.badge.setBackgroundTintList(ColorStateList.valueOf(0xFFEF4444));
            h.badge.setTextColor(0xFF070E2B);
            h.title.setText(R.string.drop_title);
            long secs = Math.max(0, (r.end - r.time) / 1000);
            String dur = secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s";
            h.sub.setText(sdf.format(new Date(r.time))
                    + String.format(Locale.getDefault(), "\n%.5f, %.5f", r.lat, r.lng));
            h.extra.setText(dur);
        }
    }

    private SharedPreferences prefs() {
        return requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
    }

    private void needConfig() {
        Toast.makeText(getContext(), R.string.cloud_no_config, Toast.LENGTH_LONG).show();
        cloudSettings();
    }

    private void cloudBackup() {
        if (getContext() == null) return;
        if (!SupabaseSync.hasConfig(prefs())) { needConfig(); return; }
        Toast.makeText(getContext(), R.string.cloud_backing_up, Toast.LENGTH_SHORT).show();
        SupabaseSync.backup(requireContext(), (ok, msg) -> {
            if (!isAdded()) return;
            Toast.makeText(getContext(), ok ? getString(R.string.cloud_done)
                    : getString(R.string.cloud_failed, msg), Toast.LENGTH_LONG).show();
        });
    }

    private void confirmRestore() {
        if (getContext() == null) return;
        if (!SupabaseSync.hasConfig(prefs())) { needConfig(); return; }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.cloud_restore)
                .setMessage(R.string.cloud_restore_confirm)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    Toast.makeText(getContext(), R.string.cloud_restoring, Toast.LENGTH_SHORT).show();
                    SupabaseSync.restore(requireContext(), (ok, msg) -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), ok ? getString(R.string.cloud_done)
                                : getString(R.string.cloud_failed, msg), Toast.LENGTH_LONG).show();
                        if (ok) load();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void cloudSettings() {
        if (getContext() == null) return;
        SharedPreferences p = prefs();
        LinearLayout ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ll.setPadding(pad, pad, pad, pad);
        final EditText etUrl = new EditText(getContext());
        etUrl.setHint(R.string.sb_url_hint);
        etUrl.setText(p.getString("sb_url", ""));
        etUrl.setSingleLine(true);
        final EditText etKey = new EditText(getContext());
        etKey.setHint(R.string.sb_key_hint);
        etKey.setText(p.getString("sb_key", ""));
        etKey.setSingleLine(true);
        ll.addView(etUrl);
        ll.addView(etKey);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.cloud_settings)
                .setView(ll)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    prefs().edit()
                            .putString("sb_url", etUrl.getText().toString().trim())
                            .putString("sb_key", etKey.getText().toString().trim())
                            .apply();
                    Toast.makeText(getContext(), R.string.cloud_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
                            .remove("drops")
                            .remove("best_dbm")
                            .remove("best_lat")
                            .remove("best_lng")
                            .apply();
                    load();
                    Toast.makeText(getContext(), R.string.history_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportCsv() {
        if (rows.isEmpty() || getContext() == null) {
            Toast.makeText(getContext(), R.string.no_data, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File f = new File(requireContext().getExternalFilesDir(null), "mbpsmeter_export.csv");
            FileWriter fw = new FileWriter(f);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            if (!showDrops) {
                fw.write("type,time,dbm,down_mbps,up_mbps,ping_ms,jitter_ms,lat,lng,network,room\n");
                for (Row r : rows) {
                    if (r.kind == 0) {
                        fw.write(String.format(Locale.US, "signal,%s,%d,,,,,%.6f,%.6f,%s,%s\n",
                                sdf.format(new Date(r.time)), r.dbm, r.lat, r.lng, r.net, r.room));
                    } else {
                        fw.write(String.format(Locale.US, "speed,%s,,%.2f,%.2f,%.0f,%.0f,,,\n",
                                sdf.format(new Date(r.time)), r.down, r.up, r.ping, r.jitter));
                    }
                }
            } else {
                fw.write("type,start,end,duration_s,min_dbm,lat,lng\n");
                for (Row r : rows) {
                    fw.write(String.format(Locale.US, "drop,%s,%s,%d,%d,%.6f,%.6f\n",
                            sdf.format(new Date(r.time)), sdf.format(new Date(r.end)),
                            (r.end - r.time) / 1000, r.minDbm, r.lat, r.lng));
                }
            }
            fw.close();
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.export_csv)));
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.no_data, Toast.LENGTH_SHORT).show();
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
