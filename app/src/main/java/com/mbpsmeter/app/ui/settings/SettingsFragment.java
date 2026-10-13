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
