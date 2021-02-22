package com.example.flirapp;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;

import com.flir.thermalsdk.live.remote.Property;

import java.util.Timer;
import java.util.TimerTask;

public class CalibrationTimer extends AppCompatActivity {
    private CameraHandler cameraHandler;

    private Chronometer warmtimer;
    private Button button995;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_timer);
        cameraHandler = CameraDetected.cameraHandler;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        warmtimer = findViewById(R.id.warmtimer);
        button995=findViewById(R.id.button995);
    }

    @Override
    protected void onStart() {

        super.onStart();
        warmtimer.setCountDown(true);
        warmtimer.setBase(SystemClock.elapsedRealtime() + 300000);
//        warmtimer.setBase(SystemClock.elapsedRealtime() + 10000);

        warmtimer.start();

        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                cameraHandler.calibration();
            }
        };
//        timer.schedule(timerTask, 1000 * 27);
        timer.schedule(timerTask, 1000 * 3);
    }

    public void go(View view){
        warmtimer.stop();
        Intent intent1 = new Intent(CalibrationTimer.this, RecordProcess.class);
        startActivity(intent1);
        finish();
    }
    @Override
    public void onResume() {

        super.onResume();
        warmtimer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                if (SystemClock.elapsedRealtime()-warmtimer.getBase()>= 0) {
                    warmtimer.stop();
                    Intent intent1 = new Intent(CalibrationTimer.this, RecordProcess.class);
                    startActivity(intent1);
                    finish();
                }
            }
        });

    }
    @Override
    public void onStop() {

        super.onStop();
    }
    @Override
    public void onPause() {

        super.onPause();
    }
}