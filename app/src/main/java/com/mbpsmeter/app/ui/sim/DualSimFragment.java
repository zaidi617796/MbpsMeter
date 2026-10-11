package com.mbpsmeter.app.ui.sim;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.DualSimReader;
import com.mbpsmeter.app.util.SignalReader;

import java.util.List;
import java.util.Locale;

/**
 * DUAL SIM COMPARE: dono SIMs ka live signal aik sath,
 * aur mashwara ke is waqt kaunsi SIM behtar hai.
 */
public class DualSimFragment extends Fragment {

    private LinearLayout simContainer;
    private TextView tvRecommend;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dualsim, container, false);
        simContainer = v.findViewById(R.id.simContainer);
        tvRecommend = v.findViewById(R.id.tvSimRecommend);
        v.findViewById(R.id.btnBack).setOnClickListener(x -> {
            if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
        });
        v.findViewById(R.id.btnRefreshSim).setOnClickListener(x -> load());
        load();
        return v;
    }

    private void load() {
        if (getContext() == null) return;
        simContainer.removeAllViews();
        List<DualSimReader.SimInfo> sims = DualSimReader.read(requireContext());

        if (sims.isEmpty()) {
            tvRecommend.setText(R.string.sim_recommend_none);
            return;
        }

        DualSimReader.SimInfo best = null;
        for (DualSimReader.SimInfo si : sims) {
            View card = LayoutInflater.from(getContext()).inflate(R.layout.item_room, simContainer, false);
            TextView name = card.findViewById(R.id.tvRoomName);
            TextView avg = card.findViewById(R.id.tvRoomAvg);
            TextView count = card.findViewById(R.id.tvRoomCount);

            name.setText("SIM " + si.slot + " - " + si.carrier);
            if (si.hasData) {
                avg.setText(si.dbm + " dBm");
                int color = si.dbm >= -80 ? 0xFF22C55E : (si.dbm >= -95 ? 0xFFF59E0B : 0xFFEF4444);
                avg.setTextColor(color);
                count.setText(SignalReader.label(si.dbm));
                count.setTextColor(0xFF8A9ABF);
                if (best == null || si.dbm > best.dbm) best = si;
            } else {
                avg.setText(getString(R.string.sim_na));
                avg.setTextColor(0xFF8A9ABF);
                count.setText("");
            }
            simContainer.addView(card);
        }

        if (best != null) {
            boolean tie = false;
            for (DualSimReader.SimInfo si : sims) {
                if (si != best && si.hasData && Math.abs(si.dbm - best.dbm) < 6) { tie = true; break; }
            }
            if (tie) {
                tvRecommend.setText(R.string.sim_recommend_tie);
            } else {
                tvRecommend.setText(String.format(Locale.getDefault(),
                        getString(R.string.sim_recommend_better), "SIM " + best.slot + " (" + best.carrier + ")"));
            }
        } else {
            tvRecommend.setText(R.string.sim_recommend_none);
        }
    }
}
