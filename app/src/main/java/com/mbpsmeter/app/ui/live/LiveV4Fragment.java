package com.mbpsmeter.app.ui.live;
import android.Manifest; import android.content.Context; import android.content.SharedPreferences; import android.content.pm.PackageManager;
import android.location.Location; import android.location.LocationManager; import android.os.Bundle; import android.os.Handler; import android.os.Looper;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup; import android.widget.Button; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.annotation.Nullable; import androidx.core.app.ActivityCompat; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.MainActivity; import com.mbpsmeter.app.R;
import org.json.JSONArray; import org.json.JSONObject; import java.text.SimpleDateFormat; import java.util.Date; import java.util.Locale; import java.util.Random;

public class LiveV4Fragment extends Fragment {
    private TextView tvDbm,tvStrength,tvGps,tvTime; private SharedPreferences prefs;
    private Handler handler=new Handler(Looper.getMainLooper()); private Runnable runnable; private Random random=new Random();
    private int currentDbm=-85; private double curLat=24.8607, curLng=67.0011; // Karachi Default

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle s){
        View v=inflater.inflate(R.layout.fragment_live_v4,container,false);
        tvDbm=v.findViewById(R.id.tvDbm); tvStrength=v.findViewById(R.id.tvStrength); tvGps=v.findViewById(R.id.tvGps); tvTime=v.findViewById(R.id.tvTime);
        Button btnSave=v.findViewById(R.id.btnSave);
        prefs=requireActivity().getSharedPreferences("MbpsMeter",Context.MODE_PRIVATE);
        getRealLocation();
        btnSave.setOnClickListener(view->save(false)); start(); return v;
    }
    private void getRealLocation(){
        try{
            LocationManager lm=(LocationManager)requireContext().getSystemService(Context.LOCATION_SERVICE);
            if(ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                Location last=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last==null) last=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(last!=null){curLat=last.getLatitude(); curLng=last.getLongitude(); MainActivity.currentLocation=last;
                    tvGps.setText("GPS: "+String.format("%.4f, %.4f",curLat,curLng));}
                else tvGps.setText("GPS: Karachi (24.8607, 67.0011)");
            }
        }catch(Exception e){tvGps.setText("GPS: Karachi");}
    }
    private void start(){runnable=new Runnable(){@Override public void run(){currentDbm=-65-random.nextInt(45); update(currentDbm); handler.postDelayed(this,3000);}}; handler.post(runnable);}
    private void update(int dbm){
        tvDbm.setText(dbm+" dBm"); if(dbm>=-80) tvStrength.setText("GOOD SIGNAL"); else if(dbm>=-95) tvStrength.setText("FAIR SIGNAL"); else tvStrength.setText("WEAK SIGNAL");
        tvTime.setText(new SimpleDateFormat("hh:mm:ss a",Locale.getDefault()).format(new Date()));
        if(dbm>=-80){ // Auto save only GOOD
            save(true);
        }
    }
    private void save(boolean auto){
        try{
            getRealLocation(); // har save par fresh location
            String json=prefs.getString("signals","[]"); JSONArray arr=new JSONArray(json);
            JSONObject obj=new JSONObject(); obj.put("dbm",currentDbm); obj.put("time",System.currentTimeMillis());
            obj.put("lat",curLat); obj.put("lng",curLng); obj.put("type",auto?"AUTO":"MANUAL"); arr.put(obj);
            SharedPreferences.Editor ed=prefs.edit(); ed.putString("signals",arr.toString()); 
            // Best location update
            int oldBest=prefs.getInt("best_dbm",-120); if(currentDbm>oldBest){
                ed.putFloat("best_lat",(float)curLat); ed.putFloat("best_lng",(float)curLng); ed.putInt("best_dbm",currentDbm);
            } ed.apply();
            Toast.makeText(getContext(),(auto?"Auto ":"")+currentDbm+" dBm @ Karachi saved",Toast.LENGTH_SHORT).show();
        }catch(Exception e){e.printStackTrace();}
    }
    @Override public void onDestroyView(){super.onDestroyView(); handler.removeCallbacks(runnable);}
}