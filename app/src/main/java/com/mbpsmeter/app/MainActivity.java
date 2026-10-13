package com.mbpsmeter.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import java.io.File;

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

import org.osmdroid.config.Configuration;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mbpsmeter.app.ui.history.HistoryV4Fragment;
import com.mbpsmeter.app.ui.live.LiveV4Fragment;
import com.mbpsmeter.app.ui.map.MapV4Fragment;
import com.mbpsmeter.app.ui.onboarding.OnboardingActivity;
import com.mbpsmeter.app.ui.rooms.RoomHeatmapFragment;
import com.mbpsmeter.app.ui.sketch.NakshaFragment;
import com.mbpsmeter.app.ui.speed.SpeedV4Fragment;



public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme: user ki pasand (dark/light/auto) - super.onCreate se PEHLE apply karna zaroori hai
        // taake theme flash na ho. getSharedPreferences attach() ke baad safe hai.
        try {
            String themeMode = getSharedPreferences("MbpsMeter", MODE_PRIVATE).getString("theme_mode", "dark");
            if ("light".equals(themeMode)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if ("auto".equals(themeMode)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        } catch (Exception ignored) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);

        // First launch: onboarding dikhao. YE super.onCreate() KE BAAD hona chahiye -
        // us se pehle startActivity() crash kar sakta hai.
        try {
            if (!getSharedPreferences("MbpsMeter", MODE_PRIVATE).getBoolean("onboarding_done", false)) {
                startActivity(new Intent(this, OnboardingActivity.class));
                finish();
                return;
            }
        } catch (Exception ignored) {}

        // OSM config - PreferenceManager ke baghair
        try {
            File osmCache = new File(getCacheDir(), "osmdroid");
            Configuration.getInstance().setOsmdroidBasePath(osmCache);
            Configuration.getInstance().setOsmdroidTileCache(new File(osmCache, "tiles"));
            Configuration.getInstance().setUserAgentValue(getPackageName());
        } catch (Exception e) { e.printStackTrace(); }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        View topBar = findViewById(R.id.topBar);

        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), nav.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
        if (topBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                Insets st = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), st.top, v.getPaddingRight(), v.getPaddingBottom());
                return WindowInsetsCompat.CONSUMED;
            });
        }

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new com.mbpsmeter.app.ui.settings.SettingsFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment sel = null;
            int id = item.getItemId();
            if (id == R.id.nav_live) sel = new LiveV4Fragment();
            else if (id == R.id.nav_rooms) sel = new RoomHeatmapFragment();
            else if (id == R.id.nav_speed) sel = new SpeedV4Fragment();
            else if (id == R.id.nav_map) sel = new MapV4Fragment();
            else if (id == R.id.nav_naksha) sel = new NakshaFragment();
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
