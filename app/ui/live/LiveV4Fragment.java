package com.mbpsmeter.app.ui.live;
import android.os.Bundle; import android.os.Handler; import android.view.*; import android.widget.*; import androidx.annotation.*; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.MainActivity; import com.mbpsmeter.app.R;
import org.json.*; import java.text.SimpleDateFormat; import java.util.*; import java.util.Locale;
public class LiveV4Fragment extends Fragment {
  TextView tvDbm, tvGps; Handler h = new Handler();
  Runnable r = new Runnable(){ public void run(){ if(tvDbm!=null){ tvDbm.setText(MainActivity.currentDbm+" dBm"); if(MainActivity.currentLocation!=null) tvGps.setText(String.format(Locale.US,"GPS: %.5f, %.5f",MainActivity.currentLocation.getLatitude(), MainActivity.currentLocation.getLongitude())); } h.postDelayed(this,1000);} };
  @Nullable public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b){
    View v = i.inflate(R.layout.fragment_live_v4, c, false);
    tvDbm=v.findViewById(R.id.tvDbm); tvGps=v.findViewById(R.id.tvGps);
    Button btn=v.findViewById(R.id.btnSave);
    btn.setOnClickListener(x-> save());
    h.post(r); return v;
  }
  void save(){
    if(MainActivity.currentLocation==null) return;
    try{
      SharedPreferences prefs = getActivity().getSharedPreferences(MainActivity.PREFS, 0);
      JSONArray arr = new JSONArray(prefs.getString("signals","[]"));
      JSONObject o = new JSONObject(); o.put("dbm",MainActivity.currentDbm); o.put("lat",MainActivity.currentLocation.getLatitude()); o.put("lon",MainActivity.currentLocation.getLongitude()); o.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
      arr.put(o); prefs.edit().putString("signals",arr.toString()).apply();
      Toast.makeText(getContext(),"Saved",Toast.LENGTH_SHORT).show();
    }catch(Exception e){}
  }
  public void onDestroyView(){ super.onDestroyView(); h.removeCallbacks(r); }
}