package com.mbpsmeter.app.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.ui.guide.GuideFragment;

/**
 * Settings screen: theme (dark/light/auto), language (English/Urdu),
 * user guide shortcut, about info.
 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        SharedPreferences prefs = requireContext().getSharedPreferences("MbpsMeter", 0);

        // ---------- Theme ----------
        RadioGroup rgTheme = v.findViewById(R.id.rgTheme);
        String cur = prefs.getString("theme_mode", "dark");
        if ("light".equals(cur)) rgTheme.check(R.id.rbLight);
        else if ("auto".equals(cur)) rgTheme.check(R.id.rbAuto);
        else rgTheme.check(R.id.rbDark);

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "dark";
            if (checkedId == R.id.rbLight) mode = "light";
            else if (checkedId == R.id.rbAuto) mode = "auto";
            prefs.edit().putString("theme_mode", mode).apply();
            requireActivity().recreate();
        });

        // ---------- Language ----------
        Button btnEnglish = v.findViewById(R.id.btnEnglish);
        Button btnUrdu = v.findViewById(R.id.btnUrdu);
        btnEnglish.setOnClickListener(b -> {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create());
            requireActivity().recreate();
        });
        btnUrdu.setOnClickListener(b -> {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ur"));
            requireActivity().recreate();
        });

        // ---------- Delete history (selective) ----------
        android.widget.CheckBox cbSignals = v.findViewById(R.id.cbDelSignals);
        android.widget.CheckBox cbSpeed = v.findViewById(R.id.cbDelSpeed);
        android.widget.CheckBox cbDrops = v.findViewById(R.id.cbDelDrops);
        android.widget.CheckBox cbSketch = v.findViewById(R.id.cbDelSketch);
        v.findViewById(R.id.btnDeleteHistory).setOnClickListener(b -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_confirm_title)
                    .setMessage(R.string.delete_confirm_msg)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        SharedPreferences.Editor ed = prefs.edit();
                        if (cbSignals.isChecked()) {
                            ed.remove("signals").remove("best_dbm").remove("best_lat").remove("best_lng");
                        }
                        if (cbSpeed.isChecked()) ed.remove("speed_tests");
                        if (cbDrops.isChecked()) ed.remove("drops");
                        if (cbSketch.isChecked()) {
                            // Naksha sketch data (saare sketch keys)
                            for (String k : prefs.getAll().keySet()) {
                                if (k.startsWith("naksha_") || k.startsWith("sketch_")) ed.remove(k);
                            }
                        }
                        ed.apply();
                        android.widget.Toast.makeText(getContext(),
                                getString(R.string.history_cleared), android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        // ---------- User guide ----------
        v.findViewById(R.id.btnGuide).setOnClickListener(b ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new GuideFragment())
                        .addToBackStack(null)
                        .commit());

        // ---------- About ----------
        TextView tvAbout = v.findViewById(R.id.tvAbout);
        tvAbout.setText(getString(R.string.about_text));
    }
}
