package com.mbpsmeter.app.ui.map;
import android.content.Context; import android.content.SharedPreferences; import android.os.Bundle;
import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup;
import android.widget.Button; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.annotation.Nullable; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.R;
import org.osmdroid.util.GeoPoint; import org.osmdroid.views.MapView; import org.osmdroid.views.overlay.Marker;

public class MapV4Fragment extends Fragment {
    private TextView tvBestSignal,tvBestLocation; private Button btnNavigateBest,btnOpenMap;
    private SharedPreferences prefs; private float bestLat=0f,bestLng=0f; private int bestDbm=-120;
    private MapView osmMap;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle s){
        View v=inflater.inflate(R.layout.fragment_map_v4,container,false);
        tvBestSignal=v.findViewById(R.id.tvBestSignal); tvBestLocation=v.findViewById(R.id.tvBestLocation);
        btnNavigateBest=v.findViewById(R.id.btnNavigateBest); btnOpenMap=v.findViewById(R.id.btnOpenMap);
        osmMap=v.findViewById(R.id.osmMap);
        prefs=requireActivity().getSharedPreferences("MbpsMeter",Context.MODE_PRIVATE);
        setupOSM(); loadBest();
        btnNavigateBest.setOnClickListener(view->{if(bestLat!=0) moveToBest();});
        btnOpenMap.setOnClickListener(view->{if(bestLat!=0) moveToBest(); else Toast.makeText(getContext(),"No location",Toast.LENGTH_SHORT).show();});
        return v;
    }
    private void setupOSM(){osmMap.setMultiTouchControls(true); osmMap.getController().setZoom(17.0); osmMap.getController().setCenter(new GeoPoint(33.6844,73.0479));}
    private void loadBest(){
        bestLat=prefs.getFloat("best_lat",0f); bestLng=prefs.getFloat("best_lng",0f); bestDbm=prefs.getInt("best_dbm",-120);
        if(bestLat==0){tvBestSignal.setText("No best location yet"); tvBestLocation.setText("Move to find GOOD signal to auto-save");}
        else{tvBestSignal.setText("Best: "+bestDbm+" dBm"); tvBestLocation.setText("Lat: "+bestLat+", Lng: "+bestLng); moveToBest();}
    }
    private void moveToBest(){
        GeoPoint p=new GeoPoint(bestLat,bestLng); osmMap.getController().animateTo(p); osmMap.getController().setZoom(18.0);
        osmMap.getOverlays().clear(); Marker m=new Marker(osmMap); m.setPosition(p); m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
        m.setTitle("Best Signal: "+bestDbm+" dBm"); osmMap.getOverlays().add(m); osmMap.invalidate();
    }
    @Override public void onResume(){super.onResume(); if(osmMap!=null) osmMap.onResume();}
    @Override public void onPause(){super.onPause(); if(osmMap!=null) osmMap.onPause();}
}