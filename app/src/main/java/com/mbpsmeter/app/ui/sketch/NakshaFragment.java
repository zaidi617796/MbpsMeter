package com.mbpsmeter.app.ui.sketch;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.SignalReader;

/**
 * Naksha tab: ghar ka sketch banao (deewar/kamra), phir Walk mode me
 * jahan khare ho wahan tap karo — asal signal reading dot ban kar save.
 */
public class NakshaFragment extends Fragment {

    private static final String PREFS = "MbpsMeter";
    private static final String K_WALLS = "naksha_walls";
    private static final String K_ROOMS = "naksha_rooms";
    private static final String K_READINGS = "naksha_readings";

    private SketchView sketch;
    private boolean walkActive = false;

    private Button btnModeDraw, btnModeWalk;
    private Button btnWall, btnRoom, btnErase, btnUndo, btnClear, btnSave;
    private TextView tvDrawHint;
    private HorizontalScrollView drawToolbar;
    private LinearLayout walkPanel;
    private TextView tvWalkHint;
    private Button btnWalkStart;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_naksha, container, false);
        try {
            sketch = v.findViewById(R.id.sketchView);
            btnModeDraw = v.findViewById(R.id.btnModeDraw);
            btnModeWalk = v.findViewById(R.id.btnModeWalk);
            tvDrawHint = v.findViewById(R.id.tvDrawHint);
            drawToolbar = v.findViewById(R.id.drawToolbar);
            walkPanel = v.findViewById(R.id.walkPanel);
            tvWalkHint = v.findViewById(R.id.tvWalkHint);
            btnWalkStart = v.findViewById(R.id.btnWalkStart);
            btnWall = v.findViewById(R.id.btnWall);
            btnRoom = v.findViewById(R.id.btnRoom);
            btnErase = v.findViewById(R.id.btnErase);
            btnUndo = v.findViewById(R.id.btnUndo);
            btnClear = v.findViewById(R.id.btnClear);
            btnSave = v.findViewById(R.id.btnSave);

            loadSketch();

            sketch.setOnSketchTapListener((x, y) -> {
                if (!walkActive) return;
                doReading(x, y);
            });

            sketch.setOnRoomDrawnListener((l, t, r, b) -> showRoomNameDialog());

            // sketch badle to walls+rooms auto-save
            sketch.setOnSketchChangeListener(this::saveSketch);

            btnModeDraw.setOnClickListener(vv -> setDrawMode());
            btnModeWalk.setOnClickListener(vv -> setWalkMode());

            btnWall.setOnClickListener(vv -> { sketch.setMode(SketchView.MODE_DRAW); refreshToolButtons(); });
            btnRoom.setOnClickListener(vv -> { sketch.setMode(SketchView.MODE_ROOM); refreshToolButtons(); });
            btnErase.setOnClickListener(vv -> { sketch.setMode(SketchView.MODE_ERASE); refreshToolButtons(); });
            btnUndo.setOnClickListener(vv -> sketch.undo());
            btnClear.setOnClickListener(vv -> confirmClear());
            btnSave.setOnClickListener(vv -> {
                saveSketch();
                saveReadings();
                Toast.makeText(requireContext(), R.string.sketch_saved, Toast.LENGTH_SHORT).show();
            });

            btnWalkStart.setOnClickListener(vv -> toggleWalk());

            tvWalkHint.setText(R.string.sketch_walk_hint);
            tvDrawHint.setText(R.string.sketch_draw_hint);

            setDrawMode();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return v;
    }

    private void setDrawMode() {
        try {
            drawToolbar.setVisibility(View.VISIBLE);
            walkPanel.setVisibility(View.GONE);
            tvDrawHint.setVisibility(View.VISIBLE);
            sketch.setMode(SketchView.MODE_DRAW);
            styleModeBtn(btnModeDraw, true);
            styleModeBtn(btnModeWalk, false);
            refreshToolButtons();
        } catch (Exception ignored) {}
    }

    private void setWalkMode() {
        try {
            drawToolbar.setVisibility(View.GONE);
            walkPanel.setVisibility(View.VISIBLE);
            tvDrawHint.setVisibility(View.GONE);
            sketch.setMode(SketchView.MODE_WALK);
            styleModeBtn(btnModeDraw, false);
            styleModeBtn(btnModeWalk, true);
        } catch (Exception ignored) {}
    }

    private void styleModeBtn(Button b, boolean active) {
        try {
            if (active) {
                b.setBackgroundResource(R.drawable.bg_gradient_btn);
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackgroundResource(R.drawable.bg_pill_dark);
                b.setTextColor(0xFF8A9ABF);
            }
        } catch (Exception ignored) {}
    }

    private void refreshToolButtons() {
        try {
            int m = sketch.getMode();
            styleToolBtn(btnWall, m == SketchView.MODE_DRAW);
            styleToolBtn(btnRoom, m == SketchView.MODE_ROOM);
            styleToolBtn(btnErase, m == SketchView.MODE_ERASE);
        } catch (Exception ignored) {}
    }

    private void styleToolBtn(Button b, boolean active) {
        try {
            if (active) {
                b.setBackgroundResource(R.drawable.bg_gradient_btn);
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackgroundResource(R.drawable.bg_pill_dark);
                b.setTextColor(0xFFFFFFFF);
            }
        } catch (Exception ignored) {}
    }

    private void toggleWalk() {
        try {
            walkActive = !walkActive;
            btnWalkStart.setText(walkActive ? R.string.stop : R.string.sketch_walk_start);
            if (walkActive) {
                styleModeBtn(btnWalkStart, true);
                Toast.makeText(requireContext(), R.string.sketch_walk_hint, Toast.LENGTH_LONG).show();
            } else {
                styleModeBtn(btnWalkStart, false);
            }
        } catch (Exception ignored) {}
    }

    /** Walk tap: asal signal parho, dot lagao, save karo */
    private void doReading(float x, float y) {
        try {
            SignalReader.Result r = SignalReader.read(requireContext());
            if (r == null || !r.real) {
                Toast.makeText(requireContext(), R.string.sketch_no_signal, Toast.LENGTH_SHORT).show();
                return;
            }
            String type = (r.networkType != null && !r.networkType.isEmpty()) ? r.networkType : "";
            sketch.addReading(x, y, r.dbm, type);
            saveReadings();
            Toast.makeText(requireContext(),
                    getString(R.string.sketch_saved) + ": " + r.dbm + " dBm",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showRoomNameDialog() {
        try {
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setHint(R.string.room_name_hint);
            input.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            input.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            int pad = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
            input.setPadding(pad, pad, pad, pad);

            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sketch_room_name)
                    .setView(input)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            sketch.setRoomName(name);
                            saveSketch();
                        } else {
                            sketch.removeLastRoom();
                        }
                    })
                    .setNegativeButton(R.string.cancel, (d, w) -> sketch.removeLastRoom())
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void confirmClear() {
        try {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sketch_clear)
                    .setMessage(R.string.clear_msg)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        sketch.clearAll();
                        saveSketch();
                        saveReadings();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    private void loadSketch() {
        try {
            SharedPreferences p = prefs();
            sketch.setWallsJson(p.getString(K_WALLS, "[]"));
            sketch.setRoomsJson(p.getString(K_ROOMS, "[]"));
            sketch.setReadingsJson(p.getString(K_READINGS, "[]"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSketch() {
        try {
            prefs().edit()
                    .putString(K_WALLS, sketch.getWallsJson())
                    .putString(K_ROOMS, sketch.getRoomsJson())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void saveReadings() {
        try {
            prefs().edit().putString(K_READINGS, sketch.getReadingsJson()).apply();
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        sketch = null;
    }
}
