package com.mbpsmeter.app.ui.rooms;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.mbpsmeter.app.R;
import com.mbpsmeter.app.ui.custom.Room3DView;
import com.mbpsmeter.app.util.SignalAdvisor;
import com.mbpsmeter.app.util.SignalReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/**
 * ROOM HEATMAP: ghar ke kamron ki signal heatmap.
 * GPS ghar ke andar reliable nahi, is liye room-based reading zyada accurate hai.
 * Tap = us kamre me reading record. Long-press = kamra delete.
 */
public class RoomHeatmapFragment extends Fragment {

    private RecyclerView recycler;
    private TextView tvBestRoom;
    private SharedPreferences prefs;
    private final ArrayList<String> rooms = new ArrayList<>();
    private final Random random = new Random();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_rooms, container, false);
        recycler = v.findViewById(R.id.recyclerRooms);
        tvBestRoom = v.findViewById(R.id.tvBestRoom);
        recycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);
        v.findViewById(R.id.btnAddRoom).setOnClickListener(x -> askRoomName());
        load();
        return v;
    }

    private void load() {
        rooms.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString("rooms", "[]"));
            for (int i = 0; i < arr.length(); i++) rooms.add(arr.getJSONObject(i).getString("name"));
        } catch (Exception ignored) {}
        if (rooms.isEmpty()) {
            // pehli dafa default kamre
            rooms.add("Drawing Room");
            rooms.add("Bedroom");
            rooms.add("Kitchen");
            saveRooms();
        }
        refresh();
    }

    private void saveRooms() {
        try {
            JSONArray arr = new JSONArray();
            for (String r : rooms) arr.put(new JSONObject().put("name", r));
            prefs.edit().putString("rooms", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** kisi kamre ki readings ka average dBm (room field wali) */
    private int[] roomStats(String room) {
        int sum = 0, n = 0;
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (room.equals(o.optString("room", ""))) {
                    sum += o.optInt("dbm", -120);
                    n++;
                }
            }
        } catch (Exception ignored) {}
        return new int[]{n == 0 ? -999 : sum / n, n};
    }

    private void refresh() {
        String bestRoom = null;
        int bestAvg = -999;
        for (String r : rooms) {
            int[] st = roomStats(r);
            if (st[1] > 0 && (bestRoom == null || st[0] > bestAvg)) {
                bestRoom = r;
                bestAvg = st[0];
            }
        }
        if (bestRoom != null && tvBestRoom != null) {
            tvBestRoom.setVisibility(View.VISIBLE);
            tvBestRoom.setText(getString(R.string.best_room) + ": " + bestRoom + " (" + bestAvg + " dBm)");
        } else if (tvBestRoom != null) {
            tvBestRoom.setVisibility(View.GONE);
        }

        recycler.setAdapter(new RecyclerView.Adapter<Holder>() {
            @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_room, p, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
                String room = rooms.get(pos);
                int[] st = roomStats(room);
                h.name.setText(room);
                if (st[1] == 0) {
                    h.avg.setText("-- dBm");
                    h.avg.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.text_muted));
                    h.count.setText(getString(R.string.no_data_tap));
                    h.card.setStrokeColor(0xFF2A3A6B);
                } else {
                    h.avg.setText(st[0] + " dBm");
                    int color = st[0] >= -80 ? 0xFF22C55E : (st[0] >= -95 ? 0xFFF59E0B : 0xFFEF4444);
                    h.avg.setTextColor(color);
                    String cnt = st[1] + " " + getString(R.string.readings_count);
                    int[] cb = cornerBest(room);
                    if (cb != null) {
                        cnt += " • " + getString(R.string.corner_best_short,
                                getString(SignalAdvisor.cornerRes(cb[0])));
                    }
                    h.count.setText(cnt);
                    h.card.setStrokeColor(color);
                    h.card.setStrokeWidth(3);
                }
                h.itemView.setOnClickListener(x -> askCorner(room));
                h.itemView.setOnLongClickListener(x -> { confirmDelete(room); return true; });
            }
            @Override public int getItemCount() { return rooms.size(); }
        });
    }

    private void askRoomName() {
        if (getContext() == null) return;
        final EditText et = new EditText(getContext());
        et.setHint(getString(R.string.room_name_hint));
        et.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.add_room)
                .setView(et)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), R.string.room_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (String r : rooms) {
                        if (r.equalsIgnoreCase(name)) {
                            Toast.makeText(getContext(), R.string.room_exists, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    rooms.add(name);
                    saveRooms();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String room) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.delete_room_title)
                .setMessage(R.string.delete_room_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    rooms.remove(room);
                    saveRooms();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** us kamre ka sab se acha corner: {cornerId, avgDbm}, ya null */
    private int[] cornerBest(String room) {
        int[] sum = new int[5];
        int[] n = new int[5];
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (room.equals(o.optString("room", ""))) {
                    int c = o.optInt("corner", 0);
                    if (c < 0 || c > 4) c = 0;
                    sum[c] += o.optInt("dbm", -120);
                    n[c]++;
                }
            }
        } catch (Exception ignored) {}
        int best = -1, bestAvg = -999;
        for (int i = 0; i < 5; i++) {
            if (n[i] > 0 && sum[i] / n[i] > bestAvg) {
                bestAvg = sum[i] / n[i];
                best = i;
            }
        }
        return best < 0 ? null : new int[]{best, bestAvg};
    }

    /** 3D room me corner picker: user zone par tap kare */
    private void askCorner(String room) {
        if (getContext() == null) return;
        Room3DView rv = new Room3DView(getContext());
        final AlertDialog[] holder = new AlertDialog[1];
        rv.setData(cornerAvgs(room), corner -> {
            if (holder[0] != null) holder[0].dismiss();
            recordFor(room, corner);
        });
        holder[0] = new AlertDialog.Builder(getContext())
                .setTitle(R.string.corner_pick_title)
                .setView(rv)
                .create();
        holder[0].show();
        if (holder[0].getWindow() != null) {
            int dw = (int) (getResources().getDisplayMetrics().widthPixels * 0.94);
            holder[0].getWindow().setLayout(dw, (int) (dw * 0.8));
        }
    }

    /** us kamre ke 5 zones ka average dBm (-999 = no data) */
    private int[] cornerAvgs(String room) {
        int[] sum = new int[5];
        int[] n = new int[5];
        int[] out = new int[]{-999, -999, -999, -999, -999};
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (room.equals(o.optString("room", ""))) {
                    int c = o.optInt("corner", 0);
                    if (c < 0 || c > 4) c = 0;
                    sum[c] += o.optInt("dbm", -120);
                    n[c]++;
                }
            }
        } catch (Exception ignored) {}
        for (int i = 0; i < 5; i++) if (n[i] > 0) out[i] = sum[i] / n[i];
        return out;
    }

    /** Us kamre me aik reading record karein (corner ke sath) */
    private void recordFor(String room, int corner) {
        SignalReader.Result r = SignalReader.read(requireContext());
        int dbm;
        String net;
        if (r.real) {
            dbm = r.dbm;
            net = r.networkType;
        } else {
            dbm = -65 - random.nextInt(35);
            net = "Demo";
        }
        double[] loc = lastLoc();
        try {
            JSONArray arr = new JSONArray(prefs.getString("signals", "[]"));
            JSONObject o = new JSONObject();
            o.put("dbm", dbm);
            o.put("time", System.currentTimeMillis());
            o.put("lat", loc[0]);
            o.put("lng", loc[1]);
            o.put("type", "ROOM");
            o.put("room", room);
            o.put("corner", corner);
            o.put("net", net);
            arr.put(o);
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("signals", arr.toString());
            int oldBest = prefs.getInt("best_dbm", -120);
            if (dbm > oldBest) {
                ed.putFloat("best_lat", (float) loc[0]);
                ed.putFloat("best_lng", (float) loc[1]);
                ed.putInt("best_dbm", dbm);
            }
            ed.apply();
            Toast.makeText(getContext(), room + ": " + dbm + " dBm " + getString(R.string.room_recorded), Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
        refresh();
    }

    private double[] lastLoc() {
        double lat = 24.8607, lng = 67.0011;
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
                Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (l != null) { lat = l.getLatitude(); lng = l.getLongitude(); }
            }
        } catch (Exception ignored) {}
        return new double[]{lat, lng};
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name, avg, count;
        MaterialCardView card;
        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.tvRoomName);
            avg = v.findViewById(R.id.tvRoomAvg);
            count = v.findViewById(R.id.tvRoomCount);
            card = (MaterialCardView) v;
        }
    }

    @Override public void onResume() { super.onResume(); refresh(); }
}
