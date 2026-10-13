package com.mbpsmeter.app.ui.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mbpsmeter.app.MainActivity;
import com.mbpsmeter.app.R;

/**
 * First-launch onboarding: 4 swipeable-style slides.
 * Shows only once; saves onboarding_done=true in "MbpsMeter" prefs.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int[] ICONS = {
            R.drawable.ic_onboard_home,
            R.drawable.ic_onboard_walk,
            R.drawable.ic_onboard_sketch,
            R.drawable.ic_onboard_speed
    };
    private static final int[] TITLES = {
            R.string.onboard1_title,
            R.string.onboard2_title,
            R.string.onboard3_title,
            R.string.onboard4_title
    };
    private static final int[] DESCS = {
            R.string.onboard1_desc,
            R.string.onboard2_desc,
            R.string.onboard3_desc,
            R.string.onboard4_desc
    };

    private int page = 0;
    private FrameLayout slideContainer;
    private LinearLayout dotsRow;
    private Button btnNext;
    private TextView[] dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        slideContainer = findViewById(R.id.slide_container);
        dotsRow = findViewById(R.id.dots_row);
        btnNext = findViewById(R.id.btn_next);
        TextView btnSkip = findViewById(R.id.btn_skip);

        // Dot indicators
        dots = new TextView[ICONS.length];
        for (int i = 0; i < dots.length; i++) {
            TextView d = new TextView(this);
            d.setText("\u25CF");
            d.setTextSize(16);
            d.setPadding(8, 0, 8, 0);
            dotsRow.addView(d);
            dots[i] = d;
        }

        btnNext.setOnClickListener(v -> {
            if (page < ICONS.length - 1) {
                showPage(page + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        showPage(0);
    }

    private void showPage(int index) {
        page = index;
        slideContainer.removeAllViews();
        View slide = LayoutInflater.from(this)
                .inflate(R.layout.item_onboard_slide, slideContainer, false);

        ImageView icon = slide.findViewById(R.id.slide_icon);
        TextView title = slide.findViewById(R.id.slide_title);
        TextView desc = slide.findViewById(R.id.slide_desc);

        icon.setImageResource(ICONS[index]);
        title.setText(TITLES[index]);
        desc.setText(DESCS[index]);

        slideContainer.addView(slide);

        // Update dots
        for (int i = 0; i < dots.length; i++) {
            dots[i].setText(i == index ? "\u25CF" : "\u25CB");
            dots[i].setTextColor(i == index
                    ? getResources().getColor(R.color.neon_cyan, getTheme())
                    : getResources().getColor(R.color.glass_border, getTheme()));
        }

        // Last slide: Next becomes Get Started
        btnNext.setText(index == ICONS.length - 1
                ? R.string.onboard_start
                : R.string.onboard_next);
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences("MbpsMeter", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_done", true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
