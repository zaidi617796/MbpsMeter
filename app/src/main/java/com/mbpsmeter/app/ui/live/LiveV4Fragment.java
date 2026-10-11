package com.mbpsmeter.app.ui.live;
import android.content.Context; import android.content.SharedPreferences; import android.os.Bundle;
import android.os.Handler; import android.os.Looper; import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import android.widget.Button; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.annotation.Nullable; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.R;
import org.json.JSONArray; import org.json.JSONObject;
import java.text.SimpleDateFormat; import java.util.Date; import java.util.Locale; import java.util.Random;

public class LiveV4Fragment extends Fragment {
    private TextView tvDbm,tvStrength,tvGps,tvTime; private Button btnSave; private SharedPreferences prefs;
    private boolean isAutoSaving=false; private Handler handler=new Handler(Looper.getMainLooper());
    private Runnable runnable; private Random random=new Random(); private int currentDbm=-85;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle s){
        View v=inflater.inflate(R.layout.fragment_live_v4,container,false);
        tvDbm=v.findViewById(R.id.tvDbm); tvStrength=v.findViewById(R.id.tvStrength); tvGps=v.findViewById(R.id.tvGps);
        tvTime=v.findViewById(R.id.tvTime); btnSave=v.findViewById(R.id.btnSave);
        prefs=requireActivity().getSharedPreferences("MbpsMeter",Context.MODE_PRIVATE);
        btnSave.setOnClickListener(view->save(false)); start(); return v;
    }
    private void start(){ runnable=new Runnable(){@Override public void run(){
            currentDbm=-65-random.nextInt(45); update(currentDbm); handler.postDelayed(this,3000);}}; handler.post(runnable);}
    private void update(int dbm){
        tvDbm.setText(dbm+" dBm"); String level; int col;
        if(dbm>=-80){level="GOOD"; col=getResources().getColor(android.R.color.holo_green_light); tvStrength.setText("GOOD SIGNAL");}
        else if(dbm>=-95){level="FAIR"; col=getResources().getColor(android.R.color.holo_orange_light); tvStrength.setText("FAIR SIGNAL");}
        else{level="WEAK"; col=getResources().getColor(android.R.color.holo_red_light); tvStrength.setText("WEAK SIGNAL");}
        tvStrength.setTextColor(col);
        tvTime.setText(new SimpleDateFormat("hh:mm:ss a",Locale.getDefault()).format(new Date()));
        if(dbm>=-95 && !isAutoSaving){isAutoSaving=true; save(true); handler.postDelayed(()->isAutoSaving=false,10000);}
    }
    private void save(boolean auto){
        try{
            String json=prefs.getString("signals","[]"); JSONArray arr=new JSONArray(json);
            JSONObject obj=new JSONObject(); obj.put("dbm",currentDbm); obj.put("time",System.currentTimeMillis());
            obj.put("lat",33.6844); obj.put("lng",73.0479); obj.put("type",auto?"AUTO":"MANUAL");
            arr.put(obj); prefs.edit().putString("signals",arr.toString()).putInt("last_dbm",currentDbm)
                    .putFloat("best_lat",33.6844f).putFloat("best_lng",73.0479f).putInt("best_dbm",currentDbm).apply();
            Toast.makeText(getContext(),(auto?"Auto Saved: ":"Saved: ")+currentDbm+" dBm",Toast.LENGTH_SHORT).show();
        }catch(Exception e){e.printStackTrace();}
    }
    @Override public void onDestroyView(){super.onDestroyView(); handler.removeCallbacks(runnable);}
}