package com.mbpsmeter.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private TextView status;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);
        layout.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = new TextView(this);
        title.setText("MbpsMeter");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);

        status = new TextView(this);
        status.setText("Ready to test your internet speed");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 40, 0, 40);

        startButton = new Button(this);
        startButton.setText("Start Speed Test");
        startButton.setTextSize(18);
        startButton.setBackgroundColor(Color.rgb(33, 150, 243));
        startButton.setTextColor(Color.WHITE);
        startButton.setPadding(40, 30, 40, 30);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setEnabled(false);
                status.setText("Testing... Please wait");
                new SpeedTestTask().execute();
            }
        });

        layout.addView(title);
        layout.addView(status);
        layout.addView(startButton);

        setContentView(layout);
    }

    private class SpeedTestTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            try {
                // 10 MB test file from Cloudflare
                URL url = new URL("https://speed.cloudflare.com/__down?bytes=10000000");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);

                long startTime = System.currentTimeMillis();

                InputStream input = connection.getInputStream();
                byte[] buffer = new byte[8192];
                long totalBytes = 0;
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                }

                input.close();
                connection.disconnect();

                long endTime = System.currentTimeMillis();
                double timeTakenSeconds = (endTime - startTime) / 1000.0;

                // Convert to Mbps
                double speedMbps = (totalBytes * 8) / (timeTakenSeconds * 1000 * 1000);

                return String.format("%.2f Mbps", speedMbps);

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            status.setText("Download Speed: " + result);
            startButton.setEnabled(true);
            startButton.setText("Test Again");
        }
    }
}