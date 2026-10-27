package com.mbpsmeter.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import com.mbpsmeter.app.ui.history.HistoryV4Fragment;
import com.mbpsmeter.app.ui.live.LiveV4Fragment;
import com.mbpsmeter.app.ui.map.MapV4Fragment;
import com.mbpsmeter.app.ui.onboarding.OnboardingActivity;
import com.mbpsmeter.app.ui.sketch.NakshaFragment;
import com.mbpsmeter.app.ui.speed.SpeedV4Fragment;



public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 101;
    private LinearLayout bottomNav;

    // ---- Custom circle tabs ----
    private static final int[] NAV_IDS = {
            R.id.nav_live, R.id.nav_speed, R.id.nav_map, R.id.nav_naksha, R.id.nav_history };
    private static final int[] NAV_ICONS = {
            R.drawable.ic_nav_live, R.drawable.ic_nav_speed, R.drawable.ic_nav_map,
            R.drawable.ic_nav_naksha, R.drawable.ic_nav_history };
    private static final int[] NAV_LABELS = {
            R.string.nav_live, R.string.nav_speed, R.string.nav_map,
            R.string.nav_naksha, R.string.nav_history };
    private static final int MAP_INDEX = 2;
    private FrameLayout[] tabCircles;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private int currentTabId = R.id.nav_live;
    private boolean tabShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Global crash catcher: crash ko log karke app ko zinda rakho,
        // system crash dialog nahi aayega.
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String msg = e.toString() + "\n";
                StackTraceElement[] st = e.getStackTrace();
                for (int i = 0; i < Math.min(8, st.length); i++) msg += st[i].toString() + "\n";
                android.util.Log.e("MbpsMeterCrash", msg);
                // Crash ko SharedPreferences me save karo taake agli launch par dikha saken
                try {
                    getSharedPreferences("MbpsMeter", MODE_PRIVATE).edit()
                        .putString("last_crash", msg).apply();
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            // Default handler ko call NAHI karenge - app crash nahi hogi
            // System ko lagta hai humne handle kar liya
            try {
                // UI thread par Toast dikhao
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        android.widget.Toast.makeText(getApplicationContext(),
                            "Error: " + e.toString(), android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        });

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

        try {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        } catch (Exception ignored) {}
        try {
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            // Layout inflate nahi hua - wajah dikhao aur ruko
            android.util.Log.e("MbpsMeterCrash", "setContentView failed", e);
            try {
                android.widget.TextView tv = new android.widget.TextView(this);
                tv.setText("Layout error:\n" + e.toString());
                tv.setTextSize(14);
                tv.setPadding(32, 32, 32, 32);
                setContentView(tv);
            } catch (Exception ignored) {}
            return;
        }

        bottomNav = findViewById(R.id.bottomNav);
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

        // Settings ka icon ab History tab me hai (v6.2) — top bar me sirf naam

        // Circle tabs code se banao (purane menu wale tareeqe ki tarah)
        try {
            buildTabs();
        } catch (Exception e) {
            android.util.Log.e("MbpsMeterCrash", "Tab build failed", e);
        }

        if (savedInstanceState == null) {
            try {
                selectTab(R.id.nav_live);
            } catch (Exception ignored) {}
        } else {
            try {
                currentTabId = savedInstanceState.getInt("sel_tab", R.id.nav_live);
                applyTabStyles();
            } catch (Exception ignored) {}
        }

        try {
            askPermissions();
        } catch (Exception e) {
            android.util.Log.e("MbpsMeterCrash", "askPermissions failed", e);
        }
    }

    /** Kisi bhi tab par programmatically jao (e.g. History se Map par) */
    public void selectTab(int menuId) {
        try {
            if (menuId == currentTabId && tabShown) {
                applyTabStyles();
                return;
            }
            currentTabId = menuId;
            applyTabStyles();
            showFragmentFor(menuId);
            tabShown = true;
        } catch (Exception ignored) {}
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("sel_tab", currentTabId);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Bottom tabs banao: har tab = circle (icon) + neeche label. Map wala circle bara. */
    private void buildTabs() {
        if (bottomNav == null) return;
        bottomNav.removeAllViews();
        tabCircles = new FrameLayout[NAV_IDS.length];
        tabIcons = new ImageView[NAV_IDS.length];
        tabLabels = new TextView[NAV_IDS.length];
        for (int i = 0; i < NAV_IDS.length; i++) {
            final int navId = NAV_IDS[i];
            boolean isMap = i == MAP_INDEX;

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            root.setLayoutParams(rootLp);
            root.setPadding(0, dp(2), 0, 0);
            root.setClickable(true);

            FrameLayout circle = new FrameLayout(this);
            int circleSize = dp(isMap ? 58 : 46);
            circle.setLayoutParams(new LinearLayout.LayoutParams(circleSize, circleSize));

            ImageView icon = new ImageView(this);
            int iconSize = dp(isMap ? 29 : 24);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconLp.gravity = Gravity.CENTER;
            icon.setLayoutParams(iconLp);
            try { icon.setImageResource(NAV_ICONS[i]); } catch (Exception ignored) {}
            circle.addView(icon);

            TextView label = new TextView(this);
            label.setText(NAV_LABELS[i]);
            label.setTextSize(isMap ? 12 : 11);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(3);
            label.setLayoutParams(labelLp);

            root.addView(circle);
            root.addView(label);
            root.setOnClickListener(x -> selectTab(navId));
            bottomNav.addView(root);

            tabCircles[i] = circle;
            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
        applyTabStyles();
    }

    /** Circle ka background: selected = cyan→purple gradient; warna halka ghaira (Map par cyan ghaira). */
    private GradientDrawable circleDrawable(boolean selected, boolean isMap) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (selected) {
            d.setColors(new int[]{ Color.parseColor("#00E5FF"), Color.parseColor("#7C4DFF") });
            d.setOrientation(GradientDrawable.Orientation.TL_BR);
        } else if (isMap) {
            d.setColor(ContextCompat.getColor(this, R.color.bg_card));
            d.setStroke(dp(3), Color.parseColor("#00E5FF"));
        } else {
            d.setColor(ContextCompat.getColor(this, R.color.bg_card));
            d.setStroke(dp(2), Color.parseColor("#2A3A6B"));
        }
        return d;
    }

    /** Selection ke rang lagao: selected tab ka text SAFED, icon safed; Map hamesha numaiyan. */
    private void applyTabStyles() {
        if (tabCircles == null) return;
        int muted = ContextCompat.getColor(this, R.color.text_muted);
        int accent = Color.parseColor("#00E5FF");
        for (int i = 0; i < NAV_IDS.length; i++) {
            boolean selected = NAV_IDS[i] == currentTabId;
            boolean isMap = i == MAP_INDEX;
            try {
                tabCircles[i].setBackground(circleDrawable(selected, isMap));
                if (selected) {
                    tabIcons[i].setImageTintList(ColorStateList.valueOf(Color.WHITE));
                    tabLabels[i].setTextColor(Color.WHITE);
                    tabLabels[i].setTypeface(null, android.graphics.Typeface.BOLD);
                } else if (isMap) {
                    tabIcons[i].setImageTintList(ColorStateList.valueOf(accent));
                    tabLabels[i].setTextColor(muted);
                    tabLabels[i].setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    tabIcons[i].setImageTintList(ColorStateList.valueOf(muted));
                    tabLabels[i].setTextColor(muted);
                    tabLabels[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            } catch (Exception ignored) {}
        }
    }

    /** Tab id ke hisab se fragment dikhao (purana listener wala kaam) */
    private void showFragmentFor(int id) {
        Fragment sel = null;
        try {
            if (id == R.id.nav_live) sel = new LiveV4Fragment();
            else if (id == R.id.nav_speed) sel = new SpeedV4Fragment();
            else if (id == R.id.nav_map) sel = new MapV4Fragment();
            else if (id == R.id.nav_naksha) sel = new NakshaFragment();
            else if (id == R.id.nav_history) sel = new HistoryV4Fragment();
        } catch (Exception ignored) {}
        if (sel != null) {
            try {
                // back stack saaf taake DualSim se wapas par nav sahi rahe
                getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, sel).commitAllowingStateLoss();
            } catch (Exception ignored) {}
        }
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
