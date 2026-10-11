package com.mbpsmeter.app.ui.map;
import android.content.Context; import android.content.SharedPreferences; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup; import android.widget.Button; import android.widget.TextView;
import androidx.annotation.NonNull; import androidx.annotation.Nullable; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.R; import org.json.JSONArray; import org.json.JSONObject;
import org.osmdroid.util.GeoPoint; import org.osmdroid.views.MapView; import org.osmdroid.views.overlay.Marker;

public class MapV4Fragment extends Fragment {
    private TextView tvBestSignal,tvBestLocation; private SharedPreferences prefs;
    private MapView osmMap; private float bestLat=0f,bestLng=0f; private int bestDbm=-120;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle s){
        View v=inflater.inflate(R.layout.fragment_map_v4,container,false);
        tvBestSignal=v.findViewById(R.id.tvBestSignal); tvBestLocation=v.findViewById(R.id.tvBestLocation);
        Button btnNav=v.findViewById(R.id.btnNavigateBest); Button btnOpen=v.findViewById(R.id.btnOpenMap);
        osmMap=v.findViewById(R.id.osmMap); prefs=requireActivity().getSharedPreferences("MbpsMeter",Context.MODE_PRIVATE);
        osmMap.setMultiTouchControls(true); osmMap.getController().setZoom(15.0);
        btnNav.setOnClickListener(view->moveTo(bestLat,bestLng,bestDbm)); btnOpen.setOnClickListener(view->moveTo(bestLat,bestLng,bestDbm));
        loadAll(); return v;
    }
    private void loadAll(){
        try{
            osmMap.getOverlays().clear();
            String json=prefs.getString("signals","[]"); JSONArray arr=new JSONArray(json);
            bestLat=prefs.getFloat("best_lat",0f); bestLng=prefs.getFloat("best_lng",0f); bestDbm=prefs.getInt("best_dbm",-120);
            GeoPoint center=new GeoPoint(24.8607,67.0011); // Karachi
            if(arr.length()>0){ JSONObject last=arr.getJSONObject(arr.length()-1); center=new GeoPoint(last.getDouble("lat"),last.getDouble("lng")); }
            else if(bestLat!=0) center=new GeoPoint(bestLat,bestLng);
            // Sari readings ke markers
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i); GeoPoint p=new GeoPoint(o.getDouble("lat"),o.getDouble("lng"));
                Marker m=new Marker(osmMap); m.setPosition(p); m.setTitle(o.getInt("dbm")+" dBm "+o.getString("type")); m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
                osmMap.getOverlays().add(m);
            }
            osmMap.getController().setCenter(center); osmMap.invalidate();
            if(bestLat==0){tvBestSignal.setText("No best yet - "+arr.length()+" readings"); tvBestLocation.setText("Karachi default: 24.8607, 67.0011");}
            else{tvBestSignal.setText("Best: "+bestDbm+" dBm | Total: "+arr.length()); tvBestLocation.setText("Lat: "+bestLat+", Lng: "+bestLng);}
        }catch(Exception e){e.printStackTrace();}
    }
    private void moveTo(float lat,float lng,int dbm){ if(lat==0) return; GeoPoint p=new GeoPoint(lat,lng); osmMap.getController().animateTo(p); osmMap.getController().setZoom(18.0); }
    @Override public void onResume(){super.onResume(); if(osmMap!=null){osmMap.onResume(); loadAll();}}
    @Override public void onPause(){super.onPause(); if(osmMap!=null) osmMap.onPause();}
}