package com.mbpsmeter.app.ui.history;
import android.content.Context; import android.content.SharedPreferences; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup; import android.widget.TextView;
import androidx.annotation.NonNull; import androidx.annotation.Nullable; import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager; import androidx.recyclerview.widget.RecyclerView;
import com.mbpsmeter.app.R; import org.json.JSONArray; import org.json.JSONObject;
import java.text.SimpleDateFormat; import java.util.ArrayList; import java.util.Date; import java.util.Locale;

public class HistoryV4Fragment extends Fragment {
    private TextView tvCount; private RecyclerView recycler; private ArrayList<JSONObject> list=new ArrayList<>();
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle savedInstanceState){
        View view=inflater.inflate(R.layout.fragment_history_v4,container,false);
        tvCount=view.findViewById(R.id.tvCount); recycler=view.findViewById(R.id.recyclerHistory);
        recycler.setLayoutManager(new LinearLayoutManager(getContext())); load(); return view;
    }
    private void load(){
        try{
            SharedPreferences prefs=requireActivity().getSharedPreferences("MbpsMeter",Context.MODE_PRIVATE);
            String json=prefs.getString("signals","[]"); JSONArray arr=new JSONArray(json); list.clear();
            for(int i=arr.length()-1;i>=0;i--) list.add(arr.getJSONObject(i));
            tvCount.setText("Total Readings: "+arr.length());
            recycler.setAdapter(new RecyclerView.Adapter<Holder>(){
                @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p,int t){return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_history,p,false));}
                @Override public void onBindViewHolder(@NonNull Holder h,int pos){
                    try{
                        JSONObject o=list.get(pos); int dbm=o.getInt("dbm"); double lat=o.getDouble("lat");
                        h.tvDbm.setText(dbm+" dBm");
                        String time=new SimpleDateFormat("dd MMM hh:mm a",Locale.getDefault()).format(new Date(o.getLong("time")));
                        h.tvTime.setText(String.format(Locale.getDefault(),"%.4f, %.4f | %s | %s",o.getDouble("lat"),o.getDouble("lng"),o.getString("type"),time));
                        if(dbm>=-80){h.tvBadge.setText("GOOD"); h.tvBadge.setTextColor(0xFF22C55E);} 
                        else if(dbm>=-95){h.tvBadge.setText("FAIR"); h.tvBadge.setTextColor(0xFFF59E0B);} 
                        else{h.tvBadge.setText("WEAK"); h.tvBadge.setTextColor(0xFFEF4444);}
                    }catch(Exception e){}
                }
                @Override public int getItemCount(){return list.size();}
            });
        }catch(Exception e){tvCount.setText("Total Readings: 0");}
    }
    static class Holder extends RecyclerView.ViewHolder{
        TextView tvDbm,tvTime,tvBadge; Holder(View v){super(v); tvDbm=v.findViewById(R.id.tvDbmItem); tvTime=v.findViewById(R.id.tvTimeItem); tvBadge=v.findViewById(R.id.tvBadge);}
    }
    @Override public void onResume(){super.onResume(); load();}
}