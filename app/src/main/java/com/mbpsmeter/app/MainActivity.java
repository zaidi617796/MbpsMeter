package com.mbpsmeter.app;

import android.location.Location;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mbpsmeter.app.ui.history.HistoryV4Fragment;
import com.mbpsmeter.app.ui.live.LiveV4Fragment;
import com.mbpsmeter.app.ui.map.MapV4Fragment;
import com.mbpsmeter.app.ui.speed.SpeedV4Fragment;

public class MainActivity extends AppCompatActivity {

    // Ye 2 lines purane fragments ke liye zaruri hain - isi ki wajah se PREFS error aa raha tha
    public static final String PREFS = "MbpsMeter";
    public static Location currentLocation = null;

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        View fragmentContainer = findViewById(R.id.fragmentContainer);

        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navInsets.bottom + 16);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        if (fragmentContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
                Insets statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), statusInsets.top, v.getPaddingRight(), v.getPaddingBottom());
                return WindowInsetsCompat.CONSUMED;
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selected = null;
            int id = item.getItemId();
            if (id == R.id.nav_live) selected = new LiveV4Fragment();
            else if (id == R.id.nav_speed) selected = new SpeedV4Fragment();
            else if (id == R.id.nav_map) selected = new MapV4Fragment();
            else if (id == R.id.nav_history) selected = new HistoryV4Fragment();

            if (selected != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, selected)
                        .commit();
            }
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_live);
        }
    }
}