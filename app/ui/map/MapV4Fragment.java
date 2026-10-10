package com.mbpsmeter.app.ui.map;
import android.location.Location; import android.os.Bundle; import android.view.*; import android.widget.*; import androidx.annotation.*; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.MainActivity; import com.mbpsmeter.app.R;
import org.json.*; import org.osmdroid.util.GeoPoint; import org.osmdroid.views.MapView; import org.osmdroid.views.overlay.Marker; import org.osmdroid.views.overlay.Polygon; import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider; import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import java.util.Locale;
public class MapV4Fragment extends Fragment {
  MapView map; TextView tvAvg, tvNodes, tvNavigate; MyLocationNewOverlay myLoc;
  @Nullable public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b){
    View v = inf.inflate(R.layout.fragment_map_v4, c, false);
    map=v.findViewById(R.id.mapView); tvAvg=v.findViewById(R.id.tvAvg); tvNodes=v.findViewById(R.id.tvNodes); tvNavigate=v.findViewById(R.id.tvNavigateBtn);
    map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
    map.setMultiTouchControls(true);
    map.getController().setZoom(17.0);
    // FIX 1: MERI LOCATION SHOW
    myLoc = new MyLocationNewOverlay(new GpsMyLocationProvider(getContext()), map);
    myLoc.enableMyLocation(); map.getOverlays().add(myLoc);
    if(MainActivity.currentLocation!=null) map.getController().setCenter(new GeoPoint(MainActivity.currentLocation.getLatitude(), MainActivity.currentLocation.getLongitude()));
    else map.getController().setCenter(new GeoPoint(24.8607, 67.0011));
    drawSaved();
    // FIX 2: NAVIGATE TO BEST
    v.findViewById(R.id.btnNavigateBest).setOnClickListener(x-> navigateToBest());
    return v;
  }
  void drawSaved(){
    try{
      JSONArray arr = new JSONArray(getActivity().getSharedPreferences(MainActivity.PREFS,0).getString("signals","[]"));
      for(int i=0;i<arr.length();i++){ JSONObject o=arr.getJSONObject(i); double lat=o.getDouble("lat"); double lon=o.getDouble("lon"); int dbm=o.getInt("dbm"); GeoPoint gp=new GeoPoint(lat,lon); Marker m=new Marker(map); m.setPosition(gp); m.setTitle(dbm+" dBm"); map.getOverlays().add(m); Polygon cir=new Polygon(map); cir.setPoints(Polygon.pointsAsCircle(gp,8)); int col=dbm>=-85?0x6034D399:dbm>=-100?0x60FACC15:0x60EF4444; cir.setFillColor(col); cir.setStrokeColor(col); map.getOverlays().add(cir); }
      tvNodes.setText(arr.length()+" Locations"); map.invalidate();
    }catch(Exception e){}
  }
  void navigateToBest(){
    try{
      JSONArray arr = new JSONArray(getActivity().getSharedPreferences(MainActivity.PREFS,0).getString("signals","[]"));
      if(arr.length()==0){ Toast.makeText(getContext(),"No saved points",Toast.LENGTH_SHORT).show(); return; }
      JSONObject best=null; int max=-200;
      for(int i=0;i<arr.length();i++){ int dbm=arr.getJSONObject(i).getInt("dbm"); if(dbm>max){ max=dbm; best=arr.getJSONObject(i);} }
      if(best==null || MainActivity.currentLocation==null) return;
      float[] res=new float[1]; Location.distanceBetween(MainActivity.currentLocation.getLatitude(), MainActivity.currentLocation.getLongitude(), best.getDouble("lat"), best.getDouble("lon"), res);
      tvNavigate.setText(String.format(Locale.US,"Best %d dBm - %.0f meter door hai", max, res[0]));
      map.getController().animateTo(new GeoPoint(best.getDouble("lat"), best.getDouble("lon")));
    }catch(Exception e){}
  }
  public void onResume(){ super.onResume(); if(map!=null) map.onResume(); }
  public void onPause(){ super.onPause(); if(map!=null) map.onPause(); }
}