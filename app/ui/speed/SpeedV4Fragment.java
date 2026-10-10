package com.mbpsmeter.app.ui.speed;
import android.os.Bundle; import android.view.*; import android.widget.*; import androidx.annotation.*; import androidx.fragment.app.Fragment;
import com.mbpsmeter.app.R;
import java.io.InputStream; import java.io.OutputStream; import java.net.HttpURLConnection; import java.net.URL;
import java.util.Locale;
public class SpeedV4Fragment extends Fragment {
  TextView tvMain, tvDown, tvUp, tvStatus; Button btn; boolean running=false;
  @Nullable public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b){
    View v=inf.inflate(R.layout.fragment_speed_v4,c,false);
    tvMain=v.findViewById(R.id.tvMainSpeed); tvDown=v.findViewById(R.id.tvDownload); tvUp=v.findViewById(R.id.tvUpload); tvStatus=v.findViewById(R.id.tvStatus); btn=v.findViewById(R.id.btnStartSpeed);
    btn.setOnClickListener(x-> runTest()); return v;
  }
  void runTest(){
    if(running) return; running=true; btn.setEnabled(false); tvStatus.setText("Testing download...");
    new Thread(()->{
      double down=-1, up=-1; String status="Failed";
      try{
        long start=System.nanoTime(); long bytes=0;
        HttpURLConnection con=(HttpURLConnection)new URL("https://speed.cloudflare.com/__down?bytes=5000000").openConnection();
        con.setConnectTimeout(15000); con.setReadTimeout(25000);
        InputStream in=con.getInputStream(); byte[] buf=new byte[32768]; int n; while((n=in.read(buf))!=-1) bytes+=n; in.close(); con.disconnect();
        double sec=(System.nanoTime()-start)/1e9; if(sec>0) down=bytes*8.0/sec/1e6;
        // Upload
        byte[] payload=new byte[500000]; java.util.Arrays.fill(payload,(byte)65);
        HttpURLConnection upCon=(HttpURLConnection)new URL("https://speed.cloudflare.com/__up").openConnection();
        upCon.setRequestMethod("POST"); upCon.setDoOutput(true); upCon.setConnectTimeout(15000); upCon.setReadTimeout(25000); upCon.setFixedLengthStreamingMode(payload.length);
        long upStart=System.nanoTime(); OutputStream os=upCon.getOutputStream(); os.write(payload); os.flush(); os.close();
        InputStream r=upCon.getInputStream(); while(r.read(new byte[4096])!=-1){} r.close();
        double upSec=(System.nanoTime()-upStart)/1e9; if(upSec>0) up=payload.length*8.0/upSec/1e6; status="Complete"; upCon.disconnect();
      }catch(Exception e){ status=e.getMessage(); }
      double fd=down, fu=up; String fs=status;
      if(getActivity()!=null) getActivity().runOnUiThread(()->{
        running=false; btn.setEnabled(true);
        tvMain.setText(fd>=0?String.format(Locale.US,"%.2f Mbps",fd):"Failed");
        tvDown.setText(fd>=0?String.format(Locale.US,"%.2f Mbps",fd):"Failed");
        tvUp.setText(fu>=0?String.format(Locale.US,"%.2f Mbps",fu):"Failed");
        tvStatus.setText("Status: "+fs);
      });
    }).start();
  }
}