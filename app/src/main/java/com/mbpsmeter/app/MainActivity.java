package com.mbpsmeter.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mbpsmeter.app.ui.history.HistoryV4Fragment;
import com.mbpsmeter.app.ui.live.LiveV4Fragment;
import com.mbpsmeter.app.ui.map.MapV4Fragment;
import com.mbpsmeter.app.ui.rooms.RoomHeatmapFragment;
import com.mbpsmeter.app.ui.speed.SpeedV4Fragment;



public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // App hamesha dark theme me rahe - DayNight system mode se affect na ho
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        View container = findViewById(R.id.fragmentContainer);

        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), nav.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
                Insets st = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), st.top, v.getPaddingRight(), v.getPaddingBottom());
                return WindowInsetsCompat.CONSUMED;
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment sel = null;
            int id = item.getItemId();
            if (id == R.id.nav_live) sel = new LiveV4Fragment();
            else if (id == R.id.nav_rooms) sel = new RoomHeatmapFragment();
            else if (id == R.id.nav_speed) sel = new SpeedV4Fragment();
            else if (id == R.id.nav_map) sel = new MapV4Fragment();
            else if (id == R.id.nav_history) sel = new HistoryV4Fragment();
            if (sel != null) {
                // back stack saaf taake DualSim se wapas par nav sahi rahe
                getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, sel).commit();
            }
            return true;
        });

        if (savedInstanceState == null) bottomNav.setSelectedItemId(R.id.nav_live);

        askPermissions();
    }

    /** App khulte hi permission popup */
    private void askPermissions() {
        String[] need = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
        };
        boolean missing = false;
        for (String p : need) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing = true;
                break;
            }
        }
        if (!missing) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_msg)
                .setPositiveButton(R.string.ok, (d, w) ->
                        ActivityCompat.requestPermissions(MainActivity.this, need, REQ_PERMS))
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean locOk = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    locOk = true;
                }
            }
            if (!locOk) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.perm_title)
                        .setMessage(R.string.perm_loc_needed)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }
    }
}
