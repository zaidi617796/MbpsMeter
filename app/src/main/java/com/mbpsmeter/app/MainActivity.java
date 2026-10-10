package com.mbpsmeter.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.google.android.gms.location.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mbpsmeter.app.ui.history.HistoryV4Fragment;
import com.mbpsmeter.app.ui.live.LiveV4Fragment;
import com.mbpsmeter.app.ui.map.MapV4Fragment;
import com.mbpsmeter.app.ui.speed.SpeedV4Fragment;
import org.osmdroid.config.Configuration;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS = "mbpsmeter_v4";
    public static int currentDbm = -120;
    public static Location currentLocation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TelephonyManager telephony;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(), getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment f = null;
            int id = item.getItemId();
            if(id == R.id.nav_live) f = new LiveV4Fragment();
            else if(id == R.id.nav_speed) f = new SpeedV4Fragment();
            else if(id == R.id.nav_map) f = new MapV4Fragment();
            else if(id == R.id.nav_history) f = new HistoryV4Fragment();
            if(f!= null) getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer, f).commit();
            return true;
        });
        bottomNav.setSelectedItemId(R.id.nav_live);

        requestPermissions();
    }

    private void requestPermissions(){
        String[] perms = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE};
        boolean need = false;
        for(String p: perms) if(ActivityCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED) need=true;
        if(need) ActivityCompat.requestPermissions(this, perms, 44);
        else startMonitoring();
    }

    @Override
    public void onRequestPermissionsResult(int c, @NonNull String[] p, @NonNull int[] r){ super.onRequestPermissionsResult(c,p,r); startMonitoring(); }

    private void startMonitoring(){
        startLocation();
        startSignal();
    }

    private void startLocation(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).setMinUpdateIntervalMillis(2000).build();
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result){
                if(result.getLastLocation()!=null) currentLocation = result.getLastLocation();
            }
        };
        fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private void startSignal(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) return;
        telephony.listen(new PhoneStateListener(){
            @Override public void onSignalStrengthsChanged(SignalStrength s){
                int dbm = -120;
                try{
                    for(CellSignalStrength cell : s.getCellSignalStrengths()){
                        int cand = cell.getDbm();
                        if(cand < 0 && cand > -200){ dbm = cand; break; }
                    }
                }catch(Exception e){}
                currentDbm = dbm;
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    public SharedPreferences prefs(){ return getSharedPreferences(PREFS, MODE_PRIVATE); }

    @Override protected void onDestroy(){
        super.onDestroy();
        if(locationCallback!=null) fusedClient.removeLocationUpdates(locationCallback);
    }
}