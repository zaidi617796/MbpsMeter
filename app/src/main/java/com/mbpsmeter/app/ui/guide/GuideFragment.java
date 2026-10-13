package com.mbpsmeter.app.ui.guide;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.mbpsmeter.app.R;
import com.mbpsmeter.app.util.SignalAdvisor;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SIGNAL GUIDE: user ko tafseel se batayein signal kidhar acha hai.
 * - Live compass arrow: behtreen spot ki taraf (device ghoomane par arrow ghoomta hai)
 * - Room corner report: har kamre me sab se acha corner
 * - Outdoor trend: gradient se simt
 * - Tips: data se bane mashware
 */
public class GuideFragment extends Fragment {

    private SharedPreferences prefs;
    private TextView tvDirBody, tvOutdoorBody;
    private LinearLayout llRooms, llTips;
    private ImageView ivArrow;

    private SensorManager sensorManager;
    private Sensor accel, magnet;
    private final float[] gravity = new float[3];
    private final float[] geomag = new float[3];
    private boolean haveG, haveM;
    private double targetBearing = Double.NaN;

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(e.values, 0, gravity, 0, 3);
                haveG = true;
            } else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(e.values, 0, geomag, 0, 3);
                haveM = true;
            }
            if (haveG && haveM && !Double.isNaN(targetBearing) && ivArrow != null) {
                float[] r = new float[9];
                float[] i = new float[9];
                if (SensorManager.getRotationMatrix(r, i, gravity, geomag)) {
                    float[] o = new float[3];
                    SensorManager.getOrientation(r, o);
                    float azDeg = (float) Math.toDegrees(o[0]);
                    ivArrow.setRotation((float) (targetBearing - azDeg));
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_guide, container, false);
        tvDirBody = v.findViewById(R.id.tvDirBody);
        tvOutdoorBody = v.findViewById(R.id.tvOutdoorBody);
        llRooms = v.findViewById(R.id.llRooms);
        llTips = v.findViewById(R.id.llTips);
        ivArrow = v.findViewById(R.id.ivArrow);
        prefs = requireActivity().getSharedPreferences("MbpsMeter", Context.MODE_PRIVATE);

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accel != null && magnet != null) {
            sensorManager.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(sensorListener, magnet, SensorManager.SENSOR_DELAY_UI);
        }
        refresh();
    }

    @Override
    public void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(sensorListener);
        super.onPause();
    }

    private boolean compassOk() {
        return accel != null && magnet != null;
    }

    private void refresh() {
        double[] loc = lastLoc();
        SignalAdvisor.BestSpot best = SignalAdvisor.bestSpot(prefs);

        // ---------- direction card ----------
        if (best == null) {
            tvDirBody.setText(R.string.guide_dir_none);
            ivArrow.setVisibility(View.GONE);
            targetBearing = Double.NaN;
        } else if (loc == null) {
            tvDirBody.setText(R.string.guide_no_gps);
            ivArrow.setVisibility(View.GONE);
            targetBearing = Double.NaN;
        } else {
            double b = SignalAdvisor.bearingTo(loc[0], loc[1], best.lat, best.lng);
            double d = SignalAdvisor.distanceMeters(loc[0], loc[1], best.lat, best.lng);
            targetBearing = b;
            String dir = getString(SignalAdvisor.dirRes(SignalAdvisor.compassIndex(b)));
            tvDirBody.setText(getString(R.string.guide_dir_body, best.dbm, Math.round(d), dir));
            ivArrow.setVisibility(compassOk() ? View.VISIBLE : View.GONE);
            ivArrow.setRotation(0);
        }

        // ---------- room corner card ----------
        llRooms.removeAllViews();
        List<String> rooms = roomNames();
        List<SignalAdvisor.CornerStat> stats = SignalAdvisor.roomCornerStats(prefs, rooms);
        boolean any = false;
        String topRoom = null, topCorner = null;
        int topDbm = -999;
        for (SignalAdvisor.CornerStat cs : stats) {
            if (cs.total() == 0) continue;
            any = true;
            int bc = cs.bestCorner();
            String cname = getString(SignalAdvisor.cornerRes(bc));
            int avg = cs.avgOf(bc);
            addLine(llRooms, getString(R.string.guide_room_line, cs.room, cname, avg, cs.count[bc]));
            if (avg > topDbm) { topDbm = avg; topRoom = cs.room; topCorner = cname; }
        }
        if (!any) addLine(llRooms, getString(R.string.guide_rooms_none));

        // ---------- outdoor trend card ----------
        SignalAdvisor.Trend t = SignalAdvisor.outdoorTrend(prefs);
        if (t == null) {
            tvOutdoorBody.setText(R.string.guide_outdoor_none);
        } else if (t.r2 < 0.25) {
            tvOutdoorBody.setText(R.string.guide_outdoor_weak);
        } else {
            String dir = getString(SignalAdvisor.dirRes(SignalAdvisor.compassIndex(t.bearing)));
            tvOutdoorBody.setText(getString(R.string.guide_outdoor_body, dir));
        }

        // ---------- tips card ----------
        llTips.removeAllViews();
        if (best != null && loc != null) {
            double b = SignalAdvisor.bearingTo(loc[0], loc[1], best.lat, best.lng);
            double d = SignalAdvisor.distanceMeters(loc[0], loc[1], best.lat, best.lng);
            if (d > 5) {
                String dir = getString(SignalAdvisor.dirRes(SignalAdvisor.compassIndex(b)));
                addTip(getString(R.string.tip_call_spot, Math.round(d), dir));
            }
        }
        if (topRoom != null) {
            addTip(getString(R.string.tip_corner_call, topRoom, topCorner));
        }
        if (best != null && best.dbm <= -100) {
            addTip(getString(R.string.tip_weak_all));
        }
        if (countSignals() < 5) {
            addTip(getString(R.string.tip_walk_more));
        }
    }

    private void addLine(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        tv.setTextSize(14);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, pad, 0, pad);
        parent.addView(tv);
    }

    private void addTip(String text) {
        TextView tv = new TextView(getContext());
        tv.setText("• " + text);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        tv.setTextSize(13);
        tv.setLineSpacing(3, 1);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, pad, 0, pad);
        llTips.addView(tv);
    }

    private List<String> roomNames() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("rooms", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i).getString("name"));
        } catch (Exception ignored) {}
        return out;
    }

    private int countSignals() {
        try {
            return new JSONArray(prefs.getString("signals", "[]")).length();
        } catch (Exception ignored) { return 0; }
    }

    private double[] lastLoc() {
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l != null) return new double[]{l.getLatitude(), l.getLongitude()};
        } catch (Exception ignored) {}
        return null;
    }
}
