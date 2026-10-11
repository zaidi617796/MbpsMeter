package com.mbpsmeter.app;
import android.location.Location; import android.os.Bundle; import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets; import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat; import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mbpsmeter.app.ui.history.HistoryV4Fragment; import com.mbpsmeter.app.ui.live.LiveV4Fragment;
import com.mbpsmeter.app.ui.map.MapV4Fragment; import com.mbpsmeter.app.ui.speed.SpeedV4Fragment;
import org.osmdroid.config.Configuration;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS = "MbpsMeter"; public static Location currentLocation = null;
    private BottomNavigationView bottomNav;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        bottomNav = findViewById(R.id.bottomNav);
        View container = findViewById(R.id.fragmentContainer);
        if(bottomNav!=null) ViewCompat.setOnApplyWindowInsetsListener(bottomNav,(v,insets)->{
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(),v.getPaddingTop(),v.getPaddingRight(),nav.bottom); return WindowInsetsCompat.CONSUMED;});
        if(container!=null) ViewCompat.setOnApplyWindowInsetsListener(container,(v,insets)->{
            Insets st = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(),st.top,v.getPaddingRight(),v.getPaddingBottom()); return WindowInsetsCompat.CONSUMED;});
        bottomNav.setOnItemSelectedListener(item->{
            Fragment sel=null; int id=item.getItemId();
            if(id==R.id.nav_live) sel=new LiveV4Fragment(); else if(id==R.id.nav_speed) sel=new SpeedV4Fragment();
            else if(id==R.id.nav_map) sel=new MapV4Fragment(); else if(id==R.id.nav_history) sel=new HistoryV4Fragment();
            if(sel!=null) getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,sel).commit(); return true;});
        if(savedInstanceState==null) bottomNav.setSelectedItemId(R.id.nav_live);
    }
}