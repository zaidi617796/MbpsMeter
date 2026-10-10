package com.mbpsmeter.app.ui.history;
import android.os.Bundle; import android.view.*; import android.widget.*; import androidx.annotation.*; import androidx.fragment.app.Fragment; import androidx.recyclerview.widget.*;
import com.mbpsmeter.app.MainActivity; import com.mbpsmeter.app.R;
import org.json.*;
public class HistoryV4Fragment extends Fragment {
  RecyclerView rv;
  @Nullable public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b){
    View v=inf.inflate(R.layout.fragment_history_v4,c,false);
    rv=v.findViewById(R.id.rvHistory); rv.setLayoutManager(new LinearLayoutManager(getContext()));
    load(); v.findViewById(R.id.btnExport).setOnClickListener(x-> Toast.makeText(getContext(),"Export coming in V4.3",Toast.LENGTH_SHORT).show());
    return v;
  }
  void load(){
    try{
      JSONArray arr=new JSONArray(getActivity().getSharedPreferences(MainActivity.PREFS,0).getString("signals","[]"));
      rv.setAdapter(new RecyclerView.Adapter<VH>(){
        @NonNull public VH onCreateViewHolder(@NonNull ViewGroup p,int t){ return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_history_v4,p,false)); }
        public void onBindViewHolder(@NonNull VH h,int pos){
          try{ JSONObject o=arr.getJSONObject(arr.length()-1-pos); int dbm=o.getInt("dbm"); h.badge.setText(dbm+" dBm"); h.badge.setBackgroundColor(dbm>=-85?0xFF7CFF8A:dbm>=-100?0xFFFFE566:0xFFFF6B6B); h.type.setText((dbm>=-85?"Strong":dbm>=-100?"Moderate":"Weak")+" • "+o.getString("time")); h.loc.setText(o.getDouble("lat")+","+o.getDouble("lon")); }catch(Exception e){}
        }
        public int getItemCount(){ return arr.length(); }
      });
    }catch(Exception e){}
  }
  static class VH extends RecyclerView.ViewHolder{ TextView badge,type,loc; VH(View v){ super(v); badge=v.findViewById(R.id.badgeDbm); type=v.findViewById(R.id.tvType); loc=v.findViewById(R.id.tvLoc);} }
}